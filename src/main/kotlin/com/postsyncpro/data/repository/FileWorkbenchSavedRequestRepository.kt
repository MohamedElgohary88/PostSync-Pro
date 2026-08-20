package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchAssertion
import com.postsyncpro.domain.model.WorkbenchHistoryEntry
import com.postsyncpro.domain.model.WorkbenchRequiredSecret
import com.postsyncpro.domain.model.WorkbenchSavedCollection
import com.postsyncpro.domain.model.WorkbenchSavedFolder
import com.postsyncpro.domain.model.WorkbenchSavedRequest
import com.postsyncpro.domain.model.WorkbenchSavedWorkspace
import com.postsyncpro.domain.model.WorkbenchSecretLocation
import com.postsyncpro.domain.model.WorkbenchVariable
import com.postsyncpro.domain.repository.WorkbenchSavedRequestRepository
import com.postsyncpro.domain.repository.WorkbenchStorageCorruptionException
import com.postsyncpro.domain.usecase.SecretRedactor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * Atomic collection/folder/request storage. Request credentials are replaced
 * with placeholders before serialization and represented by safe restoration
 * metadata in [WorkbenchSavedRequest.requiredSecrets].
 */
class FileWorkbenchSavedRequestRepository(
    file: Path,
    private val redactor: SecretRedactor = SecretRedactor(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxCollections: Int = DEFAULT_MAX_COLLECTIONS,
    private val maxFolders: Int = DEFAULT_MAX_FOLDERS,
    private val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    storageLimitBytes: Long = DEFAULT_STORAGE_LIMIT_BYTES
) : WorkbenchSavedRequestRepository {
    private val mutex = workbenchStorageMutex(file)
    private val processLock = WorkbenchStorageProcessLock(file, "Workbench saved requests")
    private val storageLimitBytes = storageLimitBytes
    private val store = SecureAtomicWorkbenchFile(file, storageLimitBytes, "Workbench saved requests")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "authType"
    }

    init {
        require(maxCollections in 1..HARD_MAX_COLLECTIONS)
        require(maxFolders in 1..HARD_MAX_FOLDERS)
        require(maxRequests in 1..HARD_MAX_REQUESTS)
        require(storageLimitBytes in MIN_STORAGE_BYTES..MAX_STORAGE_BYTES) {
            "Saved request storage limit must be between $MIN_STORAGE_BYTES and $MAX_STORAGE_BYTES bytes"
        }
    }

    override suspend fun snapshot(): WorkbenchSavedWorkspace = locked { loadFromDisk() }

    override suspend fun findRequest(id: String): WorkbenchSavedRequest? = locked {
        validateId(id, "Request")
        loadFromDisk().requests.firstOrNull { it.id == id }
    }

    override suspend fun search(
        query: String,
        collectionId: String?,
        folderId: String?
    ): List<WorkbenchSavedRequest> = locked {
        require(query.length <= MAX_SEARCH_LENGTH) { "Search query is too long" }
        collectionId?.let { validateId(it, "Collection") }
        folderId?.let { validateId(it, "Folder") }
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
        loadFromDisk().requests.filter { request ->
            (collectionId == null || request.collectionId == collectionId) &&
                (folderId == null || request.folderId == folderId) &&
                tokens.all { token -> token in request.searchableText() }
        }
    }

    override suspend fun upsertCollection(
        collection: WorkbenchSavedCollection
    ): WorkbenchSavedWorkspace = mutationLocked {
        val sanitized = sanitizeCollection(collection)
        validateCollection(sanitized)
        val current = loadFromDisk()
        val candidate = current.copy(
            collections = current.collections.filterNot { it.id == sanitized.id } + sanitized
        )
        persist(candidate)
    }

    override suspend fun upsertCollectionWithRequest(
        collection: WorkbenchSavedCollection,
        request: WorkbenchSavedRequest
    ): WorkbenchSavedWorkspace = mutationLocked {
        val safeCollection = sanitizeCollection(collection)
        val safeRequest = sanitizeRequest(request)
        validateCollection(safeCollection)
        validateRequestBasics(safeRequest)
        require(safeRequest.collectionId == safeCollection.id) {
            "Request '${safeRequest.id}' must belong to collection '${safeCollection.id}'"
        }

        val current = loadFromDisk()
        persist(
            current.copy(
                collections = current.collections.filterNot { it.id == safeCollection.id } +
                    safeCollection,
                requests = current.requests.filterNot { it.id == safeRequest.id } + safeRequest
            )
        )
    }

    override suspend fun deleteCollection(id: String): WorkbenchSavedWorkspace = mutationLocked {
        validateId(id, "Collection")
        val current = loadFromDisk()
        persist(
            current.copy(
                collections = current.collections.filterNot { it.id == id },
                folders = current.folders.filterNot { it.collectionId == id },
                requests = current.requests.filterNot { it.collectionId == id }
            )
        )
    }

    override suspend fun upsertFolder(folder: WorkbenchSavedFolder): WorkbenchSavedWorkspace = mutationLocked {
        val sanitized = sanitizeFolder(folder)
        validateFolderBasics(sanitized)
        val current = loadFromDisk()
        val candidate = current.copy(
            folders = current.folders.filterNot { it.id == sanitized.id } + sanitized
        )
        persist(candidate)
    }

    override suspend fun deleteFolder(id: String): WorkbenchSavedWorkspace = mutationLocked {
        validateId(id, "Folder")
        val current = loadFromDisk()
        val descendants = linkedSetOf(id)
        var changed: Boolean
        do {
            val before = descendants.size
            current.folders
                .filter { it.parentFolderId in descendants }
                .mapTo(descendants) { it.id }
            changed = descendants.size != before
        } while (changed)
        persist(
            current.copy(
                folders = current.folders.filterNot { it.id in descendants },
                requests = current.requests.filterNot { it.folderId in descendants }
            )
        )
    }

    override suspend fun upsertRequest(request: WorkbenchSavedRequest): WorkbenchSavedWorkspace = mutationLocked {
        val sanitized = sanitizeRequest(request)
        validateRequestBasics(sanitized)
        val current = loadFromDisk()
        val candidate = current.copy(
            requests = current.requests.filterNot { it.id == sanitized.id } + sanitized
        )
        persist(candidate)
    }

    override suspend fun deleteRequest(id: String): WorkbenchSavedWorkspace = mutationLocked {
        validateId(id, "Request")
        val current = loadFromDisk()
        persist(current.copy(requests = current.requests.filterNot { it.id == id }))
    }

    override suspend fun backupCorruptStoreAndReset(): String? = mutationLocked {
        val emptyEnvelope = json.encodeToString(
            StoredSavedWorkspace(workspace = WorkbenchSavedWorkspace())
        ).toByteArray(StandardCharsets.UTF_8)
        store.backupCorruptStoreAndReset(emptyEnvelope)?.toString()
    }

    override suspend fun clear() = mutationLocked<Unit> {
        persist(WorkbenchSavedWorkspace())
        Unit
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(ioDispatcher) {
        mutex.withLock { block() }
    }

    private suspend fun <T> mutationLocked(block: () -> T): T =
        withWorkbenchStorageMutationLock(mutex, processLock, ioDispatcher, block)

    private fun loadFromDisk(): WorkbenchSavedWorkspace {
        val bytes = store.readBytesOrNull() ?: return WorkbenchSavedWorkspace()
        val stored = try {
            json.decodeFromString<StoredSavedWorkspace>(bytes.toString(StandardCharsets.UTF_8))
        } catch (error: Exception) {
            throw WorkbenchStorageCorruptionException(
                "Workbench saved request store is corrupted; clear it explicitly before writing",
                error
            )
        }
        if (stored.version != STORAGE_VERSION) {
            throw WorkbenchStorageCorruptionException(
                "Unsupported Workbench saved request store version ${stored.version}"
            )
        }
        return try {
            val normalized = normalize(stored.workspace)
            validateWorkspace(normalized)
            normalized
        } catch (error: Exception) {
            throw WorkbenchStorageCorruptionException(
                "Workbench saved request store is invalid; clear it explicitly before writing",
                error
            )
        }
    }

    private fun persist(workspace: WorkbenchSavedWorkspace): WorkbenchSavedWorkspace {
        val normalized = normalize(workspace)
        validateWorkspace(normalized)
        val bytes = json.encodeToString(StoredSavedWorkspace(workspace = normalized))
            .toByteArray(StandardCharsets.UTF_8)
        require(bytes.size.toLong() <= storageLimitBytes) {
            "Workbench saved requests exceed the storage limit " +
                "(${bytes.size} bytes > $storageLimitBytes bytes)"
        }
        store.write(bytes)
        return normalized
    }

    private fun normalize(workspace: WorkbenchSavedWorkspace) = WorkbenchSavedWorkspace(
        collections = workspace.collections.map(::sanitizeCollection).sortedWith(
            compareByDescending<WorkbenchSavedCollection> { it.updatedAtEpochMillis }
                .thenByDescending { it.createdAtEpochMillis }
                .thenBy { it.id }
        ),
        folders = workspace.folders.map(::sanitizeFolder).sortedWith(
            compareByDescending<WorkbenchSavedFolder> { it.updatedAtEpochMillis }
                .thenByDescending { it.createdAtEpochMillis }
                .thenBy { it.id }
        ),
        requests = workspace.requests.map(::sanitizeRequest).sortedWith(
            compareByDescending<WorkbenchSavedRequest> { it.updatedAtEpochMillis }
                .thenByDescending { it.createdAtEpochMillis }
                .thenBy { it.id }
        )
    )

    private fun sanitizeCollection(collection: WorkbenchSavedCollection) = collection.copy(
        name = redactor.redactText(collection.name),
        description = redactor.redactText(collection.description)
    )

    private fun sanitizeFolder(folder: WorkbenchSavedFolder) = folder.copy(
        name = redactor.redactText(folder.name)
    )

    private fun sanitizeRequest(request: WorkbenchSavedRequest): WorkbenchSavedRequest {
        validateRequestInputBounds(request)
        val classifiedVariables = request.variables.mapValues { (key, variable) ->
            if (variable.secret || redactor.isSensitiveKey(key)) variable.copy(secret = true) else variable
        }
        val requestSecrets = redactor.collectSecrets(request.draft, classifiedVariables).toMutableSet()
        requestSecrets += collectSensitiveBodyValues(request.draft.body)
        val safeHistory = redactor.sanitizeHistoryEntry(
            WorkbenchHistoryEntry(
                id = request.id,
                createdAtEpochMillis = request.createdAtEpochMillis,
                request = request.draft,
                variables = classifiedVariables
            )
        )
        val safeDraft = safeHistory.request.copy(
            name = redactor.redactText(safeHistory.request.name, requestSecrets),
            url = redactor.redactText(safeHistory.request.url, requestSecrets),
            query = safeHistory.request.query.map { field ->
                field.copy(
                    key = redactor.redactText(field.key, requestSecrets),
                    value = redactor.redactText(field.value, requestSecrets)
                )
            },
            headers = redactor.redactHeaders(safeHistory.request.headers, requestSecrets).map { field ->
                field.copy(key = redactor.redactText(field.key, requestSecrets))
            },
            body = sanitizeBodyPreservingSafeFormatting(request.draft.body, requestSecrets),
            auth = when (val auth = safeHistory.request.auth) {
                WorkbenchAuth.None -> WorkbenchAuth.None
                is WorkbenchAuth.Bearer -> auth
                is WorkbenchAuth.Basic -> auth.copy(
                    username = redactor.redactText(auth.username, requestSecrets)
                )
                is WorkbenchAuth.ApiKey -> auth.copy(
                    name = redactor.redactText(auth.name, requestSecrets)
                )
            }
        )
        val safeAssertions = request.assertions.map { assertion ->
            sanitizeAssertion(assertion, requestSecrets)
        }
        val requiredSecrets = deriveRequiredSecrets(
            request = request,
            classifiedVariables = classifiedVariables,
            safeDraft = safeDraft,
            safeAssertions = safeAssertions,
            previous = request.requiredSecrets,
            explicitSecrets = requestSecrets
        )
        val safeVariables = safeHistory.variables.mapValues { (_, variable) ->
            if (variable.secret) variable.copy(value = "")
            else variable.copy(value = redactor.redactText(variable.value, requestSecrets))
        }.toSortedMap()
        return request.copy(
            name = redactor.redactText(request.name, requestSecrets),
            description = redactor.redactText(request.description, requestSecrets),
            tags = request.tags
                .map { redactor.redactText(it, requestSecrets) }
                .sorted()
                .toCollection(linkedSetOf()),
            draft = safeDraft,
            variables = safeVariables,
            assertions = safeAssertions,
            requiredSecrets = requiredSecrets
        )
    }

    private fun sanitizeAssertion(
        assertion: WorkbenchAssertion,
        explicitSecrets: Set<String>
    ): WorkbenchAssertion {
        val sanitized = redactor.sanitizeAssertion(assertion, explicitSecrets)
        return when {
            assertion is WorkbenchAssertion.HeaderExists &&
                sanitized is WorkbenchAssertion.HeaderExists &&
                assertion.name != sanitized.name -> sanitized.copy(enabled = false)
            assertion is WorkbenchAssertion.JsonPathExists &&
                sanitized is WorkbenchAssertion.JsonPathExists &&
                assertion.path != sanitized.path -> sanitized.copy(enabled = false)
            else -> sanitized
        }
    }

    private fun deriveRequiredSecrets(
        request: WorkbenchSavedRequest,
        classifiedVariables: Map<String, WorkbenchVariable>,
        safeDraft: com.postsyncpro.domain.model.WorkbenchRequestDraft,
        safeAssertions: List<WorkbenchAssertion>,
        previous: List<WorkbenchRequiredSecret>,
        explicitSecrets: Set<String>
    ): List<WorkbenchRequiredSecret> {
        val required = linkedMapOf<String, WorkbenchRequiredSecret>()
        previous.forEach { secret ->
            val safeId = redactor.redactText(secret.id, explicitSecrets)
            required[safeId] = secret.copy(
                id = safeId,
                name = redactor.redactText(secret.name, explicitSecrets)
            )
        }
        fun add(id: String, location: WorkbenchSecretLocation, name: String) {
            val safeId = redactor.redactText(id, explicitSecrets)
            required[safeId] = WorkbenchRequiredSecret(
                safeId,
                location,
                redactor.redactText(name, explicitSecrets)
            )
        }

        when (request.draft.auth) {
            WorkbenchAuth.None -> Unit
            is WorkbenchAuth.Bearer -> add("auth:bearer", WorkbenchSecretLocation.AUTH, "Bearer token")
            is WorkbenchAuth.Basic -> add("auth:basic:password", WorkbenchSecretLocation.AUTH, "Basic password")
            is WorkbenchAuth.ApiKey -> add("auth:api-key", WorkbenchSecretLocation.AUTH, "API key")
        }
        classifiedVariables.forEach { (key, variable) ->
            if (variable.secret) add("variable:$key", WorkbenchSecretLocation.VARIABLE, key)
        }
        request.draft.headers.forEachIndexed { index, field ->
            val safe = safeDraft.headers.getOrNull(index)
            if (field.sensitive || redactor.isSensitiveKey(field.key) || safe?.value != field.value) {
                add("header:$index", WorkbenchSecretLocation.HEADER, field.key.ifBlank { "Header ${index + 1}" })
            }
        }
        request.draft.query.forEachIndexed { index, field ->
            val safe = safeDraft.query.getOrNull(index)
            if (field.sensitive || redactor.isSensitiveKey(field.key) || safe?.value != field.value) {
                add("query:$index", WorkbenchSecretLocation.QUERY, field.key.ifBlank { "Query ${index + 1}" })
            }
        }
        if (safeDraft.url != request.draft.url || SENSITIVE_URL_PARAMETER.containsMatchIn(request.draft.url)) {
            add("url", WorkbenchSecretLocation.URL, "URL parameter")
        }
        if (bodyRequiresSecretRestoration(request.draft.body, safeDraft.body)) {
            add("body", WorkbenchSecretLocation.BODY, "Request body")
        }
        request.assertions.zip(safeAssertions).forEachIndexed { index, (original, sanitized) ->
            if (assertionRequiresRestoration(original, sanitized)) {
                add("assertion:$index", WorkbenchSecretLocation.ASSERTION, "Assertion ${index + 1}")
            }
        }
        return required.values.sortedWith(
            compareBy<WorkbenchRequiredSecret> { it.location.ordinal }.thenBy { it.id }
        )
    }

    /**
     * Credential classification belongs to the central sanitizer. Comparing
     * its output also covers request/environment secret matches that were not
     * explicitly marked sensitive on the assertion itself.
     */
    private fun assertionRequiresRestoration(
        original: WorkbenchAssertion,
        sanitized: WorkbenchAssertion
    ): Boolean = when {
        original is WorkbenchAssertion.HeaderEquals &&
            sanitized is WorkbenchAssertion.HeaderEquals ->
            sanitized.sensitive || sanitized.expected != original.expected
        original is WorkbenchAssertion.HeaderContains &&
            sanitized is WorkbenchAssertion.HeaderContains ->
            sanitized.sensitive || sanitized.expected != original.expected
        original is WorkbenchAssertion.BodyContains &&
            sanitized is WorkbenchAssertion.BodyContains ->
            sanitized.sensitive || sanitized.expected != original.expected
        original is WorkbenchAssertion.JsonPathEquals &&
            sanitized is WorkbenchAssertion.JsonPathEquals ->
            sanitized.sensitive || sanitized.expectedJson != original.expectedJson
        else -> false
    }

    private fun collectSensitiveBodyValues(body: String): Set<String> {
        if (body.isBlank() || body.length > MAX_REQUEST_BODY_BYTES || !hasSafeJsonDepth(body)) {
            return collectSensitiveFormValues(body)
        }
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return collectSensitiveFormValues(body)
        val values = linkedSetOf<String>()
        val stack = ArrayDeque<JsonElement>()
        stack.add(parsed)
        var visited = 0
        fun collectPrimitiveValues(root: JsonElement) {
            val sensitiveStack = ArrayDeque<JsonElement>()
            sensitiveStack.add(root)
            while (sensitiveStack.isNotEmpty() && visited++ < MAX_JSON_NODES) {
                when (val value = sensitiveStack.removeLast()) {
                    is JsonPrimitive -> values += value.content
                    is JsonObject -> value.values.forEach(sensitiveStack::add)
                    is JsonArray -> value.forEach(sensitiveStack::add)
                }
            }
        }
        while (stack.isNotEmpty() && visited++ < MAX_JSON_NODES) {
            when (val element = stack.removeLast()) {
                is JsonObject -> element.forEach { (key, value) ->
                    if (redactor.isSensitiveKey(key)) {
                        collectPrimitiveValues(value)
                    } else {
                        stack.add(value)
                    }
                }
                is JsonArray -> element.forEach(stack::add)
                else -> Unit
            }
        }
        return values.filterTo(linkedSetOf()) { it.isNotEmpty() }
    }

    private fun bodyRequiresSecretRestoration(original: String, sanitized: String): Boolean {
        val canonicalOriginal = if (hasSafeJsonDepth(original)) {
            runCatching { json.parseToJsonElement(original).toString() }.getOrNull()
        } else null
        val canonicalSanitized = if (hasSafeJsonDepth(sanitized)) {
            runCatching { json.parseToJsonElement(sanitized).toString() }.getOrNull()
        } else null
        return (canonicalSanitized ?: sanitized) != (canonicalOriginal ?: original)
    }

    private fun sanitizeBodyPreservingSafeFormatting(body: String, explicitSecrets: Set<String>): String {
        val sanitized = redactor.redactBodyForPersistence(
            body,
            explicitSecrets,
            maxOutputCharacters = MAX_REQUEST_BODY_BYTES
        )
        val canonicalOriginal = if (hasSafeJsonDepth(body)) {
            runCatching { json.parseToJsonElement(body).toString() }.getOrNull()
        } else null
        return if (canonicalOriginal != null && sanitized == canonicalOriginal) body else sanitized
    }

    private fun collectSensitiveFormValues(body: String): Set<String> = body
        .splitToSequence('&')
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val key = decodeFormValue(part.substring(0, separator))
            if (!redactor.isSensitiveKey(key)) return@mapNotNull null
            decodeFormValue(part.substring(separator + 1)).takeIf(String::isNotEmpty)
        }
        .take(MAX_FORM_FIELDS)
        .toCollection(linkedSetOf())

    private fun decodeFormValue(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8)
    }.getOrDefault(value)

    private fun hasSafeJsonDepth(input: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        input.forEach { char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{', '[' -> if (++depth > MAX_JSON_DEPTH) return false
                    '}', ']' -> depth--
                }
            }
        }
        return true
    }

    private fun validateWorkspace(workspace: WorkbenchSavedWorkspace) {
        require(workspace.collections.size <= maxCollections) {
            "Collection count exceeds the limit of $maxCollections"
        }
        require(workspace.folders.size <= maxFolders) {
            "Folder count exceeds the limit of $maxFolders"
        }
        require(workspace.requests.size <= maxRequests) {
            "Saved request count exceeds the limit of $maxRequests"
        }
        requireUniqueIds(workspace.collections.map { it.id }, "Collection")
        requireUniqueIds(workspace.folders.map { it.id }, "Folder")
        requireUniqueIds(workspace.requests.map { it.id }, "Request")
        workspace.collections.forEach(::validateCollection)

        val collections = workspace.collections.associateBy { it.id }
        val folders = workspace.folders.associateBy { it.id }
        workspace.folders.forEach { folder ->
            validateFolderBasics(folder)
            require(folder.collectionId in collections) {
                "Folder '${folder.id}' references a missing collection"
            }
            folder.parentFolderId?.let { parentId ->
                val parent = folders[parentId]
                    ?: throw IllegalArgumentException("Folder '${folder.id}' references a missing parent")
                require(parent.collectionId == folder.collectionId) {
                    "Folder '${folder.id}' and its parent must share a collection"
                }
            }
            require(!hasFolderCycle(folder.id, folders)) {
                "Folder '${folder.id}' introduces a parent cycle"
            }
        }
        workspace.requests.forEach { request ->
            validateRequestBasics(request)
            require(request.collectionId in collections) {
                "Request '${request.id}' references a missing collection"
            }
            request.folderId?.let { folderId ->
                val folder = folders[folderId]
                    ?: throw IllegalArgumentException("Request '${request.id}' references a missing folder")
                require(folder.collectionId == request.collectionId) {
                    "Request '${request.id}' and its folder must share a collection"
                }
            }
        }
    }

    private fun validateCollection(collection: WorkbenchSavedCollection) {
        validateId(collection.id, "Collection")
        validateName(collection.name, "Collection")
        require(collection.description.length <= MAX_DESCRIPTION_LENGTH) { "Collection description is too long" }
        validateTimestamps(collection.createdAtEpochMillis, collection.updatedAtEpochMillis)
    }

    private fun validateFolderBasics(folder: WorkbenchSavedFolder) {
        validateId(folder.id, "Folder")
        validateId(folder.collectionId, "Collection")
        folder.parentFolderId?.let { validateId(it, "Parent folder") }
        require(folder.id != folder.parentFolderId) { "A folder cannot be its own parent" }
        validateName(folder.name, "Folder")
        validateTimestamps(folder.createdAtEpochMillis, folder.updatedAtEpochMillis)
    }

    private fun validateRequestBasics(request: WorkbenchSavedRequest) {
        validateRequestInputBounds(request)
        validateId(request.id, "Request")
        validateId(request.collectionId, "Collection")
        request.folderId?.let { validateId(it, "Folder") }
        validateName(request.name, "Request")
        require(request.description.length <= MAX_DESCRIPTION_LENGTH) { "Request description is too long" }
        require(request.tags.size <= MAX_TAGS && request.tags.all {
            it.isNotBlank() && it.length <= MAX_TAG_LENGTH
        }) { "Request tags are invalid" }
        require(request.variables.size <= MAX_REQUEST_VARIABLES) { "Request has too many variables" }
        require(request.draft.query.size + request.draft.headers.size <= MAX_REQUEST_FIELDS) {
            "Request has too many fields"
        }
        require(request.draft.body.toByteArray(StandardCharsets.UTF_8).size <= MAX_REQUEST_BODY_BYTES) {
            "Request body exceeds the storage safety limit"
        }
        require(request.requiredSecrets.size <= MAX_REQUIRED_SECRETS) {
            "Request has too many secret restoration markers"
        }
        require(request.assertions.size <= MAX_ASSERTIONS) { "Request has too many assertions" }
        request.requiredSecrets.forEach { secret ->
            require(secret.id.isNotBlank() && secret.id.length <= MAX_REQUIRED_SECRET_LENGTH) {
                "Secret restoration ID is invalid"
            }
            require(secret.name.isNotBlank() && secret.name.length <= MAX_REQUIRED_SECRET_LENGTH) {
                "Secret restoration name is invalid"
            }
        }
        validateTimestamps(request.createdAtEpochMillis, request.updatedAtEpochMillis)
    }

    private fun validateRequestInputBounds(request: WorkbenchSavedRequest) {
        require(request.draft.name.length <= MAX_NAME_LENGTH) { "Draft name is too long" }
        require(request.draft.url.length <= MAX_URL_LENGTH) { "Request URL is too long" }
        require(request.draft.query.size + request.draft.headers.size <= MAX_REQUEST_FIELDS) {
            "Request has too many fields"
        }
        (request.draft.query + request.draft.headers).forEach { field ->
            require(field.key.length <= MAX_FIELD_KEY_LENGTH) { "Request field name is too long" }
            require(field.value.length <= MAX_FIELD_VALUE_LENGTH) { "Request field value is too long" }
        }
        require(request.draft.body.toByteArray(StandardCharsets.UTF_8).size <= MAX_REQUEST_BODY_BYTES) {
            "Request body exceeds the storage safety limit"
        }
        when (val auth = request.draft.auth) {
            WorkbenchAuth.None -> Unit
            is WorkbenchAuth.Bearer -> require(auth.token.length <= MAX_SECRET_VALUE_LENGTH) {
                "Bearer token is too long"
            }
            is WorkbenchAuth.Basic -> {
                require(auth.username.length <= MAX_FIELD_VALUE_LENGTH) { "Basic username is too long" }
                require(auth.password.length <= MAX_SECRET_VALUE_LENGTH) { "Basic password is too long" }
            }
            is WorkbenchAuth.ApiKey -> {
                require(auth.name.length <= MAX_FIELD_KEY_LENGTH) { "API key name is too long" }
                require(auth.value.length <= MAX_SECRET_VALUE_LENGTH) { "API key value is too long" }
            }
        }
        require(request.variables.size <= MAX_REQUEST_VARIABLES) { "Request has too many variables" }
        request.variables.forEach { (key, variable) ->
            require(key.isNotBlank() && key.length <= MAX_VARIABLE_NAME_LENGTH) {
                "Request variable name is invalid"
            }
            require(variable.value.toByteArray(StandardCharsets.UTF_8).size <= MAX_VARIABLE_VALUE_BYTES) {
                "Request variable '$key' is too large"
            }
        }
        require(request.assertions.size <= MAX_ASSERTIONS) { "Request has too many assertions" }
        request.assertions.forEach(::validateAssertionBounds)
    }

    private fun validateAssertionBounds(assertion: WorkbenchAssertion) {
        require(assertion.id.length <= MAX_ASSERTION_ID_LENGTH) { "Assertion ID is too long" }
        when (assertion) {
            is WorkbenchAssertion.StatusEquals,
            is WorkbenchAssertion.StatusRange,
            is WorkbenchAssertion.ResponseTimeMax -> Unit
            is WorkbenchAssertion.HeaderExists -> {
                require(assertion.name.length <= MAX_FIELD_KEY_LENGTH) { "Assertion header name is too long" }
            }
            is WorkbenchAssertion.HeaderEquals -> {
                require(assertion.name.length <= MAX_FIELD_KEY_LENGTH) { "Assertion header name is too long" }
                require(assertion.expected.length <= MAX_ASSERTION_EXPECTED_LENGTH) {
                    "Assertion expected value is too long"
                }
            }
            is WorkbenchAssertion.HeaderContains -> {
                require(assertion.name.length <= MAX_FIELD_KEY_LENGTH) { "Assertion header name is too long" }
                require(assertion.expected.length <= MAX_ASSERTION_EXPECTED_LENGTH) {
                    "Assertion expected value is too long"
                }
            }
            is WorkbenchAssertion.BodyContains -> require(
                assertion.expected.length <= MAX_ASSERTION_EXPECTED_LENGTH
            ) { "Assertion expected value is too long" }
            is WorkbenchAssertion.JsonPathExists -> require(assertion.path.length <= MAX_JSON_PATH_LENGTH) {
                "Assertion JSON path is too long"
            }
            is WorkbenchAssertion.JsonPathEquals -> {
                require(assertion.path.length <= MAX_JSON_PATH_LENGTH) { "Assertion JSON path is too long" }
                require(assertion.expectedJson.length <= MAX_ASSERTION_EXPECTED_LENGTH) {
                    "Assertion expected JSON is too long"
                }
            }
        }
    }

    private fun validateId(id: String, label: String) {
        require(id.isNotBlank() && id.length <= MAX_ID_LENGTH && id.none { it.isISOControl() }) {
            "$label ID must contain 1 to $MAX_ID_LENGTH printable characters"
        }
    }

    private fun validateName(name: String, label: String) {
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) {
            "$label name must contain 1 to $MAX_NAME_LENGTH characters"
        }
    }

    private fun validateTimestamps(created: Long, updated: Long) {
        require(created >= 0 && updated >= created) {
            "Timestamps must be non-negative and updated time cannot precede created time"
        }
    }

    private fun requireUniqueIds(ids: List<String>, label: String) {
        require(ids.toSet().size == ids.size) { "$label IDs must be unique" }
    }

    private fun hasFolderCycle(id: String, folders: Map<String, WorkbenchSavedFolder>): Boolean {
        val visited = linkedSetOf<String>()
        var current: String? = id
        while (current != null) {
            if (!visited.add(current)) return true
            current = folders[current]?.parentFolderId
        }
        return false
    }

    private fun WorkbenchSavedRequest.searchableText(): String = buildString {
        append(name).append(' ')
        append(description).append(' ')
        tags.forEach { append(it).append(' ') }
        append(draft.method.name).append(' ')
        append(draft.url)
    }.lowercase()

    private companion object {
        const val STORAGE_VERSION = 1
        const val DEFAULT_MAX_COLLECTIONS = 250
        const val DEFAULT_MAX_FOLDERS = 2_000
        const val DEFAULT_MAX_REQUESTS = 5_000
        const val HARD_MAX_COLLECTIONS = 2_000
        const val HARD_MAX_FOLDERS = 20_000
        const val HARD_MAX_REQUESTS = 50_000
        const val DEFAULT_STORAGE_LIMIT_BYTES = 32L * 1024L * 1024L
        const val MIN_STORAGE_BYTES = 512L
        const val MAX_STORAGE_BYTES = 64L * 1024L * 1024L
        const val MAX_ID_LENGTH = 128
        const val MAX_NAME_LENGTH = 256
        const val MAX_DESCRIPTION_LENGTH = 4_096
        const val MAX_TAGS = 64
        const val MAX_TAG_LENGTH = 128
        const val MAX_SEARCH_LENGTH = 512
        const val MAX_REQUEST_VARIABLES = 1_000
        const val MAX_REQUEST_FIELDS = 5_000
        // SecretRedactor processes bodies completely only through 2 MiB. A
        // larger draft is rejected instead of being normalized into a silently
        // truncated persisted request.
        const val MAX_REQUEST_BODY_BYTES = 2 * 1024 * 1024
        const val MAX_URL_LENGTH = 64 * 1024
        const val MAX_FIELD_KEY_LENGTH = 512
        const val MAX_FIELD_VALUE_LENGTH = 1024 * 1024
        const val MAX_VARIABLE_NAME_LENGTH = 256
        const val MAX_VARIABLE_VALUE_BYTES = 64 * 1024
        const val MAX_SECRET_VALUE_LENGTH = 64 * 1024
        const val MAX_REQUIRED_SECRETS = 2_048
        const val MAX_REQUIRED_SECRET_LENGTH = 512
        const val MAX_ASSERTIONS = 1_000
        const val MAX_ASSERTION_ID_LENGTH = 128
        const val MAX_ASSERTION_EXPECTED_LENGTH = 256 * 1024
        const val MAX_JSON_PATH_LENGTH = 4_096
        const val MAX_JSON_DEPTH = 128
        const val MAX_JSON_NODES = 100_000
        const val MAX_FORM_FIELDS = 10_000

        val SENSITIVE_URL_PARAMETER = Regex(
            "(?:^|[?&])(?:access[_-]?token|refresh[_-]?token|token|api[_-]?key|key|secret|password|passwd|signature|authorization)=",
            RegexOption.IGNORE_CASE
        )
    }
}

@Serializable
private data class StoredSavedWorkspace(
    val version: Int = 1,
    val workspace: WorkbenchSavedWorkspace = WorkbenchSavedWorkspace()
)
