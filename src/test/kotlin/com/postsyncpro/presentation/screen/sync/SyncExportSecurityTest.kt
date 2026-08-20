package com.postsyncpro.presentation.screen.sync

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncExportSecurityTest {

    @Test
    fun `collection names cannot escape the selected export directory`() {
        val directory = createTempDirectory("postsync-export-").toFile()
        try {
            val target = resolveSafeCollectionExportFile(directory, "../../etc/passwd")

            assertEquals(directory.canonicalFile, target.parentFile.canonicalFile)
            assertEquals("etc_passwd_merged.json", target.name)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `reserved and blank names receive safe deterministic filenames`() {
        assertEquals("_CON_merged.json", sanitizeCollectionExportName("CON"))
        assertEquals("collection_merged.json", sanitizeCollectionExportName(" ../ "))
    }

    @Test
    fun `atomic writer replaces content and leaves no temporary files`() {
        val directory = createTempDirectory("postsync-export-atomic-").toFile()
        try {
            val target = directory.resolve("collection_merged.json")
            atomicWriteUtf8(target, "first")
            atomicWriteUtf8(target, "second")

            assertEquals("second", target.readText())
            assertTrue(target.isFile)
            assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith(".postsync-export-") })
        } finally {
            directory.deleteRecursively()
        }
    }
}
