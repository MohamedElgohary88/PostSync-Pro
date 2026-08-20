package com.postsyncpro.workbench

import com.postsyncpro.data.repository.FileWorkbenchEnvironmentRepository
import com.postsyncpro.data.repository.FileWorkbenchSavedRequestRepository
import com.postsyncpro.data.repository.SecureAtomicWorkbenchFile
import com.postsyncpro.domain.model.WorkbenchAssertion
import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchEnvironment
import com.postsyncpro.domain.model.WorkbenchHttpMethod
import com.postsyncpro.domain.model.WorkbenchRequestDraft
import com.postsyncpro.domain.model.WorkbenchRequestField
import com.postsyncpro.domain.model.WorkbenchSavedCollection
import com.postsyncpro.domain.model.WorkbenchSavedFolder
import com.postsyncpro.domain.model.WorkbenchSavedRequest
import com.postsyncpro.domain.model.WorkbenchSecretLocation
import com.postsyncpro.domain.model.WorkbenchVariable
import com.postsyncpro.domain.repository.WorkbenchStorageCorruptionException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.io.IOException
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkbenchStorageRepositoriesTest {
    @Test
    fun `environment secrets are encrypted and selection survives reload`() = runTest {
        val directory = createTempDirectory("postsync-environments")
        val file = directory.resolve("environments.json")
        val repository = environmentRepository(file)
        val secret = "never-write-this-environment-secret"

        repository.upsert(environment("older", "Duplicate", 1, secret))
        repository.upsert(environment("newer", "Duplicate", 2, secret))
        val selected = repository.select("older")

        assertEquals("older", selected.activeEnvironmentId)
        assertEquals(listOf("newer", "older"), selected.environments.map { it.id })
        assertEquals(secret, selected.activeEnvironment?.variables?.get("apiToken")?.value)
        val disk = Files.readString(file)
        assertFalse(secret in disk)
        assertContains(disk, "encryptedValue")
        assertFalse(Files.list(directory).use { paths ->
            paths.anyMatch { it.fileName.toString().contains(".tmp-") }
        })

        val reloaded = environmentRepository(file).snapshot()
        assertEquals("older", reloaded.activeEnvironmentId)
        assertEquals(secret, reloaded.activeEnvironment?.variables?.get("apiToken")?.value)
        assertTrue(reloaded.activeEnvironment?.secretsRequiringRestore.orEmpty().isEmpty())
    }

    @Test
    fun `selection preserves ciphertext when a secret cannot currently decrypt`() = runTest {
        val file = createTempDirectory("postsync-environment-ciphertext").resolve("environments.json")
        environmentRepository(file).upsert(environment("env", "Environment", 1, "recoverable-secret"))
        val encryptedBefore = encryptedEnvironmentValue(file, "apiToken")

        val unavailable = FileWorkbenchEnvironmentRepository(
            file = file,
            encryptValue = ::encodeSecret,
            decryptValue = { error("keychain temporarily unavailable") },
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val degraded = unavailable.select("env")

        assertEquals("", degraded.activeEnvironment?.variables?.get("apiToken")?.value)
        assertEquals(setOf("apiToken"), degraded.activeEnvironment?.secretsRequiringRestore)
        unavailable.upsert(
            requireNotNull(degraded.activeEnvironment).copy(
                name = "Renamed while locked",
                updatedAtEpochMillis = 2
            )
        )
        assertEquals(encryptedBefore, encryptedEnvironmentValue(file, "apiToken"))
        assertEquals(
            "recoverable-secret",
            environmentRepository(file).snapshot().activeEnvironment?.variables?.get("apiToken")?.value
        )
    }

    @Test
    fun `one unavailable environment secret does not hide healthy variables`() = runTest {
        val file = createTempDirectory("postsync-environment-partial-decrypt").resolve("environments.json")
        environmentRepository(file).upsert(
            WorkbenchEnvironment(
                id = "env",
                name = "Environment",
                variables = mapOf(
                    "apiToken" to WorkbenchVariable("unavailable", secret = true),
                    "password" to WorkbenchVariable("healthy", secret = true),
                    "baseUrl" to WorkbenchVariable("https://example.test")
                ),
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1
            )
        )
        val partiallyAvailable = FileWorkbenchEnvironmentRepository(
            file = file,
            encryptValue = ::encodeSecret,
            decryptValue = { encrypted ->
                decodeSecret(encrypted).also { if (it == "unavailable") error("one bad envelope") }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler)
        ).snapshot().environments.single()

        assertEquals("", partiallyAvailable.variables.getValue("apiToken").value)
        assertEquals("healthy", partiallyAvailable.variables.getValue("password").value)
        assertEquals("https://example.test", partiallyAvailable.variables.getValue("baseUrl").value)
        assertEquals(setOf("apiToken"), partiallyAvailable.secretsRequiringRestore)
    }

    @Test
    fun `environment corruption blocks mutations until explicit clear`() = runTest {
        val file = createTempDirectory("postsync-environment-corrupt").resolve("environments.json")
        Files.writeString(file, "not-json")
        val original = Files.readAllBytes(file)
        val repository = environmentRepository(file)

        assertFailsWith<WorkbenchStorageCorruptionException> { repository.snapshot() }
        assertFailsWith<WorkbenchStorageCorruptionException> {
            repository.upsert(environment("new", "New", 1, "secret"))
        }
        assertContentEquals(original, Files.readAllBytes(file))

        repository.clear()
        assertTrue(repository.snapshot().environments.isEmpty())
    }

    @Test
    fun `unsupported and duplicate environment envelopes are corruption`() = runTest {
        val file = createTempDirectory("postsync-environment-invalid").resolve("environments.json")
        val repository = environmentRepository(file)
        repository.upsert(environment("one", "One", 1, "secret"))
        val valid = Json.parseToJsonElement(Files.readString(file)).jsonObject

        Files.writeString(file, JsonObject(valid + ("version" to JsonPrimitive(99))).toString())
        assertFailsWith<WorkbenchStorageCorruptionException> { repository.snapshot() }

        repository.clear()
        repository.upsert(environment("one", "One", 1, "secret"))
        val duplicateRoot = Json.parseToJsonElement(Files.readString(file)).jsonObject
        val storedEnvironment = duplicateRoot.getValue("environments").jsonArray.single()
        Files.writeString(
            file,
            JsonObject(
                duplicateRoot + ("environments" to JsonArray(listOf(storedEnvironment, storedEnvironment)))
            ).toString()
        )
        assertFailsWith<WorkbenchStorageCorruptionException> { repository.snapshot() }
    }

    @Test
    fun `environment count cap is non destructive`() = runTest {
        val file = createTempDirectory("postsync-environment-cap").resolve("environments.json")
        val repository = FileWorkbenchEnvironmentRepository(
            file = file,
            encryptValue = ::encodeSecret,
            decryptValue = ::decodeSecret,
            maxEnvironments = 1,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        repository.upsert(environment("one", "One", 1, "first-secret"))
        val before = Files.readAllBytes(file)

        assertFailsWith<IllegalArgumentException> {
            repository.upsert(environment("two", "Two", 2, "second-secret"))
        }

        assertContentEquals(before, Files.readAllBytes(file))
        assertEquals(listOf("one"), repository.snapshot().environments.map { it.id })
    }

    @Test
    fun `environment upsert and selection commit as one transaction`() = runTest {
        val file = createTempDirectory("postsync-environment-atomic-select").resolve("environments.json")
        val repository = environmentRepository(file)
        repository.upsertAndSelectEnvironment(environment("one", "One", 1, "first-secret"))

        val snapshot = repository.upsertAndSelectEnvironment(
            environment("two", "Two", 2, "second-secret")
        )

        assertEquals("two", snapshot.activeEnvironmentId)
        assertEquals(listOf("two", "one"), snapshot.environments.map { it.id })
        assertEquals(
            "two",
            environmentRepository(file).snapshot().activeEnvironmentId
        )
    }

    @Test
    fun `failed atomic environment upsert and selection preserves prior bytes`() = runTest {
        val file = createTempDirectory("postsync-environment-atomic-failure").resolve("environments.json")
        val repository = environmentRepository(file)
        repository.upsertAndSelectEnvironment(environment("one", "One", 1, "first-secret"))
        val before = Files.readAllBytes(file)
        val encryptionFailure = FileWorkbenchEnvironmentRepository(
            file = file,
            encryptValue = { error("encryption unavailable") },
            decryptValue = ::decodeSecret,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        assertFailsWith<IllegalStateException> {
            encryptionFailure.upsertAndSelectEnvironment(
                environment("two", "Two", 2, "second-secret")
            )
        }

        assertContentEquals(before, Files.readAllBytes(file))
        assertEquals("one", repository.snapshot().activeEnvironmentId)
        assertEquals(listOf("one"), repository.snapshot().environments.map { it.id })
    }

    @Test
    fun `environment repositories sharing a path serialize concurrent updates`() = runTest {
        val file = createTempDirectory("postsync-environment-concurrent").resolve("environments.json")
        val first = environmentRepository(file)
        val second = environmentRepository(file)

        coroutineScope {
            listOf(
                async { first.upsert(environment("one", "One", 1, "secret-one")) },
                async { second.upsert(environment("two", "Two", 2, "secret-two")) }
            ).awaitAll()
        }

        assertEquals(setOf("one", "two"), first.snapshot().environments.mapTo(linkedSetOf()) { it.id })
    }

    @Test
    fun `saved requests preserve hierarchy search and deterministic newest ordering`() = runTest {
        val file = createTempDirectory("postsync-saved-workspace").resolve("saved.json")
        val repository = savedRepository(file)
        repository.upsertCollection(collection("collection", "API", 1))
        repository.upsertFolder(folder("folder", "collection", null, "Users", 2))
        repository.upsertRequest(savedRequest("older", 3, name = "Duplicate", folderId = "folder"))
        val workspace = repository.upsertRequest(
            savedRequest("newer", 4, name = "Duplicate", folderId = "folder")
        )

        assertEquals(listOf("newer", "older"), workspace.requests.map { it.id })
        assertEquals(
            listOf("newer", "older"),
            repository.search("GET users", collectionId = "collection", folderId = "folder").map { it.id }
        )
        assertEquals("newer", repository.findRequest("newer")?.id)

        val afterDelete = repository.deleteFolder("folder")
        assertTrue(afterDelete.folders.isEmpty())
        assertTrue(afterDelete.requests.isEmpty())
        assertEquals(listOf("collection"), afterDelete.collections.map { it.id })
    }

    @Test
    fun `collection and first request commit as one transaction`() = runTest {
        val file = createTempDirectory("postsync-saved-atomic-create").resolve("saved.json")
        val repository = savedRepository(file)

        val workspace = repository.upsertCollectionWithRequest(
            collection("collection", "API", 1),
            savedRequest("request", 2)
        )

        assertEquals(listOf("collection"), workspace.collections.map { it.id })
        assertEquals(listOf("request"), workspace.requests.map { it.id })
        assertEquals(
            listOf("request"),
            savedRepository(file).snapshot().requests.map { it.id }
        )
    }

    @Test
    fun `failed collection and request transaction preserves prior bytes`() = runTest {
        val file = createTempDirectory("postsync-saved-atomic-failure").resolve("saved.json")
        val repository = savedRepository(file)
        repository.upsertCollectionWithRequest(
            collection("collection", "Original", 1),
            savedRequest("request", 2)
        )
        val beforeMismatch = Files.readAllBytes(file)

        assertFailsWith<IllegalArgumentException> {
            repository.upsertCollectionWithRequest(
                collection("collection", "Must not persist", 3),
                savedRequest("other", 3).copy(collectionId = "different")
            )
        }
        assertContentEquals(beforeMismatch, Files.readAllBytes(file))

        val byteLimited = FileWorkbenchSavedRequestRepository(
            file = file,
            storageLimitBytes = 4_096,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val error = assertFailsWith<IllegalArgumentException> {
            byteLimited.upsertCollectionWithRequest(
                collection("collection", "Also must not persist", 4),
                savedRequest("large", 4).copy(
                    draft = savedRequest("large", 4).draft.copy(
                        body = "x".repeat(8_000),
                        bodyEnabled = true
                    )
                )
            )
        }

        assertContains(error.message.orEmpty(), "storage limit")
        assertContentEquals(beforeMismatch, Files.readAllBytes(file))
        assertEquals("Original", repository.snapshot().collections.single().name)
        assertEquals(listOf("request"), repository.snapshot().requests.map { it.id })
    }

    @Test
    fun `atomic mutation waits for a cross process sibling file lock`() = runTest {
        val directory = createTempDirectory("postsync-saved-process-lock")
        val file = directory.resolve("saved.json")
        val lockFile = directory.resolve(".saved.json.lock")
        val repository = savedRepository(file)
        repository.upsertCollectionWithRequest(
            collection("collection", "Original", 1),
            savedRequest("request", 2)
        )
        val beforeCancellation = Files.readAllBytes(file)
        val javaExecutable = java.nio.file.Path.of(
            System.getProperty("java.home"),
            "bin",
            "java"
        ).toString()
        val helperClasses = WorkbenchFileLockHolder::class.java
            .protectionDomain.codeSource.location.toURI().path
        val holder = ProcessBuilder(
            javaExecutable,
            "-cp",
            helperClasses,
            WorkbenchFileLockHolder::class.java.name,
            lockFile.toString()
        ).redirectErrorStream(true).start()
        val output = holder.inputStream.bufferedReader()

        try {
            assertEquals("locked", output.readLine())
            val cancelledMutation = async(Dispatchers.Default) {
                repository.upsertCollectionWithRequest(
                    collection("collection", "Cancelled", 3),
                    savedRequest("cancelled-request", 3)
                )
            }

            withContext(Dispatchers.IO) { Thread.sleep(150) }
            assertFalse(cancelledMutation.isCompleted)
            cancelledMutation.cancelAndJoin()
            assertContentEquals(beforeCancellation, Files.readAllBytes(file))

            val committedMutation = async(Dispatchers.Default) {
                repository.upsertCollectionWithRequest(
                    collection("collection", "Committed", 4),
                    savedRequest("committed-request", 4)
                )
            }
            withContext(Dispatchers.IO) { Thread.sleep(150) }
            assertFalse(committedMutation.isCompleted)

            holder.outputStream.write(1)
            holder.outputStream.flush()
            val committed = committedMutation.await()
            assertEquals("Committed", committed.collections.single().name)
            assertEquals(
                setOf("request", "committed-request"),
                committed.requests.mapTo(linkedSetOf()) { it.id }
            )
            assertEquals(0, withContext(Dispatchers.IO) { holder.waitFor() })
        } finally {
            if (holder.isAlive) {
                runCatching {
                    holder.outputStream.write(1)
                    holder.outputStream.flush()
                }
                holder.destroyForcibly()
                withContext(Dispatchers.IO) { holder.waitFor() }
            }
        }
    }

    @Test
    fun `saved folders reject cross collection parents and cycles without mutation`() = runTest {
        val file = createTempDirectory("postsync-saved-folder-integrity").resolve("saved.json")
        val repository = savedRepository(file)
        repository.upsertCollection(collection("one", "One", 1))
        repository.upsertCollection(collection("two", "Two", 2))
        repository.upsertFolder(folder("parent", "one", null, "Parent", 3))
        val beforeCrossCollection = Files.readAllBytes(file)

        assertFailsWith<IllegalArgumentException> {
            repository.upsertFolder(folder("invalid", "two", "parent", "Invalid", 4))
        }
        assertContentEquals(beforeCrossCollection, Files.readAllBytes(file))

        repository.upsertFolder(folder("child", "one", "parent", "Child", 5))
        val beforeCycle = Files.readAllBytes(file)
        assertFailsWith<IllegalArgumentException> {
            repository.upsertFolder(folder("parent", "one", "child", "Parent", 6))
        }
        assertContentEquals(beforeCycle, Files.readAllBytes(file))
    }

    @Test
    fun `saved request redacts every secret and disables unsafe assertion comparisons`() = runTest {
        val file = createTempDirectory("postsync-saved-redaction").resolve("saved.json")
        val repository = savedRepository(file)
        val secret = "saved-request-super-secret"
        val assertionOnlySecret = "standalone-assertion-secret-734"
        repository.upsertCollection(collection("collection", "API", 1))
        val saved = repository.upsertRequest(
            WorkbenchSavedRequest(
                id = "request",
                collectionId = "collection",
                name = "Request $secret",
                description = "Description $secret",
                tags = setOf("safe", secret),
                draft = WorkbenchRequestDraft(
                    name = "Draft $secret",
                    method = WorkbenchHttpMethod.POST,
                    url = "https://user:$secret@example.test/users?access_token=$secret",
                    query = listOf(
                        WorkbenchRequestField("api_key", secret),
                        WorkbenchRequestField("echo", secret)
                    ),
                    headers = listOf(
                        WorkbenchRequestField("Authorization", "Bearer $secret"),
                        WorkbenchRequestField("X-Echo", secret)
                    ),
                    body = """{"password":"$secret","name":"safe"}""",
                    bodyEnabled = true,
                    auth = WorkbenchAuth.Bearer(secret)
                ),
                variables = mapOf("token" to WorkbenchVariable(secret, secret = true)),
                assertions = listOf(
                    WorkbenchAssertion.HeaderEquals(
                        id = "auth",
                        name = "Authorization",
                        expected = "Bearer $secret"
                    ),
                    WorkbenchAssertion.JsonPathEquals(
                        id = "json",
                        path = "$.password",
                        expectedJson = "\"$secret\""
                    ),
                    WorkbenchAssertion.BodyContains(id = "body", expected = secret),
                    WorkbenchAssertion.HeaderEquals(
                        id = "safe",
                        name = "X-State",
                        expected = "ready"
                    ),
                    WorkbenchAssertion.BodyContains(
                        id = "sensitive-body",
                        expected = assertionOnlySecret,
                        sensitive = true
                    )
                ),
                createdAtEpochMillis = 2,
                updatedAtEpochMillis = 2
            )
        ).requests.single()

        assertFalse(secret in Files.readString(file))
        assertFalse(assertionOnlySecret in Files.readString(file))
        assertFalse(secret in saved.toString())
        assertEquals("", (saved.draft.auth as WorkbenchAuth.Bearer).token)
        assertEquals("", saved.variables.getValue("token").value)
        assertTrue("<redacted>" in saved.draft.body)
        assertEquals(
            setOf(
                WorkbenchSecretLocation.AUTH,
                WorkbenchSecretLocation.VARIABLE,
                WorkbenchSecretLocation.HEADER,
                WorkbenchSecretLocation.QUERY,
                WorkbenchSecretLocation.URL,
                WorkbenchSecretLocation.BODY,
                WorkbenchSecretLocation.ASSERTION
            ),
            saved.requiredSecrets.mapTo(linkedSetOf()) { it.location }
        )
        assertTrue(saved.assertions[0].enabled)
        assertTrue(saved.assertions[1].enabled)
        assertTrue(saved.assertions[2].enabled)
        assertTrue(saved.assertions[3].enabled)
        assertTrue(saved.assertions[4].enabled)
        assertTrue(assertIs<WorkbenchAssertion.HeaderEquals>(saved.assertions[0]).sensitive)
        assertEquals("", assertIs<WorkbenchAssertion.HeaderEquals>(saved.assertions[0]).expected)
        assertTrue(assertIs<WorkbenchAssertion.JsonPathEquals>(saved.assertions[1]).sensitive)
        assertEquals("", assertIs<WorkbenchAssertion.JsonPathEquals>(saved.assertions[1]).expectedJson)
        assertEquals("ready", assertIs<WorkbenchAssertion.HeaderEquals>(saved.assertions[3]).expected)
        assertTrue(assertIs<WorkbenchAssertion.BodyContains>(saved.assertions[4]).sensitive)
        assertEquals("", assertIs<WorkbenchAssertion.BodyContains>(saved.assertions[4]).expected)
    }

    @Test
    fun `body classified secret is removed from otherwise normal saved fields`() = runTest {
        val file = createTempDirectory("postsync-saved-body-secret").resolve("saved.json")
        val repository = savedRepository(file)
        val secret = "body-only-classified-secret"
        repository.upsertCollection(collection("collection", "API", 1))
        val saved = repository.upsertRequest(
            WorkbenchSavedRequest(
                id = "request",
                collectionId = "collection",
                name = "Body secret",
                draft = WorkbenchRequestDraft(
                    method = WorkbenchHttpMethod.POST,
                    url = "https://example.test/users",
                    query = listOf(WorkbenchRequestField("echo", secret)),
                    body = """{"client_secret":{"nested":"$secret"}}""",
                    bodyEnabled = true
                ),
                variables = mapOf("mirror" to WorkbenchVariable(secret)),
                assertions = listOf(WorkbenchAssertion.BodyContains(expected = secret)),
                createdAtEpochMillis = 2,
                updatedAtEpochMillis = 2
            )
        ).requests.single()

        assertFalse(secret in Files.readString(file))
        assertEquals("<redacted>", saved.draft.query.single().value)
        assertEquals("<redacted>", saved.variables.getValue("mirror").value)
        assertTrue(saved.assertions.single().enabled)
        assertTrue(assertIs<WorkbenchAssertion.BodyContains>(saved.assertions.single()).sensitive)
    }

    @Test
    fun `sanitized credential assertions expose restoration metadata without plaintext`() = runTest {
        val file = createTempDirectory("postsync-saved-assertion-restoration").resolve("saved.json")
        val repository = savedRepository(file)
        val requestSecret = "request-auth-secret-734"
        val headerSecret = "standalone-header-secret-734"
        val jsonSecret = "standalone-json-secret-734"
        repository.upsertCollection(collection("collection", "API", 1))

        val saved = repository.upsertRequest(
            savedRequest("request", 2).copy(
                draft = savedRequest("request", 2).draft.copy(
                    auth = WorkbenchAuth.Bearer(requestSecret)
                ),
                assertions = listOf(
                    WorkbenchAssertion.BodyContains(expected = requestSecret),
                    WorkbenchAssertion.HeaderEquals(
                        name = "Authorization",
                        expected = "Bearer $headerSecret"
                    ),
                    WorkbenchAssertion.JsonPathEquals(
                        path = "$.client_secret",
                        expectedJson = "\"$jsonSecret\""
                    )
                )
            )
        ).requests.single()

        val disk = Files.readString(file)
        assertFalse(requestSecret in disk)
        assertFalse(headerSecret in disk)
        assertFalse(jsonSecret in disk)
        assertEquals(
            setOf("assertion:0", "assertion:1", "assertion:2"),
            saved.requiredSecrets
                .filter { it.location == WorkbenchSecretLocation.ASSERTION }
                .mapTo(linkedSetOf()) { it.id }
        )
        assertTrue(saved.assertions.all { it.enabled })
        assertTrue(saved.assertions.all {
            when (it) {
                is WorkbenchAssertion.BodyContains -> it.sensitive && it.expected.isEmpty()
                is WorkbenchAssertion.HeaderEquals -> it.sensitive && it.expected.isEmpty()
                is WorkbenchAssertion.JsonPathEquals -> it.sensitive && it.expectedJson.isEmpty()
                else -> false
            }
        })
    }

    @Test
    fun `body beyond complete redaction limit is rejected without changing store`() = runTest {
        val file = createTempDirectory("postsync-saved-body-redaction-cap").resolve("saved.json")
        val repository = savedRepository(file)
        repository.upsertCollectionWithRequest(
            collection("collection", "Original", 1),
            savedRequest("request", 2)
        )
        val before = Files.readAllBytes(file)

        val error = assertFailsWith<IllegalArgumentException> {
            repository.upsertCollectionWithRequest(
                collection("collection", "Must not persist", 3),
                savedRequest("oversized", 3).copy(
                    draft = savedRequest("oversized", 3).draft.copy(
                        body = "x".repeat(2 * 1024 * 1024 + 1),
                        bodyEnabled = true
                    )
                )
            )
        }

        assertContains(error.message.orEmpty(), "body exceeds")
        assertContentEquals(before, Files.readAllBytes(file))
        assertEquals("Original", repository.snapshot().collections.single().name)
    }

    @Test
    fun `JSON formatting normalization does not create a false body secret marker`() = runTest {
        val file = createTempDirectory("postsync-saved-safe-json").resolve("saved.json")
        val repository = savedRepository(file)
        repository.upsertCollection(collection("collection", "API", 1))

        val saved = repository.upsertRequest(
            savedRequest("request", 2).copy(
                draft = savedRequest("request", 2).draft.copy(
                    method = WorkbenchHttpMethod.POST,
                    body = "{\n  \"name\": \"safe\"\n}",
                    bodyEnabled = true
                )
            )
        ).requests.single()

        assertFalse(saved.requiredSecrets.any { it.location == WorkbenchSecretLocation.BODY })
        assertEquals("{\n  \"name\": \"safe\"\n}", saved.draft.body)
    }

    @Test
    fun `saved request corruption blocks overwrite and clear is explicit recovery`() = runTest {
        val file = createTempDirectory("postsync-saved-corrupt").resolve("saved.json")
        Files.writeString(file, "{broken")
        val before = Files.readAllBytes(file)
        val repository = savedRepository(file)

        assertFailsWith<WorkbenchStorageCorruptionException> { repository.snapshot() }
        assertFailsWith<WorkbenchStorageCorruptionException> {
            repository.upsertCollection(collection("new", "New", 1))
        }
        assertContentEquals(before, Files.readAllBytes(file))

        repository.clear()
        assertTrue(repository.snapshot().collections.isEmpty())
    }

    @Test
    fun `saved request caps and byte limit fail atomically`() = runTest {
        val directory = createTempDirectory("postsync-saved-cap")
        val file = directory.resolve("saved.json")
        val repository = FileWorkbenchSavedRequestRepository(
            file = file,
            maxRequests = 1,
            storageLimitBytes = 4_096,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        repository.upsertCollection(collection("collection", "API", 1))
        repository.upsertRequest(savedRequest("existing", 2))
        val beforeCountFailure = Files.readAllBytes(file)

        assertFailsWith<IllegalArgumentException> {
            repository.upsertRequest(savedRequest("second", 3))
        }
        assertContentEquals(beforeCountFailure, Files.readAllBytes(file))

        val byteLimited = FileWorkbenchSavedRequestRepository(
            file = file,
            maxRequests = 10,
            storageLimitBytes = 4_096,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val beforeSizeFailure = Files.readAllBytes(file)
        val error = assertFailsWith<IllegalArgumentException> {
            byteLimited.upsertRequest(
                savedRequest("large", 4).copy(
                    draft = savedRequest("large", 4).draft.copy(
                        body = "x".repeat(8_000),
                        bodyEnabled = true
                    )
                )
            )
        }
        assertContains(error.message.orEmpty(), "storage limit")
        assertContentEquals(beforeSizeFailure, Files.readAllBytes(file))
        assertFalse(Files.list(directory).use { paths ->
            paths.anyMatch { it.fileName.toString().contains(".tmp-") }
        })
    }

    @Test
    fun `corrupt environment recovery preserves oversized bytes and resets to empty`() = runTest {
        val directory = createTempDirectory("postsync-environment-corruption-backup")
        val file = directory.resolve("environments.json")
        val corruptBytes = ByteArray(2_048) { index -> (index % 251).toByte() }
        Files.write(file, corruptBytes)
        val repository = FileWorkbenchEnvironmentRepository(
            file = file,
            encryptValue = ::encodeSecret,
            decryptValue = ::decodeSecret,
            storageLimitBytes = 256,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        assertFailsWith<WorkbenchStorageCorruptionException> { repository.snapshot() }
        val backup = Path.of(requireNotNull(repository.backupCorruptStoreAndReset()))

        assertEquals(directory, backup.parent)
        assertTrue(backup.fileName.toString().matches(Regex("\\.corrupt-\\d+-[a-f0-9-]+\\.bak")))
        assertContentEquals(corruptBytes, Files.readAllBytes(backup))
        assertTrue(repository.snapshot().environments.isEmpty())
        assertTrue(Files.readString(file).contains("\"version\":1"))
    }

    @Test
    fun `corrupt saved workspace recovery preserves bytes and resets to empty`() = runTest {
        val directory = createTempDirectory("postsync-saved-corruption-backup")
        val file = directory.resolve("saved.json")
        val corruptBytes = "{".repeat(2_048).toByteArray(StandardCharsets.UTF_8)
        Files.write(file, corruptBytes)
        val repository = FileWorkbenchSavedRequestRepository(
            file = file,
            storageLimitBytes = 512,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        assertFailsWith<WorkbenchStorageCorruptionException> { repository.snapshot() }
        val backup = Path.of(requireNotNull(repository.backupCorruptStoreAndReset()))
        val reloaded = repository.snapshot()

        assertContentEquals(corruptBytes, Files.readAllBytes(backup))
        assertTrue(reloaded.collections.isEmpty())
        assertTrue(reloaded.folders.isEmpty())
        assertTrue(reloaded.requests.isEmpty())
    }

    @Test
    fun `recovery reset failure restores original or retains recoverable backup`() = runTest {
        val directory = createTempDirectory("postsync-corruption-reset-failure")
        val originalFile = directory.resolve("original.json")
        val originalBytes = "original-corrupt-bytes".toByteArray(StandardCharsets.UTF_8)
        Files.write(originalFile, originalBytes)
        val originalStore = SecureAtomicWorkbenchFile(originalFile, 1_024, "Recovery test")
        val restoredBackup = directory.resolve(".corrupt-123-fixed-id.bak")

        assertFailsWith<IOException> {
            originalStore.backupCorruptStoreAndReset(
                resetBytes = "{}".toByteArray(StandardCharsets.UTF_8),
                timestampEpochMillis = 123,
                backupId = "fixed-id",
                resetWriter = { throw IOException("reset failed") }
            )
        }
        assertContentEquals(originalBytes, Files.readAllBytes(originalFile))
        assertFalse(Files.exists(restoredBackup, java.nio.file.LinkOption.NOFOLLOW_LINKS))

        val retainedFile = directory.resolve("retained.json")
        val retainedBytes = "second-corrupt-store".toByteArray(StandardCharsets.UTF_8)
        Files.write(retainedFile, retainedBytes)
        val retainedStore = SecureAtomicWorkbenchFile(retainedFile, 1_024, "Recovery test")
        val retainedBackup = directory.resolve(".corrupt-456-retained-id.bak")
        val error = assertFailsWith<IOException> {
            retainedStore.backupCorruptStoreAndReset(
                resetBytes = "{}".toByteArray(StandardCharsets.UTF_8),
                timestampEpochMillis = 456,
                backupId = "retained-id",
                resetWriter = {
                    Files.createDirectory(retainedFile)
                    throw IOException("reset failed after target appeared")
                }
            )
        }

        assertTrue(error.suppressed.isNotEmpty())
        assertTrue(Files.isDirectory(retainedFile))
        assertContentEquals(retainedBytes, Files.readAllBytes(retainedBackup))
    }

    @Test
    fun `recovery never overwrites a sibling backup symlink`() = runTest {
        val directory = createTempDirectory("postsync-corruption-backup-symlink")
        val file = directory.resolve("store.json")
        val originalBytes = "corrupt-store".toByteArray(StandardCharsets.UTF_8)
        val sentinel = directory.resolve("sentinel")
        val backup = directory.resolve(".corrupt-789-collision-id.bak")
        Files.write(file, originalBytes)
        Files.writeString(sentinel, "sentinel")
        if (runCatching { Files.createSymbolicLink(backup, sentinel) }.isFailure) return@runTest
        val store = SecureAtomicWorkbenchFile(file, 1_024, "Recovery test")

        assertFailsWith<IllegalArgumentException> {
            store.backupCorruptStoreAndReset(
                resetBytes = "{}".toByteArray(StandardCharsets.UTF_8),
                timestampEpochMillis = 789,
                backupId = "collision-id",
                resetWriter = store::write
            )
        }

        assertContentEquals(originalBytes, Files.readAllBytes(file))
        assertTrue(Files.isSymbolicLink(backup))
        assertEquals("sentinel", Files.readString(sentinel))
    }

    @Test
    fun `storage rejects a symbolic link target`() = runTest {
        val directory = createTempDirectory("postsync-storage-symlink")
        val target = directory.resolve("target.json")
        val link = directory.resolve("environments.json")
        Files.writeString(target, "sentinel")
        if (runCatching { Files.createSymbolicLink(link, target) }.isFailure) return@runTest
        val repository = environmentRepository(link)

        assertFailsWith<IllegalArgumentException> { repository.snapshot() }
        assertFailsWith<IllegalArgumentException> { repository.clear() }
        assertEquals("sentinel", Files.readString(target))
    }

    @Test
    fun `atomic mutation rejects a symbolic link sibling lock`() = runTest {
        val directory = createTempDirectory("postsync-storage-lock-symlink")
        val target = directory.resolve("lock-target")
        val file = directory.resolve("environments.json")
        val lock = directory.resolve(".environments.json.lock")
        Files.writeString(target, "sentinel")
        if (runCatching { Files.createSymbolicLink(lock, target) }.isFailure) return@runTest

        assertFailsWith<IllegalArgumentException> {
            environmentRepository(file).upsertAndSelectEnvironment(
                environment("environment", "Environment", 1, "secret")
            )
        }

        assertFalse(Files.exists(file))
        assertEquals("sentinel", Files.readString(target))
    }

    private fun environment(id: String, name: String, updated: Long, secret: String) =
        WorkbenchEnvironment(
            id = id,
            name = name,
            variables = mapOf(
                "baseUrl" to WorkbenchVariable("https://example.test"),
                // Sensitive names are upgraded to secret even if a caller forgets the flag.
                "apiToken" to WorkbenchVariable(secret, secret = false)
            ),
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = updated
        )

    private fun collection(id: String, name: String, updated: Long) = WorkbenchSavedCollection(
        id = id,
        name = name,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = updated
    )

    private fun folder(
        id: String,
        collectionId: String,
        parentId: String?,
        name: String,
        updated: Long
    ) = WorkbenchSavedFolder(
        id = id,
        collectionId = collectionId,
        parentFolderId = parentId,
        name = name,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = updated
    )

    private fun savedRequest(
        id: String,
        updated: Long,
        name: String = "Users request",
        folderId: String? = null
    ) = WorkbenchSavedRequest(
        id = id,
        collectionId = "collection",
        folderId = folderId,
        name = name,
        tags = setOf("users", "smoke"),
        draft = WorkbenchRequestDraft(
            method = WorkbenchHttpMethod.GET,
            url = "https://example.test/users"
        ),
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = updated
    )

    private fun environmentRepository(path: java.nio.file.Path) =
        FileWorkbenchEnvironmentRepository(
            file = path,
            encryptValue = ::encodeSecret,
            decryptValue = ::decodeSecret
        )

    private fun savedRepository(path: java.nio.file.Path) =
        FileWorkbenchSavedRequestRepository(file = path)

    private fun encryptedEnvironmentValue(path: java.nio.file.Path, key: String): String {
        val root = Json.parseToJsonElement(Files.readString(path)).jsonObject
        return root.getValue("environments").jsonArray.first().jsonObject
            .getValue("variables").jsonObject.getValue(key).jsonObject
            .getValue("encryptedValue").jsonPrimitive.content
    }

    private fun encodeSecret(value: String): String = Base64.getEncoder()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeSecret(value: String): String = String(
        Base64.getDecoder().decode(value),
        StandardCharsets.UTF_8
    )
}
