package com.postsyncpro.presentation.screen.workbench

import com.postsyncpro.domain.model.WorkbenchAssertion
import com.postsyncpro.presentation.i18n.AppStrings
import com.postsyncpro.presentation.i18n.ArabicStrings
import com.postsyncpro.presentation.i18n.FrenchStrings
import com.postsyncpro.presentation.i18n.GermanStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkbenchScreenUiPolicyTest {
    @Test
    fun `save command is resolved immediately for the current workbench state`() {
        assertEquals(
            WorkbenchSaveCommand.SAVE_CURRENT,
            resolveWorkbenchSaveCommand(
                hasActiveSavedRequest = true,
                saveAsRequested = false,
                shortcutsBlocked = false,
                librarySaving = false
            )
        )
        assertEquals(
            WorkbenchSaveCommand.OPEN_SAVE_DIALOG,
            resolveWorkbenchSaveCommand(
                hasActiveSavedRequest = false,
                saveAsRequested = false,
                shortcutsBlocked = false,
                librarySaving = false
            )
        )
        assertEquals(
            WorkbenchSaveCommand.OPEN_SAVE_AS_DIALOG,
            resolveWorkbenchSaveCommand(
                hasActiveSavedRequest = true,
                saveAsRequested = true,
                shortcutsBlocked = false,
                librarySaving = false
            )
        )
    }

    @Test
    fun `save shortcuts are consumed without action behind dialogs or during persistence`() {
        assertEquals(
            WorkbenchSaveCommand.NONE,
            resolveWorkbenchSaveCommand(
                hasActiveSavedRequest = true,
                saveAsRequested = false,
                shortcutsBlocked = true,
                librarySaving = false
            )
        )
        assertEquals(
            WorkbenchSaveCommand.NONE,
            resolveWorkbenchSaveCommand(
                hasActiveSavedRequest = false,
                saveAsRequested = true,
                shortcutsBlocked = false,
                librarySaving = true
            )
        )
    }

    @Test
    fun `numeric request buffers accept only bounded values`() {
        assertEquals(100L, parseWorkbenchBoundedLong("100", 100L, 300_000L))
        assertEquals(300_000L, parseWorkbenchBoundedLong("300000", 100L, 300_000L))
        assertNull(parseWorkbenchBoundedLong("99", 100L, 300_000L))
        assertNull(parseWorkbenchBoundedLong("300001", 100L, 300_000L))
        assertNull(parseWorkbenchBoundedLong("", 100L, 300_000L))
        assertNull(parseWorkbenchBoundedLong("999999999999999999999", 100L, 300_000L))
    }

    @Test
    fun `invalid timeout buffer survives editor disposal and keeps send blocked`() {
        val firstComposition = workbenchRequestTimeoutSpec(committedValue = 1_000L)
        val rootOwnedBuffers = mutableMapOf(
            firstComposition.key to WorkbenchNumericFieldBuffer(
                text = "",
                lastCommittedValue = firstComposition.committedValue
            )
        )

        // Recreating the field models leaving and returning to a compact pane.
        val recreatedField = workbenchRequestTimeoutSpec(committedValue = 1_000L)

        assertEquals(firstComposition.key, recreatedField.key)
        assertEquals("", workbenchNumericFieldText(rootOwnedBuffers[recreatedField.key], recreatedField))
        assertFalse(areWorkbenchNumericFieldsValid(listOf(recreatedField), rootOwnedBuffers))
    }

    @Test
    fun `assertion numeric buffers are keyed by assertion and slot across subtab switches`() {
        val before = WorkbenchAssertion.StatusRange(
            id = "status-range-1",
            minimum = 200,
            maximum = 299
        )
        val after = before.copy(minimum = 201, maximum = 298)
        val beforeSpecs = workbenchAssertionNumericFieldSpecs(listOf(before))
        val afterSpecs = workbenchAssertionNumericFieldSpecs(listOf(after))

        assertEquals(beforeSpecs.map { it.key }, afterSpecs.map { it.key })
        assertNotEquals(beforeSpecs[0].key, beforeSpecs[1].key)

        val invalidMinimum = WorkbenchNumericFieldBuffer(
            text = "99",
            lastCommittedValue = before.minimum.toLong()
        )
        val reconciled = reconcileWorkbenchNumericFieldBuffer(invalidMinimum, afterSpecs[0])

        assertEquals("99", reconciled.text)
        assertFalse(isWorkbenchNumericFieldValid(reconciled, afterSpecs[0]))
    }

    @Test
    fun `numeric buffer reconciliation follows valid commits and resets stale valid text`() {
        val initial = workbenchRequestTimeoutSpec(committedValue = 1_000L)
        val userEdit = WorkbenchNumericFieldBuffer(text = "1500", lastCommittedValue = 1_000L)

        val matchingCommit = workbenchRequestTimeoutSpec(committedValue = 1_500L)
        assertEquals(
            "1500",
            reconcileWorkbenchNumericFieldBuffer(userEdit, matchingCommit).text
        )

        val externalCommit = workbenchRequestTimeoutSpec(committedValue = 2_000L)
        assertEquals(
            "2000",
            reconcileWorkbenchNumericFieldBuffer(userEdit, externalCommit).text
        )
        assertTrue(areWorkbenchNumericFieldsValid(listOf(initial), emptyMap()))
    }

    @Test
    fun `draft replacement gets fresh numeric keys without leaking invalid text`() {
        val previousDraft = workbenchRequestTimeoutSpec(committedValue = 1_000L, draftIdentity = 4L)
        val buffers = mapOf(
            previousDraft.key to WorkbenchNumericFieldBuffer(
                text = "",
                lastCommittedValue = previousDraft.committedValue
            )
        )
        val openedDraft = workbenchRequestTimeoutSpec(committedValue = 2_500L, draftIdentity = 5L)

        assertNotEquals(previousDraft.key, openedDraft.key)
        assertEquals("2500", workbenchNumericFieldText(buffers[openedDraft.key], openedDraft))
        assertTrue(areWorkbenchNumericFieldsValid(listOf(openedDraft), buffers))
    }

    @Test
    fun `disabled assertion buffers are retained but do not block send validation`() {
        val disabled = WorkbenchAssertion.StatusEquals(
            id = "disabled-status",
            enabled = false,
            expected = 200
        )
        val storedSpecs = workbenchAssertionNumericFieldSpecs(listOf(disabled), draftIdentity = 2L)
        val validationSpecs = workbenchNumericValidationFieldSpecs(
            timeoutMillis = 1_000L,
            assertions = listOf(disabled),
            draftIdentity = 2L
        )

        assertEquals(1, storedSpecs.size)
        assertEquals(1, validationSpecs.size)
        assertEquals("timeoutMillis", validationSpecs.single().key.fieldId)
    }

    @Test
    fun `send shortcut reads latest numeric buffer after valid edit commit`() {
        val editedSpec = workbenchRequestTimeoutSpec(committedValue = 1_000L)
        val buffers = mutableMapOf(
            editedSpec.key to WorkbenchNumericFieldBuffer(
                text = "",
                lastCommittedValue = editedSpec.committedValue
            )
        )
        var currentSpecs = listOf(editedSpec)
        fun shortcutEnabled() = canExecuteWorkbenchSendShortcut(
            shortcutsBlocked = false,
            numericFieldsValid = { areWorkbenchNumericFieldsValid(currentSpecs, buffers) },
            isSending = false,
            hasRequestUrl = true,
            hasRequiredSecretRestorations = false,
            replayRequiresFullRequest = false,
            environmentBusy = false
        )

        assertFalse(shortcutEnabled())

        buffers[editedSpec.key] = WorkbenchNumericFieldBuffer(
            text = "1500",
            lastCommittedValue = editedSpec.committedValue
        )
        val committedSpec = workbenchRequestTimeoutSpec(committedValue = 1_500L)
        buffers[committedSpec.key] = reconcileWorkbenchNumericFieldBuffer(
            buffers[committedSpec.key],
            committedSpec
        )
        currentSpecs = listOf(committedSpec)

        assertTrue(shortcutEnabled())
        assertEquals("1500", workbenchNumericFieldText(buffers[committedSpec.key], committedSpec))
    }

    @Test
    fun `localized collection delete copy names the collection and affected contents`() {
        val collectionName = "Payments v2"
        listOf(AppStrings(), ArabicStrings(), FrenchStrings(), GermanStrings()).forEach { strings ->
            val title = strings.workbenchDeleteCollectionTitle(collectionName)
            val impact = strings.workbenchDeleteCollectionImpact(requestCount = 12, folderCount = 3)

            assertTrue(title.contains(collectionName), title)
            assertTrue(impact.contains("12"), impact)
            assertTrue(impact.contains("3"), impact)
            assertFalse(impact.contains(strings.workbenchNoCollections), impact)
        }
    }

    @Test
    fun `tab arrow navigation wraps and mirrors horizontal direction in RTL`() {
        assertEquals(2, resolveWorkbenchTabIndex(0, 3, WorkbenchTabDirection.LEFT, isRtl = false))
        assertEquals(1, resolveWorkbenchTabIndex(0, 3, WorkbenchTabDirection.RIGHT, isRtl = false))
        assertEquals(1, resolveWorkbenchTabIndex(0, 3, WorkbenchTabDirection.LEFT, isRtl = true))
        assertEquals(2, resolveWorkbenchTabIndex(0, 3, WorkbenchTabDirection.RIGHT, isRtl = true))
        assertEquals(0, resolveWorkbenchTabIndex(2, 3, WorkbenchTabDirection.RIGHT, isRtl = false))
        assertEquals(0, resolveWorkbenchTabIndex(2, 3, WorkbenchTabDirection.LEFT, isRtl = true))
        assertNull(resolveWorkbenchTabIndex(0, 0, WorkbenchTabDirection.RIGHT, isRtl = false))
        assertNull(resolveWorkbenchTabIndex(4, 3, WorkbenchTabDirection.RIGHT, isRtl = false))
    }

    @Test
    fun `workbench assertion auth response and recovery copy is localized and complete`() {
        val english = AppStrings()
        val localized = listOf(ArabicStrings(), FrenchStrings(), GermanStrings())

        localized.forEach { strings ->
            val assertionLabels = listOf(
                strings.workbenchAssertionStatusEquals,
                strings.workbenchAssertionStatusRange,
                strings.workbenchAssertionResponseTime,
                strings.workbenchAssertionHeaderExists,
                strings.workbenchAssertionHeaderEquals,
                strings.workbenchAssertionHeaderContains,
                strings.workbenchAssertionBodyContains,
                strings.workbenchAssertionJsonPathExists,
                strings.workbenchAssertionJsonPathEquals
            )
            val checkLabels = listOf(
                strings.workbenchCheckPassed,
                strings.workbenchCheckFailed,
                strings.workbenchCheckError,
                strings.workbenchCheckSkipped
            )
            val authLabels = listOf(
                strings.workbenchAuthNone,
                strings.workbenchAuthBearer,
                strings.workbenchAuthBasic,
                strings.workbenchAuthApiKey
            )
            val apiKeyLocations = listOf(
                strings.workbenchApiKeyLocationHeader,
                strings.workbenchApiKeyLocationQuery,
                strings.workbenchApiKeyLocationCookie
            )

            assertTrue(assertionLabels.all { it.isNotBlank() })
            assertEquals(assertionLabels.size, assertionLabels.distinct().size)
            assertEquals(checkLabels.size, checkLabels.distinct().size)
            assertEquals(authLabels.size, authLabels.distinct().size)
            assertEquals(apiKeyLocations.size, apiKeyLocations.distinct().size)
            assertTrue(strings.workbenchResponseStatusLabel("418 Teapot").contains("418 Teapot"))
            assertTrue(strings.workbenchResponseTimeLabel(321).contains("321"))
            assertTrue(strings.workbenchResponseSizeLabel("12 KiB").contains("12 KiB"))
            assertTrue(strings.workbenchEnvironmentRecoveryWarning.isNotBlank())
            assertTrue(strings.workbenchLibraryRecoveryWarning.isNotBlank())
            assertTrue(strings.workbenchMissingVariable("token", "BODY").contains("token"))
            assertNotEquals(strings.stopped, strings.workbenchCheckSkipped)
        }

        assertNotEquals(english.workbenchExpanded, ArabicStrings().workbenchExpanded)
        assertNotEquals(english.workbenchIgnoreCase, FrenchStrings().workbenchIgnoreCase)
        assertNotEquals(english.workbenchSaveRequestAs, GermanStrings().workbenchSaveRequestAs)
    }

    @Test
    fun `corrupt store recovery is available only while its repository is idle`() {
        assertTrue(
            isWorkbenchRecoveryActionEnabled(
                recoveryAvailable = true,
                isLoading = false,
                isSaving = false
            )
        )
        assertFalse(
            isWorkbenchRecoveryActionEnabled(
                recoveryAvailable = true,
                isLoading = true,
                isSaving = false
            )
        )
        assertFalse(
            isWorkbenchRecoveryActionEnabled(
                recoveryAvailable = true,
                isLoading = false,
                isSaving = true
            )
        )
        assertFalse(
            isWorkbenchRecoveryActionEnabled(
                recoveryAvailable = false,
                isLoading = false,
                isSaving = false
            )
        )
    }
}
