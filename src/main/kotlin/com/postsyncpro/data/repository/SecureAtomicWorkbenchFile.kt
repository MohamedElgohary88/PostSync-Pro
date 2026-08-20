package com.postsyncpro.data.repository

import com.postsyncpro.domain.repository.WorkbenchStorageCorruptionException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.FileChannel
import java.nio.channels.FileLockInterruptionException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val workbenchStorageLocks = ConcurrentHashMap<String, Mutex>()

internal fun workbenchStorageMutex(path: Path): Mutex = workbenchStorageLocks.computeIfAbsent(
    path.toAbsolutePath().normalize().toString()
) { Mutex() }

/**
 * Runs one cancellable cross-process read-modify-write transaction. A lock
 * interruption caused by coroutine cancellation is normalized back to
 * CancellationException instead of leaking a channel-specific exception.
 */
internal suspend fun <T> withWorkbenchStorageMutationLock(
    mutex: Mutex,
    processLock: WorkbenchStorageProcessLock,
    dispatcher: CoroutineDispatcher,
    block: () -> T
): T {
    mutex.lock()
    return try {
        try {
            runInterruptible(dispatcher) { processLock.withExclusiveLock(block) }
        } catch (error: FileLockInterruptionException) {
            currentCoroutineContext().ensureActive()
            throw error
        } catch (error: ClosedByInterruptException) {
            currentCoroutineContext().ensureActive()
            throw error
        }
    } finally {
        mutex.unlock()
    }
}

/**
 * Cross-process companion to [workbenchStorageMutex]. The stable sibling lock
 * file lets a repository hold one OS lock across its complete read-modify-write
 * transaction, including validation and the final atomic rename.
 *
 * This helper is intentionally independent of the JSON file implementation so
 * history and future Workbench stores can share the same transaction boundary.
 */
internal class WorkbenchStorageProcessLock(
    file: Path,
    private val label: String
) {
    private val file = file.toAbsolutePath().normalize()
    private val parent = requireNotNull(file.parent) { "$label path must have a parent directory" }
    internal val path: Path = parent.resolve(".${file.fileName}.lock")

    fun <T> withExclusiveLock(block: () -> T): T {
        ensurePrivateDirectory(parent)
        require(!Files.isSymbolicLink(path)) { "$label lock file cannot be a symbolic link" }
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                "$label lock path is not a regular file"
            }
        }

        val channel = FileChannel.open(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS
        )
        try {
            secureFile(path)
            val lock = channel.lock()
            try {
                return block()
            } finally {
                // Lock/channel cleanup must not turn an already committed
                // atomic rename into a caller-visible transaction failure.
                runCatching { lock.release() }
            }
        } finally {
            runCatching { channel.close() }
        }
    }

    private fun ensurePrivateDirectory(directory: Path) {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(directory)) { "$label directory cannot be a symbolic link" }
            require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                "$label parent is not a directory"
            }
        } else {
            Files.createDirectories(directory)
        }
        try {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        } catch (_: UnsupportedOperationException) {
            val asFile = directory.toFile()
            asFile.setReadable(false, false)
            asFile.setReadable(true, true)
            asFile.setWritable(false, false)
            asFile.setWritable(true, true)
            asFile.setExecutable(false, false)
            asFile.setExecutable(true, true)
        }
    }

    private fun secureFile(path: Path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
            val asFile = path.toFile()
            asFile.setReadable(false, false)
            asFile.setReadable(true, true)
            asFile.setWritable(false, false)
            asFile.setWritable(true, true)
            asFile.setExecutable(false, false)
        }
    }
}

