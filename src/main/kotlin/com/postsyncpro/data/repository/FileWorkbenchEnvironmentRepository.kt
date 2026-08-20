package com.postsyncpro.data.repository

import com.postsyncpro.data.security.ApiKeyEncryption
import com.postsyncpro.domain.model.WorkbenchEnvironment
import com.postsyncpro.domain.model.WorkbenchEnvironmentSnapshot
import com.postsyncpro.domain.model.WorkbenchVariable
import com.postsyncpro.domain.repository.WorkbenchEnvironmentRepository
import com.postsyncpro.domain.repository.WorkbenchStorageCorruptionException
import com.postsyncpro.domain.usecase.SecretRedactor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * Atomic environment storage with encrypted-at-rest secret variables.
 *
 * A secret that cannot be decrypted is returned as an empty value and its key
 * is added to [WorkbenchEnvironment.secretsRequiringRestore]. One damaged
 * ciphertext therefore never makes unrelated environments unusable.
 */
class FileWorkbenchEnvironmentRepository(
    file: Path,
    private val encryptValue: (String) -> String = ApiKeyEncryption::encrypt,
    private val decryptValue: (String) -> String = ApiKeyEncryption::decrypt,
    private val redactor: SecretRedactor = SecretRedactor(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxEnvironments: Int = DEFAULT_MAX_ENVIRONMENTS,
    private val maxVariablesPerEnvironment: Int = DEFAULT_MAX_VARIABLES,
    storageLimitBytes: Long = DEFAULT_STORAGE_LIMIT_BYTES
) : WorkbenchEnvironmentRepository {
    private val mutex = workbenchStorageMutex(file)
    private val processLock = WorkbenchStorageProcessLock(file, "Workbench environments")
    private val storageLimitBytes = storageLimitBytes
    private val store = SecureAtomicWorkbenchFile(file, storageLimitBytes, "Workbench environments")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        require(maxEnvironments in 1..HARD_MAX_ENVIRONMENTS) {
            "Environment count limit must be between 1 and $HARD_MAX_ENVIRONMENTS"
        }
        require(maxVariablesPerEnvironment in 1..HARD_MAX_VARIABLES) {
            "Variable count limit must be between 1 and $HARD_MAX_VARIABLES"
        }
        require(storageLimitBytes in MIN_STORAGE_BYTES..MAX_STORAGE_BYTES) {
            "Environment storage limit must be between $MIN_STORAGE_BYTES and $MAX_STORAGE_BYTES bytes"
        }
    }

    override suspend fun snapshot(): WorkbenchEnvironmentSnapshot = locked {
        decodeSnapshot(loadStoredFromDisk())
    }

    override suspend fun find(id: String): WorkbenchEnvironment? = locked {
        validateId(id, "Environment")
        decodeSnapshot(loadStoredFromDisk()).environments.firstOrNull { it.id == id }
    }

    override suspend fun upsert(
        environment: WorkbenchEnvironment
    ): WorkbenchEnvironmentSnapshot = mutationLocked {
        upsertLocked(environment, select = false)
    }

    override suspend fun upsertAndSelectEnvironment(
        environment: WorkbenchEnvironment
    ): WorkbenchEnvironmentSnapshot = mutationLocked {
        upsertLocked(environment, select = true)
    }

    private fun upsertLocked(
        environment: WorkbenchEnvironment,
        select: Boolean
    ): WorkbenchEnvironmentSnapshot {
        val unavailableSecretKeys = environment.secretsRequiringRestore
        val safeEnvironment = sanitizeEnvironment(environment)
        validateEnvironment(safeEnvironment)
        val current = loadStoredFromDisk()
        val previous = current.environments.firstOrNull { it.id == safeEnvironment.id }
        val encodedEnvironment = encodeEnvironment(
            environment = safeEnvironment,
            previous = previous,
            preserveSecretKeys = unavailableSecretKeys
        )
        val updated = current.environments
            .filterNot { it.id == safeEnvironment.id } + encodedEnvironment
        require(updated.size <= maxEnvironments) {
            "Environment count exceeds the limit of $maxEnvironments"
        }
        return persistStored(
            current.copy(
                activeEnvironmentId = if (select) safeEnvironment.id else current.activeEnvironmentId,
                environments = sortStoredEnvironments(updated)
            )
        )
    }

    override suspend fun delete(id: String): WorkbenchEnvironmentSnapshot = mutationLocked {
        validateId(id, "Environment")
        val current = loadStoredFromDisk()
        persistStored(
            current.copy(
                activeEnvironmentId = current.activeEnvironmentId.takeUnless { it == id },
                environments = current.environments.filterNot { it.id == id }
            )
        )
    }

    override suspend fun select(id: String?): WorkbenchEnvironmentSnapshot = mutationLocked {
        id?.let { validateId(it, "Environment") }
        val current = loadStoredFromDisk()
        require(id == null || current.environments.any { it.id == id }) {
            "Environment '$id' does not exist"
        }
        // Preserve every ciphertext byte-for-byte. Selection must never turn a
        // temporarily undecryptable secret into a persisted empty placeholder.
        persistStored(current.copy(activeEnvironmentId = id))
    }

    override suspend fun backupCorruptStoreAndReset(): String? = mutationLocked {
        val emptyEnvelope = json.encodeToString(StoredEnvironmentFile())
            .toByteArray(StandardCharsets.UTF_8)
        store.backupCorruptStoreAndReset(emptyEnvelope)?.toString()
    }

    override suspend fun clear() = mutationLocked<Unit> {
        persistStored(StoredEnvironmentFile())
        Unit
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(ioDispatcher) {
        mutex.withLock { block() }
    }

    private suspend fun <T> mutationLocked(block: () -> T): T =
        withWorkbenchStorageMutationLock(mutex, processLock, ioDispatcher, block)

    private fun loadStoredFromDisk(): StoredEnvironmentFile {
        val bytes = store.readBytesOrNull() ?: return StoredEnvironmentFile()
        val stored = try {
            json.decodeFromString<StoredEnvironmentFile>(bytes.toString(StandardCharsets.UTF_8))
        } catch (error: Exception) {
            throw WorkbenchStorageCorruptionException(
                "Workbench environment store is corrupted; clear it explicitly before writing",
                error
            )
        }
        try {
            validateStoredFile(stored)
        } catch (error: Exception) {
            throw WorkbenchStorageCorruptionException(
                "Workbench environment store is invalid; clear it explicitly before writing",
                error
            )
        }
        return stored.copy(environments = sortStoredEnvironments(stored.environments))
    }

    private fun persistStored(stored: StoredEnvironmentFile): WorkbenchEnvironmentSnapshot {
        validateStoredFile(stored)
        val normalized = stored.copy(environments = sortStoredEnvironments(stored.environments))
        val bytes = json.encodeToString(normalized).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size.toLong() <= storageLimitBytes) {
            "Workbench environments exceed the storage limit " +
                "(${bytes.size} bytes > $storageLimitBytes bytes)"
        }
        store.write(bytes)
        return decodeSnapshot(normalized)
    }

    private fun decodeSnapshot(stored: StoredEnvironmentFile): WorkbenchEnvironmentSnapshot {
        val environments = stored.environments.map(::decodeEnvironment)
        val snapshot = WorkbenchEnvironmentSnapshot(
            activeEnvironmentId = stored.activeEnvironmentId,
            environments = sortEnvironments(environments)
        )
        validateSnapshot(snapshot)
        return snapshot
    }

    private fun sanitizeEnvironment(environment: WorkbenchEnvironment): WorkbenchEnvironment {
        val explicitSecrets = environment.variables
            .filter { (key, variable) -> variable.secret || redactor.isSensitiveKey(key) }
            .values
            .mapTo(linkedSetOf()) { it.value }
            .filterTo(linkedSetOf()) { it.isNotEmpty() }
        val safeVariables = environment.variables.mapValues { (key, variable) ->
            val isSecret = variable.secret || redactor.isSensitiveKey(key)
            if (isSecret) {
                variable.copy(secret = true)
            } else {
                variable.copy(value = redactor.redactText(variable.value, explicitSecrets))
            }
        }
        return environment.copy(
            name = redactor.redactText(environment.name, explicitSecrets),
            variables = safeVariables,
            secretsRequiringRestore = safeVariables
                .filterValues { it.secret && it.value.isEmpty() }
                .keys
        )
    }

    private fun encodeEnvironment(
        environment: WorkbenchEnvironment,
        previous: StoredEnvironment? = null,
        preserveSecretKeys: Set<String> = emptySet()
    ): StoredEnvironment {
        val variables = environment.variables.mapValues { (key, variable) ->
            val isSecret = variable.secret || redactor.isSensitiveKey(key)
            if (isSecret) {
                val previousSecret = previous?.variables?.get(key)?.takeIf {
                    it.secret || redactor.isSensitiveKey(key)
                }
                val preserveUnavailable = variable.value.isEmpty() &&
                    key in preserveSecretKeys &&
                    previousSecret?.encryptedValue?.isNotBlank() == true
                val encrypted = when {
                    preserveUnavailable -> checkNotNull(previousSecret).encryptedValue
                    variable.value.isNotEmpty() -> encryptValue(variable.value)
                    else -> ""
                }
                require(variable.value.isEmpty() || encrypted.isNotBlank()) {
                    "Encryption returned an empty value for secret variable '$key'"
                }
                require(variable.value.isEmpty() || encrypted != variable.value) {
                    "Encryption did not transform secret variable '$key'"
                }
                StoredEnvironmentVariable(
                    enabled = variable.enabled,
                    secret = true,
                    value = "",
                    encryptedValue = encrypted,
                    restoreRequired = if (preserveUnavailable) {
                        previousSecret?.restoreRequired == true
                    } else {
                        variable.value.isEmpty()
                    }
                )
            } else {
                StoredEnvironmentVariable(
                    enabled = variable.enabled,
                    secret = false,
                    value = variable.value
                )
            }
        }
        return StoredEnvironment(
            id = environment.id,
            name = environment.name,
            variables = variables,
            createdAtEpochMillis = environment.createdAtEpochMillis,
            updatedAtEpochMillis = environment.updatedAtEpochMillis
        )
    }

    private fun decodeEnvironment(stored: StoredEnvironment): WorkbenchEnvironment {
        val needsRestore = linkedSetOf<String>()
        val variables = stored.variables.mapValues { (key, variable) ->
            val isSecret = variable.secret || redactor.isSensitiveKey(key)
            if (!isSecret) {
                WorkbenchVariable(variable.value, variable.enabled, secret = false)
            } else {
                val decrypted = if (variable.encryptedValue.isNotBlank()) {
                    runCatching { decryptValue(variable.encryptedValue) }.getOrNull()
                } else null
                if (decrypted == null || variable.restoreRequired) needsRestore += key
                WorkbenchVariable(
                    value = decrypted.takeUnless { variable.restoreRequired }.orEmpty(),
                    enabled = variable.enabled,
                    secret = true
                )
            }
        }
        return WorkbenchEnvironment(
            id = stored.id,
            name = stored.name,
            variables = variables,
            createdAtEpochMillis = stored.createdAtEpochMillis,
            updatedAtEpochMillis = stored.updatedAtEpochMillis,
            secretsRequiringRestore = needsRestore
        )
    }

    private fun validateStoredFile(stored: StoredEnvironmentFile) {
        require(stored.version == STORAGE_VERSION) {
            "Unsupported environment store version ${stored.version}"
        }
        require(stored.environments.size <= maxEnvironments) {
            "Environment count exceeds the limit of $maxEnvironments"
        }
        require(stored.environments.map { it.id }.toSet().size == stored.environments.size) {
            "Environment IDs must be unique"
        }
        require(stored.activeEnvironmentId == null || stored.environments.any {
            it.id == stored.activeEnvironmentId
        }) { "Active environment does not exist" }
        stored.environments.forEach { environment ->
            validateId(environment.id, "Environment")
            require(environment.name.isNotBlank() && environment.name.length <= MAX_NAME_LENGTH) {
                "Environment name must contain 1 to $MAX_NAME_LENGTH characters"
            }
            validateTimestamps(environment.createdAtEpochMillis, environment.updatedAtEpochMillis)
            require(environment.variables.size <= maxVariablesPerEnvironment) {
                "Environment '${environment.id}' exceeds the variable limit of $maxVariablesPerEnvironment"
            }
            environment.variables.forEach { (key, variable) ->
                require(key.isNotBlank() && key.length <= MAX_VARIABLE_NAME_LENGTH) {
                    "Variable name must contain 1 to $MAX_VARIABLE_NAME_LENGTH characters"
                }
                val isSecret = variable.secret || redactor.isSensitiveKey(key)
                if (isSecret) {
                    require(variable.value.isEmpty()) {
                        "Secret variable '$key' contains plaintext in the environment store"
                    }
                    require(variable.encryptedValue.length <= MAX_ENCRYPTED_VALUE_CHARACTERS) {
                        "Encrypted variable '$key' is too large"
                    }
                } else {
                    require(variable.encryptedValue.isEmpty() && !variable.restoreRequired) {
                        "Non-secret variable '$key' contains secret-storage metadata"
                    }
                    require(variable.value.toByteArray(StandardCharsets.UTF_8).size <= MAX_VARIABLE_VALUE_BYTES) {
                        "Variable '$key' exceeds the value limit of $MAX_VARIABLE_VALUE_BYTES bytes"
                    }
                }
            }
            validateEnvironment(decodeEnvironment(environment))
        }
    }

    private fun validateSnapshot(snapshot: WorkbenchEnvironmentSnapshot) {
        require(snapshot.environments.size <= maxEnvironments) {
            "Environment count exceeds the limit of $maxEnvironments"
        }
        require(snapshot.environments.map { it.id }.toSet().size == snapshot.environments.size) {
            "Environment IDs must be unique"
        }
        require(snapshot.activeEnvironmentId == null || snapshot.environments.any {
            it.id == snapshot.activeEnvironmentId
        }) { "Active environment does not exist" }
        snapshot.environments.forEach(::validateEnvironment)
    }

    private fun validateEnvironment(environment: WorkbenchEnvironment) {
        validateId(environment.id, "Environment")
        require(environment.name.isNotBlank() && environment.name.length <= MAX_NAME_LENGTH) {
            "Environment name must contain 1 to $MAX_NAME_LENGTH characters"
        }
        validateTimestamps(environment.createdAtEpochMillis, environment.updatedAtEpochMillis)
        require(environment.variables.size <= maxVariablesPerEnvironment) {
            "Environment '${environment.id}' exceeds the variable limit of $maxVariablesPerEnvironment"
        }
        environment.variables.forEach { (key, variable) ->
            require(key.isNotBlank() && key.length <= MAX_VARIABLE_NAME_LENGTH) {
                "Variable name must contain 1 to $MAX_VARIABLE_NAME_LENGTH characters"
            }
            require(variable.value.toByteArray(StandardCharsets.UTF_8).size <= MAX_VARIABLE_VALUE_BYTES) {
                "Variable '$key' exceeds the value limit of $MAX_VARIABLE_VALUE_BYTES bytes"
            }
        }
        require(environment.secretsRequiringRestore.all { key ->
            environment.variables[key]?.secret == true
        }) { "Only secret variables can require restoration" }
    }

    private fun validateId(id: String, label: String) {
        require(id.isNotBlank() && id.length <= MAX_ID_LENGTH && id.none { it.isISOControl() }) {
            "$label ID must contain 1 to $MAX_ID_LENGTH printable characters"
        }
    }

    private fun validateTimestamps(created: Long, updated: Long) {
        require(created >= 0 && updated >= created) {
            "Timestamps must be non-negative and updated time cannot precede created time"
        }
    }

    private fun sortEnvironments(environments: List<WorkbenchEnvironment>) = environments.sortedWith(
        compareByDescending<WorkbenchEnvironment> { it.updatedAtEpochMillis }
            .thenByDescending { it.createdAtEpochMillis }
            .thenBy { it.id }
    )

    private fun sortStoredEnvironments(environments: List<StoredEnvironment>) = environments.sortedWith(
        compareByDescending<StoredEnvironment> { it.updatedAtEpochMillis }
            .thenByDescending { it.createdAtEpochMillis }
            .thenBy { it.id }
    )

    private companion object {
        const val STORAGE_VERSION = 1
        const val DEFAULT_MAX_ENVIRONMENTS = 100
        const val DEFAULT_MAX_VARIABLES = 256
        const val HARD_MAX_ENVIRONMENTS = 1_000
        const val HARD_MAX_VARIABLES = 10_000
        const val DEFAULT_STORAGE_LIMIT_BYTES = 4L * 1024L * 1024L
        const val MIN_STORAGE_BYTES = 256L
        const val MAX_STORAGE_BYTES = 16L * 1024L * 1024L
        const val MAX_ID_LENGTH = 128
        const val MAX_NAME_LENGTH = 256
        const val MAX_VARIABLE_NAME_LENGTH = 256
        const val MAX_VARIABLE_VALUE_BYTES = 64 * 1024
        const val MAX_ENCRYPTED_VALUE_CHARACTERS = 128 * 1024
    }
}

@Serializable
private data class StoredEnvironmentFile(
    val version: Int = 1,
    val activeEnvironmentId: String? = null,
    val environments: List<StoredEnvironment> = emptyList()
)

@Serializable
private data class StoredEnvironment(
    val id: String,
    val name: String,
    val variables: Map<String, StoredEnvironmentVariable> = emptyMap(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Serializable
private data class StoredEnvironmentVariable(
    val enabled: Boolean = true,
    val secret: Boolean = false,
    /** Always empty when [secret] is true. */
    val value: String = "",
    val encryptedValue: String = "",
    val restoreRequired: Boolean = false
)
