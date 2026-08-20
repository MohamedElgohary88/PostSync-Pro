package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.WorkbenchHistoryEntry
import com.postsyncpro.domain.repository.WorkbenchHistoryRepository
import com.postsyncpro.domain.usecase.SecretRedactor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.util.UUID

class FileWorkbenchHistoryRepository(
    private val file: Path,
    capacity: Int = DEFAULT_CAPACITY,
    private val redactor: SecretRedactor = SecretRedactor(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val storageLimitBytes: Long = MAX_FILE_BYTES
) : WorkbenchHistoryRepository {
    private val capacity = capacity.coerceIn(1, MAX_CAPACITY)
    private val mutex = workbenchStorageMutex(file)
    private val processLock = WorkbenchStorageProcessLock(file, "Workbench history")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "authType"
    }

    init {
        require(storageLimitBytes in MIN_FILE_BYTES..MAX_FILE_BYTES) {
            "History storage limit must be between $MIN_FILE_BYTES and $MAX_FILE_BYTES bytes"
        }
    }

    override suspend fun list(): List<WorkbenchHistoryEntry> =
        withWorkbenchStorageMutationLock(mutex, processLock, ioDispatcher, ::loadFromDisk)

    override suspend fun find(id: String): WorkbenchHistoryEntry? =
        withWorkbenchStorageMutationLock(mutex, processLock, ioDispatcher) {
            loadFromDisk().firstOrNull { it.id == id }
        }

    override suspend fun save(entry: WorkbenchHistoryEntry): List<WorkbenchHistoryEntry> =
        withWorkbenchStorageMutationLock(mutex, processLock, ioDispatcher) {
            val sanitized = redactor.sanitizeHistoryEntry(entry)
            requireSingleEntryFits(sanitized)
            val updated = (listOf(sanitized) + loadFromDisk().filterNot { it.id == sanitized.id })
                .sortedByDescending { it.createdAtEpochMillis }
                .take(capacity)
            val storage = fitToStorageLimit(updated)
            writeAtomically(storage.bytes)
            storage.entries
        }

    override suspend fun clear() {
        withWorkbenchStorageMutationLock(mutex, processLock, ioDispatcher) {
            writeAtomically(serialize(emptyList()))
        }
    }

    private fun loadFromDisk(): List<WorkbenchHistoryEntry> {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        require(!Files.isSymbolicLink(file)) { "History file cannot be a symbolic link" }
        if (Files.size(file) > storageLimitBytes) return emptyList()

        val content = Files.readString(file, StandardCharsets.UTF_8)
        if (content.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<WorkbenchHistoryEntry>>(content) }
            .getOrDefault(emptyList())
            .map(redactor::sanitizeHistoryEntry)
            .sortedByDescending { it.createdAtEpochMillis }
            .take(capacity)
    }

    private fun requireSingleEntryFits(entry: WorkbenchHistoryEntry) {
        val bytes = serialize(listOf(entry))
        require(bytes.size.toLong() <= storageLimitBytes) {
            "History entry '${entry.id}' exceeds the storage limit " +
                "(${bytes.size} bytes > $storageLimitBytes bytes)"
        }
    }

    /**
     * Entries arrive newest-first. A prefix therefore represents repeatedly
     * evicting the oldest entry. Binary search avoids serializing a near-limit
     * history once per eviction when capacity is large.
     */
    private fun fitToStorageLimit(entries: List<WorkbenchHistoryEntry>): EncodedHistory {
        val completeBytes = serialize(entries)
        if (completeBytes.size.toLong() <= storageLimitBytes) {
            return EncodedHistory(entries, completeBytes)
        }

        require(entries.isNotEmpty()) { "Empty history exceeds the storage limit" }
        var bestCount = 0
        var bestBytes = ByteArray(0)
        var lower = 1
        var upper = entries.lastIndex

        while (lower <= upper) {
            val candidateCount = lower + (upper - lower) / 2
            val candidateBytes = serialize(entries.take(candidateCount))
            if (candidateBytes.size.toLong() <= storageLimitBytes) {
                bestCount = candidateCount
                bestBytes = candidateBytes
                lower = candidateCount + 1
            } else {
                upper = candidateCount - 1
            }
        }

        require(bestCount > 0) { "Newest history entry exceeds the storage limit" }
        return EncodedHistory(entries.take(bestCount), bestBytes)
    }

    private fun serialize(entries: List<WorkbenchHistoryEntry>): ByteArray =
        json.encodeToString(entries).toByteArray(StandardCharsets.UTF_8)

    private fun writeAtomically(bytes: ByteArray) {
        val absolute = file.toAbsolutePath().normalize()
        val parent = absolute.parent ?: error("History path must have a parent directory")
        Files.createDirectories(parent)
        require(!Files.isSymbolicLink(parent)) { "History directory cannot be a symbolic link" }
        require(!Files.exists(absolute, LinkOption.NOFOLLOW_LINKS) || !Files.isSymbolicLink(absolute)) {
            "History file cannot be a symbolic link"
        }

        require(bytes.size.toLong() <= storageLimitBytes) { "History exceeds the storage limit" }
        val temporary = parent.resolve(".${absolute.fileName}.tmp-${UUID.randomUUID()}")

        try {
            FileChannel.open(
                temporary,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            ).use { channel ->
                var buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            restrictPermissions(temporary)

            try {
                Files.move(
                    temporary,
                    absolute,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (unsupported: AtomicMoveNotSupportedException) {
                throw IllegalStateException(
                    "Workbench history requires atomic file replacement",
                    unsupported
                )
            }
            restrictPermissions(absolute)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun restrictPermissions(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            )
        }
    }

    private data class EncodedHistory(
        val entries: List<WorkbenchHistoryEntry>,
        val bytes: ByteArray
    )

    private companion object {
        const val DEFAULT_CAPACITY = 100
        const val MAX_CAPACITY = 1_000
        const val MIN_FILE_BYTES = 2L
        const val MAX_FILE_BYTES = 32L * 1024L * 1024L
    }
}