/** Small, repository-local secure storage boundary for Workbench JSON stores. */
internal class SecureAtomicWorkbenchFile(
    file: Path,
    private val storageLimitBytes: Long,
    private val label: String
) {
    private val file = file.toAbsolutePath().normalize()

    init {
        require(storageLimitBytes > 0) { "$label storage limit must be positive" }
        require(file.parent != null) { "$label path must have a parent directory" }
    }

    /** Null means the store does not exist; malformed stores raise a corruption error. */
    fun readBytesOrNull(): ByteArray? {
        if (Files.exists(file.parent, LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(file.parent)) { "$label directory cannot be a symbolic link" }
            require(Files.isDirectory(file.parent, LinkOption.NOFOLLOW_LINKS)) {
                "$label parent is not a directory"
            }
        }
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null
        rejectTargetSymlink()
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "$label path is not a regular file"
        }
        val declaredSize = Files.size(file)
        if (declaredSize !in 1..storageLimitBytes) {
            throw WorkbenchStorageCorruptionException(
                "$label file has an invalid size ($declaredSize bytes)"
            )
        }
        val bytes = Files.readAllBytes(file)
        if (bytes.isEmpty() || bytes.size.toLong() > storageLimitBytes) {
            throw WorkbenchStorageCorruptionException("$label file changed to an invalid size while reading")
        }
        return bytes
    }

    fun write(bytes: ByteArray) {
        require(bytes.size.toLong() <= storageLimitBytes) {
            "$label exceeds the storage limit (${bytes.size} bytes > $storageLimitBytes bytes)"
        }
        val parent = file.parent
        ensurePrivateDirectory(parent)
        rejectTargetSymlink()

        val temporary = parent.resolve(".${file.fileName}.tmp-${UUID.randomUUID()}")
        try {
            FileChannel.open(
                temporary,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            ).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            secureFile(temporary)

            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (error: AtomicMoveNotSupportedException) {
                throw IllegalStateException(
                    "$label filesystem does not support atomic replacement",
                    error
                )
            }
            runCatching {
                FileChannel.open(parent, StandardOpenOption.READ).use { it.force(true) }
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /**
     * Recovery deliberately never reads or sizes [file]. The existing inode is
     * renamed to a unique, non-overwriting sibling before [resetBytes] are
     * written. If the reset fails before commit, restoring the original name is
     * attempted; if that is unsafe, the backup is retained for manual recovery.
     */
    fun backupCorruptStoreAndReset(resetBytes: ByteArray): Path? =
        backupCorruptStoreAndReset(
            resetBytes = resetBytes,
            timestampEpochMillis = System.currentTimeMillis(),
            backupId = UUID.randomUUID().toString(),
            resetWriter = ::write
        )

    /** Deterministic overload used by focused recovery tests. */
    fun backupCorruptStoreAndReset(
        resetBytes: ByteArray,
        timestampEpochMillis: Long,
        backupId: String,
        resetWriter: (ByteArray) -> Unit
    ): Path? {
        require(resetBytes.isNotEmpty()) { "$label reset envelope cannot be empty" }
        require(resetBytes.size.toLong() <= storageLimitBytes) {
            "$label reset envelope exceeds the storage limit"
        }
        require(timestampEpochMillis >= 0) { "Corruption backup timestamp cannot be negative" }
        require(backupId.matches(SAFE_BACKUP_ID)) { "Corruption backup ID is invalid" }

        val parent = requireNotNull(file.parent) { "$label path must have a parent directory" }
        ensurePrivateDirectory(parent)
        rejectTargetSymlink()
        val sourceExists = Files.exists(file, LinkOption.NOFOLLOW_LINKS)
        if (sourceExists) {
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                "$label path is not a regular file"
            }
        }
        val candidate = if (sourceExists) {
            parent.resolve(".corrupt-$timestampEpochMillis-$backupId.bak")
        } else null
        var movedBackup: Path? = null

        try {
            if (candidate != null) {
                require(!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    "$label corruption backup already exists"
                }
                require(!Files.isSymbolicLink(candidate)) {
                    "$label corruption backup cannot be a symbolic link"
                }
                // No REPLACE_EXISTING: a racing file or symlink always wins by
                // making recovery fail closed, never by being overwritten.
                Files.move(file, candidate)
                movedBackup = candidate
                secureFile(candidate)
                forceDirectory(parent)
            }

            resetWriter(resetBytes)
            return movedBackup
        } catch (resetFailure: Throwable) {
            movedBackup?.let { backup ->
                val restoreFailure = runCatching {
                    restoreBackupWithoutOverwrite(backup, parent)
                }.exceptionOrNull()
                if (restoreFailure != null && restoreFailure !== resetFailure) {
                    resetFailure.addSuppressed(restoreFailure)
                }
            }
            throw resetFailure
        }
    }

    private fun restoreBackupWithoutOverwrite(backup: Path, parent: Path) {
        require(!Files.isSymbolicLink(backup)) { "$label corruption backup became a symbolic link" }
        require(Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) {
            "$label corruption backup is no longer a regular file"
        }
        check(!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            "$label reset target now exists; backup retained at $backup"
        }
        Files.move(backup, file)
        secureFile(file)
        forceDirectory(parent)
    }

    private fun forceDirectory(directory: Path) {
        runCatching {
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        }
    }

    private fun ensurePrivateDirectory(directory: Path) {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(directory)) {
                "$label directory cannot be a symbolic link"
            }
            require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                "$label parent is not a directory"
            }
        } else {
            Files.createDirectories(directory)
        }
        try {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        } catch (_: UnsupportedOperationException) {
            val asFile = directory.toFile()
            asFile.setReadable(false, false)
            asFile.setReadable(true, true)
            asFile.setWritable(false, false)
            asFile.setWritable(true, true)
            asFile.setExecutable(false, false)
            asFile.setExecutable(true, true)
        }
    }

    private fun secureFile(path: Path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
            val asFile = path.toFile()
            asFile.setReadable(false, false)
            asFile.setReadable(true, true)
            asFile.setWritable(false, false)
            asFile.setWritable(true, true)
            asFile.setExecutable(false, false)
        }
    }

    private fun rejectTargetSymlink() {
        require(!Files.isSymbolicLink(file)) { "$label file cannot be a symbolic link" }
    }

    private companion object {
        val SAFE_BACKUP_ID = Regex("[A-Za-z0-9-]{1,128}")
    }
}
