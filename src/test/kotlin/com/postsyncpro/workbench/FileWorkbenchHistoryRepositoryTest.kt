package com.postsyncpro.workbench

import com.postsyncpro.data.repository.FileWorkbenchHistoryRepository
import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchHistoryEntry
import com.postsyncpro.domain.model.WorkbenchRequestDraft
import com.postsyncpro.domain.model.WorkbenchVariable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileWorkbenchHistoryRepositoryTest {
    @Test
    fun `persists newest-first capped history atomically without secrets`() = runTest {
        val directory = createTempDirectory("postsync-history-test")
        val file = directory.resolve("history.json")
        val repository = FileWorkbenchHistoryRepository(
            file = file,
            capacity = 2,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val secret = "never-write-this-secret"

        repository.save(entry("one", 1, secret))
        repository.save(entry("two", 2, secret))
        val saved = repository.save(entry("three", 3, secret))

        assertEquals(listOf("three", "two"), saved.map { it.id })
        val disk = Files.readString(file)
        assertFalse(secret in disk)
        assertTrue("<redacted>" in disk)
        assertFalse(Files.list(directory).use { paths ->
            paths.anyMatch { it.fileName.toString().contains(".tmp-") }
        })

        val reloaded = FileWorkbenchHistoryRepository(
            file = file,
            capacity = 2,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        ).list()
        assertEquals(listOf("three", "two"), reloaded.map { it.id })
        assertEquals("", (reloaded.first().request.auth as WorkbenchAuth.Bearer).token)
        assertEquals("", reloaded.first().variables.getValue("token").value)
    }

    @Test
    fun `corrupt or oversized-looking content fails closed as empty history`() = runTest {
        val file = createTempDirectory("postsync-history-corrupt").resolve("history.json")
        Files.writeString(file, "not-json")
        val repository = FileWorkbenchHistoryRepository(
            file,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun `evicts oldest entries until serialized history fits the byte limit`() = runTest {
        val file = createTempDirectory("postsync-history-size-cap").resolve("history.json")
        val storageLimit = 4_096L
        val repository = FileWorkbenchHistoryRepository(
            file = file,
            capacity = 10,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            storageLimitBytes = storageLimit
        )

        repository.save(largeEntry("oldest", 1, 1_300))
        repository.save(largeEntry("middle", 2, 1_300))
        val saved = repository.save(largeEntry("newest", 3, 1_300))

        assertEquals("newest", saved.first().id)
        assertFalse(saved.any { it.id == "oldest" })
        assertTrue(saved.zipWithNext().all { (newer, older) ->
            newer.createdAtEpochMillis >= older.createdAtEpochMillis
        })
        assertTrue(Files.size(file) <= storageLimit)
        assertEquals(saved.map { it.id }, repository.list().map { it.id })
    }

    @Test
    fun `oversized single entry fails without replacing existing history`() = runTest {
        val directory = createTempDirectory("postsync-history-single-oversize")
        val file = directory.resolve("history.json")
        val repository = FileWorkbenchHistoryRepository(
            file = file,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            storageLimitBytes = 4_096L
        )
        repository.save(entry("existing", 1, "stored-secret"))
        val originalBytes = Files.readAllBytes(file)

        val error = assertFailsWith<IllegalArgumentException> {
            repository.save(largeEntry("too-large", 2, 8_000))
        }

        assertContains(error.message.orEmpty(), "too-large")
        assertContains(error.message.orEmpty(), "exceeds the storage limit")
        assertContentEquals(originalBytes, Files.readAllBytes(file))
        assertEquals(listOf("existing"), repository.list().map { it.id })
        assertFalse(Files.list(directory).use { paths ->
            paths.anyMatch { it.fileName.toString().contains(".tmp-") }
        })
    }

    private fun entry(id: String, created: Long, secret: String) = WorkbenchHistoryEntry(
        id = id,
        createdAtEpochMillis = created,
        request = WorkbenchRequestDraft(
            url = "https://example.test?token=$secret",
            body = """{"password":"$secret"}""",
            bodyEnabled = true,
            auth = WorkbenchAuth.Bearer(secret)
        ),
        variables = mapOf("token" to WorkbenchVariable(secret, secret = true))
    )

    private fun largeEntry(id: String, created: Long, bodySize: Int) = WorkbenchHistoryEntry(
        id = id,
        createdAtEpochMillis = created,
        request = WorkbenchRequestDraft(
            url = "https://example.test/$id",
            body = "x".repeat(bodySize),
            bodyEnabled = true
        )
    )
}
