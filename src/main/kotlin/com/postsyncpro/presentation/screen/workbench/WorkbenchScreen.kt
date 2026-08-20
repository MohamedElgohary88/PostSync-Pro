package com.postsyncpro.presentation.screen.workbench

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.data.parser.SwaggerParser
import com.postsyncpro.domain.model.*
import com.postsyncpro.presentation.components.FilePickerButton
import com.postsyncpro.presentation.i18n.AppStrings
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class RequestEditorTab { PARAMS, HEADERS, BODY, AUTH, VARIABLES, ASSERTIONS }
private enum class ResponseViewerTab { BODY, HEADERS, CHECKS }
private enum class WorkbenchPane { LIBRARY, REQUEST, RESPONSE }
private enum class WorkbenchRailTab { COLLECTIONS, CONTRACT, HISTORY }
internal enum class WorkbenchSaveCommand { NONE, SAVE_CURRENT, OPEN_SAVE_DIALOG, OPEN_SAVE_AS_DIALOG }
internal enum class WorkbenchTabDirection { LEFT, RIGHT }
private enum class AssertionKind {
    STATUS_EQUALS,
    STATUS_RANGE,
    RESPONSE_TIME,
    HEADER_EXISTS,
    HEADER_EQUALS,
    HEADER_CONTAINS,
    BODY_CONTAINS,
    JSON_PATH_EXISTS,
    JSON_PATH_EQUALS
}
private enum class RibbonState { IDLE, ACTIVE, SUCCESS, ERROR }
private const val MAX_BODY_PREVIEW_CHARS = 100_000
private const val BODY_PREVIEW_CHUNK_CHARS = 2_000

private typealias WorkbenchDialogRegistration = (token: Any, visible: Boolean) -> Unit
private val LocalWorkbenchDialogRegistration = staticCompositionLocalOf<WorkbenchDialogRegistration> {
    { _, _ -> }
}

internal data class WorkbenchNumericFieldKey(
    val ownerId: String,
    val fieldId: String
)

internal data class WorkbenchNumericFieldSpec(
    val key: WorkbenchNumericFieldKey,
    val committedValue: Long,
    val minimum: Long,
    val maximum: Long
)

internal data class WorkbenchNumericFieldBuffer(
    val text: String,
    val lastCommittedValue: Long
)

/** The Workbench owns one active draft slot; its identity must outlive compact pane composition. */
private const val ACTIVE_DRAFT_NUMERIC_OWNER = "active-request-draft"

internal fun workbenchRequestTimeoutSpec(
    committedValue: Long,
    draftIdentity: Long = 0L
) = WorkbenchNumericFieldSpec(
    key = WorkbenchNumericFieldKey("$ACTIVE_DRAFT_NUMERIC_OWNER:$draftIdentity", "timeoutMillis"),
    committedValue = committedValue,
    minimum = 100L,
    maximum = 300_000L
)

internal fun workbenchAssertionNumericFieldSpecs(
    assertions: List<WorkbenchAssertion>,
    draftIdentity: Long = 0L
): List<WorkbenchNumericFieldSpec> = buildList {
    assertions.forEach { assertion ->
        val ownerId = "$ACTIVE_DRAFT_NUMERIC_OWNER:$draftIdentity:assertion:${assertion.id}"
        when (assertion) {
            is WorkbenchAssertion.StatusEquals -> add(
                WorkbenchNumericFieldSpec(
                    key = WorkbenchNumericFieldKey(ownerId, "statusEquals.expected"),
                    committedValue = assertion.expected.toLong(),
                    minimum = 100L,
                    maximum = 599L
                )
            )
            is WorkbenchAssertion.StatusRange -> {
                add(
                    WorkbenchNumericFieldSpec(
                        key = WorkbenchNumericFieldKey(ownerId, "statusRange.minimum"),
                        committedValue = assertion.minimum.toLong(),
                        minimum = 100L,
                        maximum = 599L
                    )
                )
                add(
                    WorkbenchNumericFieldSpec(
                        key = WorkbenchNumericFieldKey(ownerId, "statusRange.maximum"),
                        committedValue = assertion.maximum.toLong(),
                        minimum = 100L,
                        maximum = 599L
                    )
                )
            }
            is WorkbenchAssertion.ResponseTimeMax -> add(
                WorkbenchNumericFieldSpec(
                    key = WorkbenchNumericFieldKey(ownerId, "responseTimeMax.maximumMillis"),
                    committedValue = assertion.maximumMillis,
                    minimum = 0L,
                    maximum = 300_000L
                )
            )
            is WorkbenchAssertion.HeaderExists,
            is WorkbenchAssertion.HeaderEquals,
            is WorkbenchAssertion.HeaderContains,
            is WorkbenchAssertion.BodyContains,
            is WorkbenchAssertion.JsonPathExists,
            is WorkbenchAssertion.JsonPathEquals -> Unit
        }
    }
}

internal fun workbenchNumericValidationFieldSpecs(
    timeoutMillis: Long,
    assertions: List<WorkbenchAssertion>,
    draftIdentity: Long = 0L
): List<WorkbenchNumericFieldSpec> =
    listOf(workbenchRequestTimeoutSpec(timeoutMillis, draftIdentity)) +
        workbenchAssertionNumericFieldSpecs(
            assertions = assertions.filter { it.enabled },
            draftIdentity = draftIdentity
        )

internal fun reconcileWorkbenchNumericFieldBuffer(
    current: WorkbenchNumericFieldBuffer?,
    spec: WorkbenchNumericFieldSpec
): WorkbenchNumericFieldBuffer {
    if (current == null) {
        return WorkbenchNumericFieldBuffer(
            text = spec.committedValue.toString(),
            lastCommittedValue = spec.committedValue
        )
    }
    if (current.lastCommittedValue == spec.committedValue) return current

    val parsed = parseWorkbenchBoundedLong(current.text, spec.minimum, spec.maximum)
    return when {
        parsed == null -> current.copy(lastCommittedValue = spec.committedValue)
        parsed == spec.committedValue -> current.copy(lastCommittedValue = spec.committedValue)
        else -> WorkbenchNumericFieldBuffer(
            text = spec.committedValue.toString(),
            lastCommittedValue = spec.committedValue
        )
    }
}

internal fun workbenchNumericFieldText(
    buffer: WorkbenchNumericFieldBuffer?,
    spec: WorkbenchNumericFieldSpec
): String = reconcileWorkbenchNumericFieldBuffer(buffer, spec).text

internal fun isWorkbenchNumericFieldValid(
    buffer: WorkbenchNumericFieldBuffer?,
    spec: WorkbenchNumericFieldSpec
): Boolean = parseWorkbenchBoundedLong(
    workbenchNumericFieldText(buffer, spec),
    spec.minimum,
    spec.maximum
) != null

internal fun areWorkbenchNumericFieldsValid(
    specs: List<WorkbenchNumericFieldSpec>,
    buffers: Map<WorkbenchNumericFieldKey, WorkbenchNumericFieldBuffer>
): Boolean = specs.all { spec -> isWorkbenchNumericFieldValid(buffers[spec.key], spec) }

internal fun canExecuteWorkbenchSendShortcut(
    shortcutsBlocked: Boolean,
    numericFieldsValid: () -> Boolean,
    isSending: Boolean,
    hasRequestUrl: Boolean,
    hasRequiredSecretRestorations: Boolean,
    replayRequiresFullRequest: Boolean,
    environmentBusy: Boolean
): Boolean = !shortcutsBlocked && numericFieldsValid() && !isSending && hasRequestUrl &&
    !hasRequiredSecretRestorations && !replayRequiresFullRequest && !environmentBusy

internal fun resolveWorkbenchSaveCommand(
    hasActiveSavedRequest: Boolean,
    saveAsRequested: Boolean,
    shortcutsBlocked: Boolean,
    librarySaving: Boolean
): WorkbenchSaveCommand = when {
    shortcutsBlocked || librarySaving -> WorkbenchSaveCommand.NONE
    saveAsRequested -> WorkbenchSaveCommand.OPEN_SAVE_AS_DIALOG
    hasActiveSavedRequest -> WorkbenchSaveCommand.SAVE_CURRENT
    else -> WorkbenchSaveCommand.OPEN_SAVE_DIALOG
}

internal fun parseWorkbenchBoundedLong(value: String, minimum: Long, maximum: Long): Long? =
    value.toLongOrNull()?.takeIf { it in minimum..maximum }

internal fun isWorkbenchRecoveryActionEnabled(
    recoveryAvailable: Boolean,
    isLoading: Boolean,
    isSaving: Boolean
): Boolean = recoveryAvailable && !isLoading && !isSaving

internal fun resolveWorkbenchTabIndex(
    currentIndex: Int,
    tabCount: Int,
    direction: WorkbenchTabDirection,
    isRtl: Boolean
): Int? {
    if (tabCount <= 0 || currentIndex !in 0 until tabCount) return null
    val delta = when (direction) {
        WorkbenchTabDirection.LEFT -> if (isRtl) 1 else -1
        WorkbenchTabDirection.RIGHT -> if (isRtl) -1 else 1
    }
    return (currentIndex + delta + tabCount) % tabCount
}

private fun workbenchTabDirection(key: Key): WorkbenchTabDirection? = when (key) {
    Key.DirectionLeft -> WorkbenchTabDirection.LEFT
    Key.DirectionRight -> WorkbenchTabDirection.RIGHT
    else -> null
}

@Composable
fun WorkbenchScreen(viewModel: WorkbenchViewModel) {
    val state by viewModel.state.collectAsState()
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val workbenchFocusRequester = remember { FocusRequester() }
    var compactPane by remember { mutableStateOf(WorkbenchPane.REQUEST) }
    var railTab by remember { mutableStateOf(WorkbenchRailTab.COLLECTIONS) }
    var requestEditorTab by remember { mutableStateOf(RequestEditorTab.PARAMS) }
    var responseViewerTab by remember { mutableStateOf(ResponseViewerTab.BODY) }
    var collectionQuery by remember { mutableStateOf("") }
    val expandedCollections = remember { mutableStateMapOf<String, Boolean>() }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveAs by remember { mutableStateOf(false) }
    var numericDraftIdentity by remember { mutableLongStateOf(0L) }
    val openDialogs = remember { mutableStateMapOf<Any, Unit>() }
    val numericFieldBuffers = remember {
        mutableStateMapOf<WorkbenchNumericFieldKey, WorkbenchNumericFieldBuffer>()
    }
    val numericFieldSpecs = remember(numericDraftIdentity, state.draft.timeoutMillis, state.assertions) {
        listOf(workbenchRequestTimeoutSpec(state.draft.timeoutMillis, numericDraftIdentity)) +
            workbenchAssertionNumericFieldSpecs(state.assertions, numericDraftIdentity)
    }
    val numericValidationSpecs = remember(numericDraftIdentity, state.draft.timeoutMillis, state.assertions) {
        workbenchNumericValidationFieldSpecs(
            timeoutMillis = state.draft.timeoutMillis,
            assertions = state.assertions,
            draftIdentity = numericDraftIdentity
        )
    }
    val dialogRegistration = remember {
        { token: Any, visible: Boolean ->
            if (visible) openDialogs[token] = Unit else openDialogs.remove(token)
            Unit
        }
    }
    val shortcutsBlocked = showSaveDialog || openDialogs.isNotEmpty()
    val inputsValid = areWorkbenchNumericFieldsValid(numericValidationSpecs, numericFieldBuffers)
    val onNumericBufferChange: (WorkbenchNumericFieldSpec, String) -> Unit = { spec, text ->
        numericFieldBuffers[spec.key] = WorkbenchNumericFieldBuffer(
            text = text,
            lastCommittedValue = spec.committedValue
        )
    }

    LaunchedEffect(numericFieldSpecs) {
        val activeKeys = numericFieldSpecs.mapTo(linkedSetOf()) { it.key }
        numericFieldSpecs.forEach { spec ->
            val current = numericFieldBuffers[spec.key]
            val reconciled = reconcileWorkbenchNumericFieldBuffer(current, spec)
            if (current != reconciled) numericFieldBuffers[spec.key] = reconciled
        }
        numericFieldBuffers.keys.toList()
            .filterNot { it in activeKeys }
            .forEach { key -> numericFieldBuffers.remove(key) }
    }

    LaunchedEffect(Unit) {
        workbenchFocusRequester.requestFocus()
    }

    fun executeSaveCommand(saveAsRequested: Boolean) {
        when (
            resolveWorkbenchSaveCommand(
                hasActiveSavedRequest = state.activeSavedRequestId != null,
                saveAsRequested = saveAsRequested,
                shortcutsBlocked = shortcutsBlocked,
                librarySaving = state.isLibrarySaving
            )
        ) {
            WorkbenchSaveCommand.NONE -> Unit
            WorkbenchSaveCommand.SAVE_CURRENT -> viewModel.saveCurrentRequest()
            WorkbenchSaveCommand.OPEN_SAVE_DIALOG -> {
                saveAs = false
                showSaveDialog = true
            }
            WorkbenchSaveCommand.OPEN_SAVE_AS_DIALOG -> {
                saveAs = true
                showSaveDialog = true
            }
        }
    }

    LaunchedEffect(state.response, state.executionFailure) {
        if (state.response != null || state.executionFailure != null) {
            compactPane = WorkbenchPane.RESPONSE
        }
    }

    CompositionLocalProvider(LocalWorkbenchDialogRegistration provides dialogRegistration) {
        if (showSaveDialog) {
            SaveRequestDialog(
                state = state,
                saveAs = saveAs,
                onDismiss = { showSaveDialog = false },
                onSave = { collectionId, name ->
                    viewModel.saveCurrentRequest(collectionId = collectionId, name = name, saveAs = saveAs)
                    showSaveDialog = false
                },
                onCreateAndSave = { collectionName, requestName ->
                    viewModel.createCollectionAndSaveRequest(collectionName, requestName)
                    showSaveDialog = false
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || (!event.isMetaPressed && !event.isCtrlPressed)) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.Enter -> {
                        if (canExecuteWorkbenchSendShortcut(
                            shortcutsBlocked = shortcutsBlocked,
                            numericFieldsValid = {
                                areWorkbenchNumericFieldsValid(numericValidationSpecs, numericFieldBuffers)
                            },
                            isSending = state.isSending,
                            hasRequestUrl = state.draft.url.isNotBlank(),
                            hasRequiredSecretRestorations = state.requiredSecretRestorations.isNotEmpty(),
                            replayRequiresFullRequest = state.replayRequiresFullRequest,
                            environmentBusy = state.isEnvironmentLoading || state.isEnvironmentSaving
                        )) {
                            viewModel.send()
                        }
                        true
                    }
                    Key.S -> {
                        if (!shortcutsBlocked) executeSaveCommand(saveAsRequested = event.isShiftPressed)
                        true
                    }
                    else -> false
                }
            }.focusRequester(workbenchFocusRequester).focusable(),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    strings.screenWorkbench,
                    style = typography.heading1.copy(color = palette.textPrimary)
                )
                Text(
                    strings.hintWorkbench,
                    style = typography.bodySmall.copy(color = palette.textSecondary)
                )
            }
            EnvironmentSelector(state = state, viewModel = viewModel)
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth < 1_050.dp) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TabStrip(
                        tabs = WorkbenchPane.entries,
                        selected = compactPane,
                        label = {
                            when (it) {
                                WorkbenchPane.LIBRARY -> strings.workbenchLibrary
                                WorkbenchPane.REQUEST -> strings.workbenchRequest
                                WorkbenchPane.RESPONSE -> strings.workbenchResponse
                            }
                        },
                        onSelect = { compactPane = it }
                    )
                    Spacer(Modifier.height(AppDimensions.spacingSm))
                    when (compactPane) {
                        WorkbenchPane.LIBRARY -> WorkbenchRail(
                            state = state,
                            viewModel = viewModel,
                            selectedTab = railTab,
                            onSelectTab = { railTab = it },
                            collectionQuery = collectionQuery,
                            onCollectionQueryChange = { collectionQuery = it },
                            expandedCollections = expandedCollections,
                            onDraftReplacement = { numericDraftIdentity += 1L },
                            modifier = Modifier.fillMaxSize()
                        )
                        WorkbenchPane.REQUEST -> RequestEditor(
                            state = state,
                            viewModel = viewModel,
                            selectedTab = requestEditorTab,
                            onSelectTab = { requestEditorTab = it },
                            onSaveRequest = { executeSaveCommand(saveAsRequested = false) },
                            onSaveRequestAs = { executeSaveCommand(saveAsRequested = true) },
                            inputsValid = inputsValid,
                            numericDraftIdentity = numericDraftIdentity,
                            numericFieldBuffers = numericFieldBuffers,
                            onNumericBufferChange = onNumericBufferChange,
                            modifier = Modifier.fillMaxSize()
                        )
                        WorkbenchPane.RESPONSE -> ResponseViewer(
                            state = state,
                            selectedTab = responseViewerTab,
                            onSelectTab = { responseViewerTab = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.spacingMd)
                ) {
                    WorkbenchRail(
                        state = state,
                        viewModel = viewModel,
                        selectedTab = railTab,
                        onSelectTab = { railTab = it },
                        collectionQuery = collectionQuery,
                        onCollectionQueryChange = { collectionQuery = it },
                        expandedCollections = expandedCollections,
                        onDraftReplacement = { numericDraftIdentity += 1L },
                        modifier = Modifier.width(258.dp).fillMaxHeight()
                    )
                    RequestEditor(
                        state = state,
                        viewModel = viewModel,
                        selectedTab = requestEditorTab,
                        onSelectTab = { requestEditorTab = it },
                        onSaveRequest = { executeSaveCommand(saveAsRequested = false) },
                        onSaveRequestAs = { executeSaveCommand(saveAsRequested = true) },
                        inputsValid = inputsValid,
                        numericDraftIdentity = numericDraftIdentity,
                        numericFieldBuffers = numericFieldBuffers,
                        onNumericBufferChange = onNumericBufferChange,
                        modifier = Modifier.weight(1.05f).fillMaxHeight()
                    )
                    ResponseViewer(
                        state = state,
                        selectedTab = responseViewerTab,
                        onSelectTab = { responseViewerTab = it },
                        modifier = Modifier.weight(0.95f).fillMaxHeight()
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun EnvironmentSelector(state: WorkbenchState, viewModel: WorkbenchViewModel) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    var expanded by remember { mutableStateOf(false) }
    var showNewEnvironment by remember { mutableStateOf(false) }
    var environmentName by remember { mutableStateOf("") }
    var showSwitchConfirmation by remember { mutableStateOf(false) }
    var pendingEnvironmentId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showRecoveryConfirmation by remember { mutableStateOf(false) }
    val active = state.environmentSnapshot.activeEnvironment
    val recoveryEnabled = isWorkbenchRecoveryActionEnabled(
        recoveryAvailable = state.environmentRecoveryAvailable,
        isLoading = state.isEnvironmentLoading,
        isSaving = state.isEnvironmentSaving
    )

    if (showNewEnvironment) {
        WorkbenchAlertDialog(
            onDismissRequest = { showNewEnvironment = false },
            title = { Text(strings.workbenchNewEnvironment) },
            text = {
                WorkbenchTextField(
                    value = environmentName,
                    onValueChange = { environmentName = it.take(256) },
                    placeholder = strings.workbenchEnvironmentName,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = environmentName.isNotBlank(),
                    onClick = {
                        viewModel.createEnvironment(environmentName)
                        environmentName = ""
                        showNewEnvironment = false
                    }
                ) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showNewEnvironment = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showSwitchConfirmation) {
        WorkbenchAlertDialog(
            onDismissRequest = { showSwitchConfirmation = false },
            title = { Text(strings.workbenchEnvironment) },
            text = { Text(strings.workbenchDiscardChanges) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.selectEnvironment(pendingEnvironmentId, discardUnsavedChanges = true)
                    showSwitchConfirmation = false
                }) { Text(strings.continueText) }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchConfirmation = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showDeleteConfirmation && active != null) {
        WorkbenchAlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(strings.workbenchDeleteEnvironment) },
            text = { Text(active.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEnvironment(active.id, discardUnsavedChanges = true)
                    showDeleteConfirmation = false
                }) { Text(strings.delete, color = palette.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showRecoveryConfirmation) {
        WorkbenchAlertDialog(
            onDismissRequest = { showRecoveryConfirmation = false },
            title = { Text(strings.workbenchEnvironmentRecoveryTitle) },
            text = { Text(strings.workbenchEnvironmentRecoveryWarning) },
            confirmButton = {
                TextButton(
                    enabled = recoveryEnabled,
                    onClick = {
                        showRecoveryConfirmation = false
                        viewModel.backupAndResetCorruptEnvironments()
                    }
                ) { Text(strings.workbenchBackupAndReset, color = palette.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryConfirmation = false }) { Text(strings.cancel) }
            }
        )
    }

    Column(horizontalAlignment = Alignment.End) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.widthIn(min = 150.dp, max = 220.dp).height(38.dp),
                contentPadding = PaddingValues(horizontal = 9.dp),
                border = BorderStroke(1.dp, if (state.environmentDirty) palette.warning else palette.border)
            ) {
                if (state.isEnvironmentLoading || state.isEnvironmentSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = palette.accent)
                } else {
                    Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp), tint = palette.accent)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    (active?.name ?: strings.workbenchScratch) + if (state.environmentDirty) " •" else "",
                    modifier = Modifier.weight(1f),
                    style = typography.caption.copy(color = palette.textPrimary, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(17.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(strings.workbenchScratch) },
                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                    onClick = {
                        expanded = false
                        pendingEnvironmentId = null
                        if (state.environmentDirty) showSwitchConfirmation = true
                        else viewModel.selectEnvironment(null)
                    }
                )
                state.environmentSnapshot.environments.forEach { environment ->
                    DropdownMenuItem(
                        text = { Text(environment.name) },
                        leadingIcon = {
                            Icon(
                                if (environment.id == active?.id) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            expanded = false
                            pendingEnvironmentId = environment.id
                            if (state.environmentDirty) showSwitchConfirmation = true
                            else viewModel.selectEnvironment(environment.id)
                        }
                    )
                }
            }
        }
        IconButton(onClick = { showNewEnvironment = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, contentDescription = strings.workbenchNewEnvironment, tint = palette.accent, modifier = Modifier.size(17.dp))
        }
        if (active != null && state.environmentDirty) {
            IconButton(onClick = { viewModel.saveEnvironment() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Save, contentDescription = strings.workbenchSaveEnvironment, tint = palette.warning, modifier = Modifier.size(17.dp))
            }
        }
        if (active != null) {
            IconButton(onClick = { showDeleteConfirmation = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = strings.workbenchDeleteEnvironment, tint = palette.textTertiary, modifier = Modifier.size(17.dp))
            }
        }
    }
    state.environmentErrorMessage?.let { message ->
        Text(
            message,
            modifier = Modifier.widthIn(max = 310.dp)
                .padding(top = 2.dp)
                .workbenchErrorSemantics(message, LiveRegionMode.Polite),
            style = typography.caption.copy(color = palette.error),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (state.environmentRecoveryAvailable) {
        TextButton(
            onClick = { showRecoveryConfirmation = true },
            enabled = recoveryEnabled,
            modifier = Modifier.widthIn(max = 310.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(strings.workbenchBackupAndReset, style = typography.caption, color = palette.error)
        }
    }
    }
}

@Composable
private fun WorkbenchRail(
    state: WorkbenchState,
    viewModel: WorkbenchViewModel,
    selectedTab: WorkbenchRailTab,
    onSelectTab: (WorkbenchRailTab) -> Unit,
    collectionQuery: String,
    onCollectionQueryChange: (String) -> Unit,
    expandedCollections: MutableMap<String, Boolean>,
    onDraftReplacement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    var showNewCollection by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    var showClearHistoryConfirmation by remember { mutableStateOf(false) }
    var collectionToDelete by remember { mutableStateOf<String?>(null) }
    var requestToDelete by remember { mutableStateOf<String?>(null) }
    var savedRequestToOpen by remember { mutableStateOf<String?>(null) }
    var historyToOpen by remember { mutableStateOf<String?>(null) }
    var showLibraryRecoveryConfirmation by remember { mutableStateOf(false) }
    val recoveryEnabled = isWorkbenchRecoveryActionEnabled(
        recoveryAvailable = state.libraryRecoveryAvailable,
        isLoading = state.isLibraryLoading,
        isSaving = state.isLibrarySaving
    )

    if (showNewCollection) {
        WorkbenchAlertDialog(
            onDismissRequest = { showNewCollection = false },
            title = { Text(strings.workbenchNewCollection) },
            text = {
                WorkbenchTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it.take(256) },
                    placeholder = strings.workbenchCollectionName,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newCollectionName.isNotBlank(),
                    onClick = {
                        viewModel.createCollection(newCollectionName)
                        newCollectionName = ""
                        showNewCollection = false
                    }
                ) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showNewCollection = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showClearHistoryConfirmation) {
        WorkbenchAlertDialog(
            onDismissRequest = { showClearHistoryConfirmation = false },
            title = { Text("${strings.clear} ${strings.workbenchHistory}?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryConfirmation = false
                    viewModel.clearHistory()
                }) { Text(strings.clear) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirmation = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    collectionToDelete?.let { id ->
        state.savedWorkspace.collections.firstOrNull { it.id == id }?.let { collection ->
            val affectedRequestCount = state.savedWorkspace.requests.count { it.collectionId == id }
            val affectedFolderCount = state.savedWorkspace.folders.count { it.collectionId == id }
            WorkbenchAlertDialog(
                onDismissRequest = { collectionToDelete = null },
                title = { Text(strings.workbenchDeleteCollectionTitle(collection.name)) },
                text = {
                    Text(
                        strings.workbenchDeleteCollectionImpact(
                            requestCount = affectedRequestCount,
                            folderCount = affectedFolderCount
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !state.isLibrarySaving,
                        onClick = {
                            viewModel.deleteCollection(id)
                            collectionToDelete = null
                        }
                    ) { Text(strings.delete, color = palette.error) }
                },
                dismissButton = {
                    TextButton(onClick = { collectionToDelete = null }) { Text(strings.cancel) }
                }
            )
        }
    }

    requestToDelete?.let { id ->
        WorkbenchAlertDialog(
            onDismissRequest = { requestToDelete = null },
            title = { Text("${strings.delete} ${strings.workbenchRequest}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSavedRequest(id)
                    requestToDelete = null
                }) { Text(strings.delete, color = palette.error) }
            },
            dismissButton = {
                TextButton(onClick = { requestToDelete = null }) { Text(strings.cancel) }
            }
        )
    }

    savedRequestToOpen?.let { id ->
        WorkbenchAlertDialog(
            onDismissRequest = { savedRequestToOpen = null },
            title = { Text(strings.workbenchOpenRequest) },
            text = { Text(strings.workbenchDiscardChanges) },
            confirmButton = {
                TextButton(onClick = {
                    onDraftReplacement()
                    viewModel.openSavedRequest(id, discardUnsavedChanges = true)
                    savedRequestToOpen = null
                }) { Text(strings.workbenchOpenRequest) }
            },
            dismissButton = {
                TextButton(onClick = { savedRequestToOpen = null }) { Text(strings.cancel) }
            }
        )
    }

    historyToOpen?.let { id ->
        WorkbenchAlertDialog(
            onDismissRequest = { historyToOpen = null },
            title = { Text(strings.workbenchReplay) },
            text = { Text(strings.workbenchDiscardChanges) },
            confirmButton = {
                TextButton(onClick = {
                    onDraftReplacement()
                    viewModel.reopenHistory(id, discardUnsavedChanges = true)
                    historyToOpen = null
                }) { Text(strings.workbenchReplay) }
            },
            dismissButton = {
                TextButton(onClick = { historyToOpen = null }) { Text(strings.cancel) }
            }
        )
    }

    if (showLibraryRecoveryConfirmation) {
        WorkbenchAlertDialog(
            onDismissRequest = { showLibraryRecoveryConfirmation = false },
            title = { Text(strings.workbenchLibraryRecoveryTitle) },
            text = { Text(strings.workbenchLibraryRecoveryWarning) },
            confirmButton = {
                TextButton(
                    enabled = recoveryEnabled,
                    onClick = {
                        showLibraryRecoveryConfirmation = false
                        viewModel.backupAndResetCorruptLibrary()
                    }
                ) { Text(strings.workbenchBackupAndReset, color = palette.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLibraryRecoveryConfirmation = false }) { Text(strings.cancel) }
            }
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimensions.cardRadius),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabStrip(
                tabs = WorkbenchRailTab.entries,
                selected = selectedTab,
                label = {
                    when (it) {
                        WorkbenchRailTab.COLLECTIONS -> strings.workbenchCollections
                        WorkbenchRailTab.CONTRACT -> strings.workbenchContract
                        WorkbenchRailTab.HISTORY -> strings.workbenchHistory
                    }
                },
                onSelect = onSelectTab
            )
            HorizontalDivider(color = palette.divider)
            if (state.libraryRecoveryAvailable) {
                TextButton(
                    onClick = { showLibraryRecoveryConfirmation = true },
                    enabled = recoveryEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.workbenchBackupAndReset, style = LocalAppTypography.current.caption, color = palette.error)
                }
                HorizontalDivider(color = palette.divider)
            }
            when (selectedTab) {
                WorkbenchRailTab.COLLECTIONS -> CollectionsRail(
                    state = state,
                    query = collectionQuery,
                    onQueryChange = onCollectionQueryChange,
                    expanded = expandedCollections,
                    onNewCollection = { showNewCollection = true },
                    onOpen = { id ->
                        if (state.isDraftDirty) savedRequestToOpen = id
                        else {
                            onDraftReplacement()
                            viewModel.openSavedRequest(id)
                        }
                    },
                    onDeleteRequest = { requestToDelete = it },
                    onDeleteCollection = { collectionToDelete = it }
                )
                WorkbenchRailTab.CONTRACT -> ContractRail(state, viewModel, onDraftReplacement)
                WorkbenchRailTab.HISTORY -> HistoryRail(
                    state = state,
                    onClear = { showClearHistoryConfirmation = true },
                    onOpen = { id ->
                        if (state.isDraftDirty || state.environmentDirty) historyToOpen = id
                        else {
                            onDraftReplacement()
                            viewModel.reopenHistory(id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CollectionsRail(
    state: WorkbenchState,
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: MutableMap<String, Boolean>,
    onNewCollection: () -> Unit,
    onOpen: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onDeleteCollection: (String) -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val normalizedQuery = query.trim().lowercase()
    val requestsByCollection = remember(state.savedWorkspace, normalizedQuery) {
        state.savedWorkspace.requests.asSequence()
            .filter { request ->
                normalizedQuery.isBlank() ||
                    request.name.lowercase().contains(normalizedQuery) ||
                    request.draft.url.lowercase().contains(normalizedQuery) ||
                    request.draft.method.name.lowercase().contains(normalizedQuery) ||
                    request.tags.any { it.lowercase().contains(normalizedQuery) }
            }
            .groupBy { it.collectionId }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        WorkbenchTextField(
            value = query,
            onValueChange = { onQueryChange(it.take(160)) },
            placeholder = strings.workbenchSearchRequests,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNewCollection, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = strings.workbenchNewCollection, tint = palette.accent)
        }
    }
    state.libraryErrorMessage?.let { message ->
        Text(
            message,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 3.dp)
                .workbenchErrorSemantics(message, LiveRegionMode.Polite),
            style = typography.caption.copy(color = palette.error),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    when {
        state.isLibraryLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = palette.accent, strokeWidth = 2.dp)
        }
        state.savedWorkspace.collections.isEmpty() -> EmptyRailMessage(strings.workbenchNoCollections, Modifier.fillMaxSize())
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            state.savedWorkspace.collections.forEach { collection ->
                val requests = requestsByCollection[collection.id].orEmpty()
                if (normalizedQuery.isBlank() || requests.isNotEmpty() || collection.name.lowercase().contains(normalizedQuery)) {
                    item(key = "collection:${collection.id}") {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(palette.surfaceVariant, RoundedCornerShape(7.dp))
                                .padding(horizontal = 3.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isExpanded = expanded[collection.id] ?: true
                            IconButton(
                                onClick = { expanded[collection.id] = !isExpanded },
                                modifier = Modifier.size(28.dp).semantics {
                                    stateDescription = if (isExpanded) strings.workbenchExpanded else strings.workbenchCollapsed
                                }
                            ) {
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                    contentDescription = collection.name,
                                    modifier = Modifier.size(17.dp),
                                    tint = palette.textSecondary
                                )
                            }
                            Icon(Icons.Default.Folder, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                collection.name,
                                modifier = Modifier.weight(1f),
                                style = typography.label.copy(color = palette.textPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(requests.size.toString(), style = typography.caption.copy(color = palette.textTertiary))
                            IconButton(
                                onClick = { onDeleteCollection(collection.id) },
                                enabled = !state.isLibrarySaving,
                                modifier = Modifier.size(27.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = strings.delete, tint = palette.textTertiary, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    if ((expanded[collection.id] ?: true) || normalizedQuery.isNotBlank()) {
                        items(requests, key = { "request:${it.id}" }) { request ->
                            SavedRequestRow(
                                request = request,
                                selected = state.activeSavedRequestId == request.id,
                                onOpen = { onOpen(request.id) },
                                onDelete = { onDeleteRequest(request.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContractRail(
    state: WorkbenchState,
    viewModel: WorkbenchViewModel,
    onDraftReplacement: () -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    var contractError by remember { mutableStateOf<String?>(null) }
    var pendingOperation by remember { mutableStateOf<WorkbenchOperationRef?>(null) }

    pendingOperation?.let { operation ->
        WorkbenchAlertDialog(
            onDismissRequest = { pendingOperation = null },
            title = { Text("${operation.method} ${operation.path}") },
            text = { Text(strings.workbenchDiscardChanges) },
            confirmButton = {
                TextButton(onClick = {
                    state.contract?.let {
                        onDraftReplacement()
                        viewModel.selectOperation(it, operation.path, operation.method)
                    }
                    pendingOperation = null
                }) { Text(strings.continueText) }
            },
            dismissButton = {
                TextButton(onClick = { pendingOperation = null }) { Text(strings.cancel) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeading(Icons.Default.Api, strings.workbenchContract)
        Box(modifier = Modifier.padding(horizontal = 10.dp)) {
            FilePickerButton(
                label = strings.workbenchLoadContract,
                selectedFile = state.contractSourceName ?: state.contract?.let { strings.workbenchContract },
                fileExtensions = listOf("json", "yaml", "yml"),
                onSelectionCleared = {
                    contractError = null
                    viewModel.setContract(null)
                },
                onFileSelected = { file ->
                    contractError = null
                    scope.launch {
                        val parsed = withContext(Dispatchers.IO) {
                            runCatching { SwaggerParser.parse(file.toPath()) }
                        }
                        parsed.onSuccess { viewModel.setContract(it, file.name) }
                            .onFailure { contractError = it.message ?: strings.workbenchContractParseFailed }
                    }
                }
            )
        }
        contractError?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    .workbenchErrorSemantics(it),
                style = typography.caption.copy(color = palette.error),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.operations.isEmpty()) {
            EmptyRailMessage(strings.workbenchLoadContract, Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(state.operations, key = { "${it.method}:${it.path}" }) { operation ->
                    val selected = state.selectedOperation == operation
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (selected) palette.sidebarSelected else Color.Transparent, RoundedCornerShape(7.dp))
                            .semantics {
                                role = Role.Button
                                this.selected = selected
                                contentDescription = "${operation.method} ${operation.path}"
                            }
                            .clickable {
                                if (state.isDraftDirty) pendingOperation = operation
                                else state.contract?.let {
                                    onDraftReplacement()
                                    viewModel.selectOperation(it, operation.path, operation.method)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MethodBadge(operation.method)
                        Spacer(Modifier.width(7.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                operation.path,
                                style = typography.codeSmall.copy(color = palette.textPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRail(state: WorkbenchState, onClear: () -> Unit, onOpen: (String) -> Unit) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) { SectionHeading(Icons.Default.History, strings.workbenchHistory) }
            if (state.history.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = strings.clear, tint = palette.textTertiary, modifier = Modifier.size(17.dp))
                }
            }
        }
        when {
            state.isHistoryLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = palette.accent, strokeWidth = 2.dp)
            }
            state.history.isEmpty() -> EmptyRailMessage(strings.workbenchNoHistory, Modifier.fillMaxSize())
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(state.history, key = { it.id }) { entry ->
                    HistoryRow(entry = entry, onClick = { onOpen(entry.id) })
                }
            }
        }
    }
}

@Composable
private fun RequestEditor(
    state: WorkbenchState,
    viewModel: WorkbenchViewModel,
    selectedTab: RequestEditorTab,
    onSelectTab: (RequestEditorTab) -> Unit,
    onSaveRequest: () -> Unit,
    onSaveRequestAs: () -> Unit,
    inputsValid: Boolean,
    numericDraftIdentity: Long,
    numericFieldBuffers: Map<WorkbenchNumericFieldKey, WorkbenchNumericFieldBuffer>,
    onNumericBufferChange: (WorkbenchNumericFieldSpec, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val timeoutSpec = workbenchRequestTimeoutSpec(state.draft.timeoutMillis, numericDraftIdentity)
    val timeoutText = workbenchNumericFieldText(numericFieldBuffers[timeoutSpec.key], timeoutSpec)
    val timeoutInvalid = !isWorkbenchNumericFieldValid(numericFieldBuffers[timeoutSpec.key], timeoutSpec)
    val timeoutRangeMessage = "100–300000 ms"

    fun commitTimeout() {
        val parsed = parseWorkbenchBoundedLong(timeoutText, minimum = 100L, maximum = 300_000L)
        if (parsed != null && parsed != state.draft.timeoutMillis) {
            viewModel.updateDraft { it.copy(timeoutMillis = parsed) }
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimensions.cardRadius),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SectionHeading(Icons.Default.EditNote, strings.workbenchRequest)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MethodPicker(
                    method = state.draft.method,
                    onSelect = { method -> viewModel.updateDraft { it.copy(method = method) } }
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    WorkbenchTextField(
                        value = state.draft.url,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(url = value) } },
                        placeholder = "https://api.example.com/v1/resource",
                        modifier = Modifier.weight(1f)
                    )
                }
                if (state.isSending) {
                    FilledTonalButton(
                        onClick = viewModel::cancelRequest,
                        modifier = Modifier.height(50.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = palette.error.copy(alpha = 0.14f),
                            contentColor = palette.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(strings.cancel)
                    }
                } else {
                    Button(
                        onClick = viewModel::send,
                        enabled = state.draft.url.isNotBlank() &&
                            inputsValid &&
                            state.requiredSecretRestorations.isEmpty() &&
                            !state.replayRequiresFullRequest &&
                            !state.isEnvironmentLoading && !state.isEnvironmentSaving,
                        modifier = Modifier.height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.onAccent
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(strings.workbenchSend)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WorkbenchTextField(
                    value = state.draft.name,
                    onValueChange = { value -> viewModel.updateDraft { it.copy(name = value.take(120)) } },
                    placeholder = strings.workbenchRequest,
                    modifier = Modifier.weight(1f)
                )
                if (state.isDraftDirty) {
                    Box(
                        modifier = Modifier.size(8.dp)
                            .background(palette.warning, RoundedCornerShape(50))
                            .semantics { contentDescription = strings.workbenchUnsavedRequestChanges }
                    )
                }
                IconButton(
                    onClick = onSaveRequest,
                    enabled = !state.isLibrarySaving,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = strings.workbenchSaveRequest, tint = palette.accent, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onSaveRequestAs,
                    enabled = !state.isLibrarySaving,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.SaveAs, contentDescription = strings.workbenchSaveRequestAs, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
                WorkbenchTextField(
                    value = timeoutText,
                    onValueChange = { value ->
                        val filtered = value.filter(Char::isDigit).take(6)
                        onNumericBufferChange(timeoutSpec, filtered)
                        parseWorkbenchBoundedLong(filtered, minimum = 100L, maximum = 300_000L)
                            ?.takeIf { it != state.draft.timeoutMillis }
                            ?.let { timeout -> viewModel.updateDraft { it.copy(timeoutMillis = timeout) } }
                    },
                    placeholder = strings.workbenchTimeout,
                    modifier = Modifier
                        .width(112.dp)
                        .onFocusChanged { if (!it.isFocused) commitTimeout() }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                commitTimeout()
                                true
                            } else {
                                false
                            }
                        },
                    technical = true
                )
            }
            if (timeoutInvalid) {
                Text(
                    timeoutRangeMessage,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .workbenchErrorSemantics(timeoutRangeMessage),
                    style = typography.caption.copy(color = palette.error),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }

            ScrollableWorkbenchTabs(
                tabs = RequestEditorTab.entries,
                selected = selectedTab,
                label = {
                    when (it) {
                        RequestEditorTab.PARAMS -> strings.workbenchParams
                        RequestEditorTab.HEADERS -> strings.headers
                        RequestEditorTab.BODY -> strings.workbenchBody
                        RequestEditorTab.AUTH -> strings.workbenchAuth
                        RequestEditorTab.VARIABLES -> strings.variables
                        RequestEditorTab.ASSERTIONS -> strings.workbenchAssertions
                    }
                },
                onSelect = onSelectTab
            )
            HorizontalDivider(color = palette.divider)

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                when (selectedTab) {
                    RequestEditorTab.PARAMS -> RequestFieldsEditor(
                        fields = state.draft.query,
                        onChange = { fields -> viewModel.updateDraft { it.copy(query = fields) } }
                    )
                    RequestEditorTab.HEADERS -> RequestFieldsEditor(
                        fields = state.draft.headers,
                        onChange = { fields -> viewModel.updateDraft { it.copy(headers = fields) } }
                    )
                    RequestEditorTab.BODY -> BodyEditor(state.draft, viewModel)
                    RequestEditorTab.AUTH -> AuthEditor(
                        auth = state.draft.auth,
                        onChange = { auth -> viewModel.updateDraft { it.copy(auth = auth) } }
                    )
                    RequestEditorTab.VARIABLES -> VariablesEditor(state, viewModel)
                    RequestEditorTab.ASSERTIONS -> AssertionsEditor(
                        assertions = state.assertions,
                        viewModel = viewModel,
                        numericDraftIdentity = numericDraftIdentity,
                        numericFieldBuffers = numericFieldBuffers,
                        onNumericBufferChange = onNumericBufferChange
                    )
                }
            }

            val errorText = state.executionFailure?.message
                ?: state.errorMessage
                ?: state.variableErrors.firstOrNull()?.let { formatVariableError(it, strings) }
                ?: state.libraryErrorMessage
                ?: state.environmentErrorMessage
            if (errorText != null) {
                HorizontalDivider(color = palette.divider)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(palette.error.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .workbenchErrorSemantics(errorText),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = palette.error, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(errorText, style = typography.bodySmall.copy(color = palette.error), maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun ResponseViewer(
    state: WorkbenchState,
    selectedTab: ResponseViewerTab,
    onSelectTab: (ResponseViewerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimensions.cardRadius),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SectionHeading(Icons.Default.Terminal, strings.workbenchResponse)
            ExecutionRibbon(state)
            HorizontalDivider(color = palette.divider)

            val response = state.response
            when {
                state.isSending -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(strings.workbenchInFlight, style = typography.bodySmall.copy(color = palette.textSecondary))
                    }
                }
                response == null -> Box(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (state.executionFailure == null) Icons.Default.DataObject else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (state.executionFailure == null) palette.textTertiary else palette.error,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            state.executionFailure?.message ?: strings.workbenchNoResponse,
                            modifier = state.executionFailure?.message?.let {
                                Modifier.workbenchErrorSemantics(it)
                            } ?: Modifier,
                            style = typography.bodySmall.copy(
                                color = if (state.executionFailure == null) palette.textSecondary else palette.error
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> {
                    ResponseMeta(response)
                    val bodyPreviewLimited = response.previewTruncated ||
                        response.body.length > MAX_BODY_PREVIEW_CHARS
                    if (response.truncated || bodyPreviewLimited) {
                        Text(
                            if (response.truncated) strings.workbenchTruncated else strings.workbenchPreviewLimited,
                            modifier = Modifier.fillMaxWidth()
                                .background(palette.warning.copy(alpha = 0.09f))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            style = typography.caption.copy(color = palette.warning)
                        )
                    }
                    TabStrip(
                        tabs = ResponseViewerTab.entries,
                        selected = selectedTab,
                        label = {
                            when (it) {
                                ResponseViewerTab.BODY -> strings.workbenchBody
                                ResponseViewerTab.HEADERS -> strings.headers
                                ResponseViewerTab.CHECKS -> strings.workbenchChecks
                            }
                        },
                        onSelect = onSelectTab
                    )
                    HorizontalDivider(color = palette.divider)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                        when (selectedTab) {
                            ResponseViewerTab.BODY -> CodeViewer(response.body)
                            ResponseViewerTab.HEADERS -> HeaderViewer(response.headers)
                            ResponseViewerTab.CHECKS -> ChecksViewer(state)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionRibbon(state: WorkbenchState) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val lifecycle = state.lifecycle
    val executionFinished = lifecycle.outcome != WorkbenchExecutionOutcome.NONE
    val stages = listOf(
        strings.workbenchReady to if (
            lifecycle.preparedAtEpochMillis != null || state.draft.url.isNotBlank()
        ) RibbonState.SUCCESS else RibbonState.IDLE,
        strings.workbenchInFlight to when {
            lifecycle.phase == WorkbenchExecutionPhase.RESOLVING ||
                lifecycle.phase == WorkbenchExecutionPhase.IN_FLIGHT -> RibbonState.ACTIVE
            lifecycle.outcome == WorkbenchExecutionOutcome.FAILED ||
                lifecycle.outcome == WorkbenchExecutionOutcome.CANCELLED -> RibbonState.ERROR
            lifecycle.inFlightAtEpochMillis != null && executionFinished -> RibbonState.SUCCESS
            else -> RibbonState.IDLE
        },
        strings.workbenchReceived to when {
            lifecycle.responseReceivedAtEpochMillis != null -> RibbonState.SUCCESS
            lifecycle.outcome == WorkbenchExecutionOutcome.FAILED ||
                lifecycle.outcome == WorkbenchExecutionOutcome.CANCELLED -> RibbonState.ERROR
            else -> RibbonState.IDLE
        },
        strings.workbenchContract to when (lifecycle.contractValidation) {
            WorkbenchContractValidationState.NOT_REQUESTED -> RibbonState.IDLE
            WorkbenchContractValidationState.PENDING -> RibbonState.ACTIVE
            WorkbenchContractValidationState.COMPLETED ->
                if (lifecycle.contractPassed == true) RibbonState.SUCCESS else RibbonState.ERROR
            WorkbenchContractValidationState.FAILED -> RibbonState.ERROR
        },
        strings.workbenchAssertions to when (lifecycle.assertionEvaluation) {
            WorkbenchAssertionEvaluationState.NOT_REQUESTED -> RibbonState.IDLE
            WorkbenchAssertionEvaluationState.PENDING -> RibbonState.ACTIVE
            WorkbenchAssertionEvaluationState.COMPLETED -> when (lifecycle.assertionsPassed) {
                true -> RibbonState.SUCCESS
                false -> RibbonState.ERROR
                null -> RibbonState.IDLE
            }
            WorkbenchAssertionEvaluationState.FAILED -> RibbonState.ERROR
        },
        strings.workbenchSaved to when (lifecycle.historyPersistence) {
            WorkbenchHistoryPersistenceState.NOT_ATTEMPTED -> RibbonState.IDLE
            WorkbenchHistoryPersistenceState.PENDING -> RibbonState.ACTIVE
            WorkbenchHistoryPersistenceState.SAVED -> RibbonState.SUCCESS
            WorkbenchHistoryPersistenceState.FAILED -> RibbonState.ERROR
        }
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(
            strings.workbenchFlightRecorder.uppercase(),
            style = typography.caption.copy(color = palette.textTertiary, letterSpacing = 0.8.sp)
        )
        Spacer(Modifier.height(7.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            stages.forEach { (label, stage) ->
                val color = when (stage) {
                    RibbonState.IDLE -> palette.textTertiary
                    RibbonState.ACTIVE -> palette.info
                    RibbonState.SUCCESS -> palette.success
                    RibbonState.ERROR -> palette.error
                }
                val accessibleState = when (stage) {
                    RibbonState.IDLE -> strings.stopped
                    RibbonState.ACTIVE -> strings.running
                    RibbonState.SUCCESS -> strings.success
                    RibbonState.ERROR -> strings.failed
                }
                Column(
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = label
                        stateDescription = accessibleState
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(3.dp)
                            .background(color.copy(alpha = if (stage == RibbonState.IDLE) 0.24f else 1f), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        style = typography.caption.copy(color = color, fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestFieldsEditor(
    fields: List<WorkbenchRequestField>,
    onChange: (List<WorkbenchRequestField>) -> Unit
) {
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 3.dp)) {
            Text(strings.workbenchKey, modifier = Modifier.weight(0.9f), style = LocalAppTypography.current.caption, color = LocalAppPalette.current.textTertiary)
            Spacer(Modifier.width(7.dp))
            Text(strings.workbenchValue, modifier = Modifier.weight(1.1f), style = LocalAppTypography.current.caption, color = LocalAppPalette.current.textTertiary)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(fields) { index, field ->
                RequestFieldRow(
                    field = field,
                    onChange = { changed -> onChange(fields.toMutableList().also { it[index] = changed }) },
                    onDelete = { onChange(fields.toMutableList().also { it.removeAt(index) }) }
                )
            }
        }
        TextButton(
            onClick = { onChange(fields + WorkbenchRequestField()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(strings.workbenchAddRow)
        }
    }
}

@Composable
private fun RequestFieldRow(
    field: WorkbenchRequestField,
    onChange: (WorkbenchRequestField) -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Checkbox(
            checked = field.enabled,
            onCheckedChange = { onChange(field.copy(enabled = it)) },
            modifier = Modifier.size(28.dp)
        )
        WorkbenchTextField(
            value = field.key,
            onValueChange = { onChange(field.copy(key = it)) },
            placeholder = LocalAppStrings.current.workbenchKey,
            modifier = Modifier.weight(0.9f),
            technical = true
        )
        WorkbenchTextField(
            value = field.value,
            onValueChange = { onChange(field.copy(value = it)) },
            placeholder = LocalAppStrings.current.workbenchValue,
            modifier = Modifier.weight(1.1f),
            secret = field.sensitive,
            technical = true
        )
        IconToggleButton(
            checked = field.sensitive,
            onCheckedChange = { onChange(field.copy(sensitive = it)) },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                if (field.sensitive) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = LocalAppStrings.current.workbenchSecret,
                tint = if (field.sensitive) palette.warning else palette.textTertiary,
                modifier = Modifier.size(15.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = LocalAppStrings.current.remove, tint = palette.textTertiary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun BodyEditor(draft: WorkbenchRequestDraft, viewModel: WorkbenchViewModel) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = draft.bodyEnabled,
                onCheckedChange = { enabled -> viewModel.updateDraft { it.copy(bodyEnabled = enabled) } }
            )
            Text(strings.requestBody, style = typography.bodySmall.copy(color = palette.textSecondary))
            Spacer(Modifier.weight(1f))
            IconToggleButton(
                checked = draft.bodySensitive,
                onCheckedChange = { sensitive ->
                    viewModel.updateDraft { it.copy(bodySensitive = sensitive) }
                },
                modifier = Modifier.semantics {
                    contentDescription = strings.workbenchSecret
                    stateDescription = if (draft.bodySensitive) strings.workbenchSecret else strings.none
                }
            ) {
                Icon(
                    if (draft.bodySensitive) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (draft.bodySensitive) palette.warning else palette.textTertiary
                )
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = draft.body,
                onValueChange = { value -> viewModel.updateDraft { it.copy(body = value) } },
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = strings.requestBody
                    if (draft.bodySensitive) password()
                },
                enabled = draft.bodyEnabled,
                textStyle = typography.code.copy(color = palette.textPrimary),
                visualTransformation = if (draft.bodySensitive) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                colors = workbenchFieldColors(),
                shape = RoundedCornerShape(AppDimensions.inputRadius)
            )
        }
    }
}

@Composable
private fun AuthEditor(auth: WorkbenchAuth, onChange: (WorkbenchAuth) -> Unit) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    var expanded by remember { mutableStateOf(false) }
    val kind = when (auth) {
        WorkbenchAuth.None -> strings.workbenchAuthNone
        is WorkbenchAuth.Bearer -> strings.workbenchAuthBearer
        is WorkbenchAuth.Basic -> strings.workbenchAuthBasic
        is WorkbenchAuth.ApiKey -> strings.workbenchAuthApiKey
    }
    val options = listOf(
        strings.workbenchAuthNone to WorkbenchAuth.None,
        strings.workbenchAuthBearer to WorkbenchAuth.Bearer(),
        strings.workbenchAuthBasic to WorkbenchAuth.Basic(),
        strings.workbenchAuthApiKey to WorkbenchAuth.ApiKey()
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(strings.workbenchAuth, style = typography.label.copy(color = palette.textSecondary))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.width(180.dp)) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(kind, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { (label, option) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                expanded = false
                                onChange(option)
                            }
                        )
                    }
                }
            }
        }

        when (auth) {
            WorkbenchAuth.None -> Text(strings.workbenchAuthNone, style = typography.bodySmall.copy(color = palette.textTertiary))
            is WorkbenchAuth.Bearer -> SecretField(
                value = auth.token,
                onValueChange = { onChange(auth.copy(token = it)) },
                placeholder = strings.workbenchBearerToken
            )
            is WorkbenchAuth.Basic -> {
                WorkbenchTextField(
                    value = auth.username,
                    onValueChange = { onChange(auth.copy(username = it)) },
                    placeholder = strings.workbenchUsername,
                    modifier = Modifier.fillMaxWidth(),
                    technical = true
                )
                SecretField(
                    value = auth.password,
                    onValueChange = { onChange(auth.copy(password = it)) },
                    placeholder = strings.workbenchPassword
                )
            }
            is WorkbenchAuth.ApiKey -> {
                WorkbenchTextField(
                    value = auth.name,
                    onValueChange = { onChange(auth.copy(name = it)) },
                    placeholder = strings.workbenchApiKeyHeaderName,
                    modifier = Modifier.fillMaxWidth(),
                    technical = true
                )
                SecretField(
                    value = auth.value,
                    onValueChange = { onChange(auth.copy(value = it)) },
                    placeholder = strings.workbenchApiKeyValue
                )
                ApiKeyLocationPicker(auth.location) { onChange(auth.copy(location = it)) }
            }
        }
    }
}

@Composable
private fun VariablesEditor(state: WorkbenchState, viewModel: WorkbenchViewModel) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    var newKey by remember { mutableStateOf("") }
    val variables = state.variables
    val activeEnvironment = state.environmentSnapshot.activeEnvironment
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            color = if (activeEnvironment == null) palette.info.copy(alpha = 0.08f) else palette.surfaceVariant,
            shape = RoundedCornerShape(7.dp),
            border = BorderStroke(1.dp, if (state.environmentDirty) palette.warning.copy(alpha = 0.5f) else palette.borderSubtle)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (activeEnvironment == null) Icons.Default.EditNote else Icons.Default.Dns,
                    contentDescription = null,
                    tint = if (activeEnvironment == null) palette.info else palette.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (activeEnvironment == null) "${strings.workbenchScratch} · ${strings.workbenchSessionVariables}"
                    else "${strings.workbenchEnvironment}: ${activeEnvironment.name}",
                    modifier = Modifier.weight(1f),
                    style = LocalAppTypography.current.caption.copy(color = palette.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (activeEnvironment != null && state.environmentDirty) {
                    TextButton(onClick = viewModel::discardEnvironmentChanges, contentPadding = PaddingValues(5.dp)) {
                        Text(strings.cancel, style = LocalAppTypography.current.caption)
                    }
                    TextButton(onClick = { viewModel.saveEnvironment() }, contentPadding = PaddingValues(5.dp)) {
                        Text(strings.save, style = LocalAppTypography.current.caption, color = palette.warning)
                    }
                }
            }
        }
        state.environmentErrorMessage?.let { message ->
            Text(
                message,
                modifier = Modifier.fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .workbenchErrorSemantics(message, LiveRegionMode.Polite),
                style = LocalAppTypography.current.caption.copy(color = palette.error),
                maxLines = 2
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(variables.entries.sortedBy { it.key }, key = { it.key }) { (name, variable) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Checkbox(
                        checked = variable.enabled,
                        onCheckedChange = { viewModel.updateVariable(name, variable.copy(enabled = it)) },
                        modifier = Modifier.size(28.dp)
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            name,
                            modifier = Modifier.weight(0.8f),
                            style = LocalAppTypography.current.codeSmall.copy(color = palette.textPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        WorkbenchTextField(
                            value = variable.value,
                            onValueChange = { viewModel.updateVariable(name, variable.copy(value = it)) },
                            placeholder = strings.workbenchValue,
                            modifier = Modifier.weight(1.2f),
                            secret = variable.secret
                        )
                    }
                    IconToggleButton(
                        checked = variable.secret,
                        onCheckedChange = { viewModel.updateVariable(name, variable.copy(secret = it)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (variable.secret) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = strings.workbenchSecret,
                            tint = if (variable.secret) palette.warning else palette.textTertiary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.updateVariable(name, null) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = strings.remove, tint = palette.textTertiary, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkbenchTextField(
                value = newKey,
                onValueChange = { newKey = it.trim().take(80) },
                placeholder = strings.workbenchKey,
                modifier = Modifier.weight(1f),
                technical = true
            )
            FilledTonalButton(
                onClick = {
                    if (newKey.isNotBlank()) {
                        viewModel.updateVariable(newKey, WorkbenchVariable())
                        newKey = ""
                    }
                },
                enabled = newKey.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.workbenchAddRow)
            }
        }
    }
}

@Composable
private fun AssertionsEditor(
    assertions: List<WorkbenchAssertion>,
    viewModel: WorkbenchViewModel,
    numericDraftIdentity: Long,
    numericFieldBuffers: Map<WorkbenchNumericFieldKey, WorkbenchNumericFieldBuffer>,
    onNumericBufferChange: (WorkbenchNumericFieldSpec, String) -> Unit
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    var addMenuExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.workbenchAssertions, style = LocalAppTypography.current.label.copy(color = palette.textPrimary))
                Text(
                    strings.workbenchSafeJsonPathHint,
                    style = LocalAppTypography.current.caption.copy(color = palette.textTertiary)
                )
            }
            Box {
                FilledTonalButton(
                    onClick = { addMenuExpanded = true },
                    enabled = assertions.size < 100
                ) {
                    Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(strings.workbenchAddAssertion)
                }
                DropdownMenu(expanded = addMenuExpanded, onDismissRequest = { addMenuExpanded = false }) {
                    AssertionKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(assertionKindLabel(kind, strings)) },
                            onClick = {
                                addMenuExpanded = false
                                viewModel.addAssertion(newAssertion(kind))
                            }
                        )
                    }
                }
            }
        }
        if (assertions.isEmpty()) {
            EmptyPanel(strings.workbenchNoAssertions, Icons.AutoMirrored.Filled.FactCheck)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 6.dp)
            ) {
                items(assertions, key = { it.id }) { assertion ->
                    AssertionCard(
                        assertion = assertion,
                        onChange = { viewModel.updateAssertion(assertion.id, it) },
                        onDelete = { viewModel.removeAssertion(assertion.id) },
                        numericDraftIdentity = numericDraftIdentity,
                        numericFieldBuffers = numericFieldBuffers,
                        onNumericBufferChange = onNumericBufferChange
                    )
                }
            }
        }
    }
}

@Composable
private fun AssertionCard(
    assertion: WorkbenchAssertion,
    onChange: (WorkbenchAssertion) -> Unit,
    onDelete: () -> Unit,
    numericDraftIdentity: Long,
    numericFieldBuffers: Map<WorkbenchNumericFieldKey, WorkbenchNumericFieldBuffer>,
    onNumericBufferChange: (WorkbenchNumericFieldSpec, String) -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val numericSpecs = workbenchAssertionNumericFieldSpecs(listOf(assertion), numericDraftIdentity)
    fun numericSpec(fieldId: String): WorkbenchNumericFieldSpec =
        numericSpecs.first { it.key.fieldId == fieldId }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = palette.surfaceVariant.copy(alpha = if (assertion.enabled) 1f else 0.55f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, palette.borderSubtle)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = assertion.enabled,
                    onCheckedChange = { onChange(assertion.withEnabled(it)) },
                    modifier = Modifier.size(28.dp)
                )
                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    assertionLabel(assertion, strings),
                    modifier = Modifier.weight(1f),
                    style = typography.label.copy(color = palette.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = LocalAppStrings.current.remove, tint = palette.textTertiary, modifier = Modifier.size(15.dp))
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                when (assertion) {
                    is WorkbenchAssertion.StatusEquals -> {
                        val spec = numericSpec("statusEquals.expected")
                        AssertionNumberField(
                            spec = spec,
                            text = workbenchNumericFieldText(numericFieldBuffers[spec.key], spec),
                            placeholder = strings.workbenchHttpStatus,
                            onTextChange = { onNumericBufferChange(spec, it) },
                            onCommit = { onChange(assertion.copy(expected = it.toInt())) }
                        )
                    }
                    is WorkbenchAssertion.StatusRange -> {
                        val minimumSpec = numericSpec("statusRange.minimum")
                        val maximumSpec = numericSpec("statusRange.maximum")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssertionNumberField(
                                spec = minimumSpec,
                                text = workbenchNumericFieldText(
                                    numericFieldBuffers[minimumSpec.key],
                                    minimumSpec
                                ),
                                placeholder = strings.workbenchMinimum,
                                modifier = Modifier.weight(1f),
                                onTextChange = { onNumericBufferChange(minimumSpec, it) },
                                onCommit = { onChange(assertion.copy(minimum = it.toInt())) }
                            )
                            AssertionNumberField(
                                spec = maximumSpec,
                                text = workbenchNumericFieldText(
                                    numericFieldBuffers[maximumSpec.key],
                                    maximumSpec
                                ),
                                placeholder = strings.workbenchMaximum,
                                modifier = Modifier.weight(1f),
                                onTextChange = { onNumericBufferChange(maximumSpec, it) },
                                onCommit = { onChange(assertion.copy(maximum = it.toInt())) }
                            )
                        }
                    }
                    is WorkbenchAssertion.ResponseTimeMax -> {
                        val spec = numericSpec("responseTimeMax.maximumMillis")
                        AssertionNumberField(
                            spec = spec,
                            text = workbenchNumericFieldText(numericFieldBuffers[spec.key], spec),
                            placeholder = strings.workbenchMaximumMillis,
                            onTextChange = { onNumericBufferChange(spec, it) },
                            onCommit = { onChange(assertion.copy(maximumMillis = it)) }
                        )
                    }
                    is WorkbenchAssertion.HeaderExists -> WorkbenchTextField(
                        value = assertion.name,
                        onValueChange = { onChange(assertion.copy(name = it.take(256))) },
                        placeholder = strings.workbenchHeaderName,
                        modifier = Modifier.fillMaxWidth()
                    )
                    is WorkbenchAssertion.HeaderEquals -> AssertionHeaderFields(
                        name = assertion.name,
                        expected = assertion.expected,
                        ignoreCase = assertion.ignoreCase,
                        sensitive = assertion.sensitive,
                        onName = { onChange(assertion.copy(name = it.take(256))) },
                        onExpected = { onChange(assertion.copy(expected = it.take(16_384))) },
                        onIgnoreCase = { onChange(assertion.copy(ignoreCase = it)) },
                        onSensitive = { onChange(assertion.copy(sensitive = it)) }
                    )
                    is WorkbenchAssertion.HeaderContains -> AssertionHeaderFields(
                        name = assertion.name,
                        expected = assertion.expected,
                        ignoreCase = assertion.ignoreCase,
                        sensitive = assertion.sensitive,
                        onName = { onChange(assertion.copy(name = it.take(256))) },
                        onExpected = { onChange(assertion.copy(expected = it.take(16_384))) },
                        onIgnoreCase = { onChange(assertion.copy(ignoreCase = it)) },
                        onSensitive = { onChange(assertion.copy(sensitive = it)) }
                    )
                    is WorkbenchAssertion.BodyContains -> {
                        WorkbenchTextField(
                            value = assertion.expected,
                            onValueChange = { onChange(assertion.copy(expected = it.take(16_384))) },
                            placeholder = LocalAppStrings.current.workbenchExpected,
                            modifier = Modifier.fillMaxWidth(),
                            secret = assertion.sensitive
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IgnoreCaseToggle(assertion.ignoreCase) { onChange(assertion.copy(ignoreCase = it)) }
                            Spacer(Modifier.weight(1f))
                            SensitiveOperandToggle(assertion.sensitive) { onChange(assertion.copy(sensitive = it)) }
                        }
                    }
                    is WorkbenchAssertion.JsonPathExists -> WorkbenchTextField(
                        value = assertion.path,
                        onValueChange = { onChange(assertion.copy(path = it.take(2_048))) },
                        placeholder = "$.data.id",
                        modifier = Modifier.fillMaxWidth()
                    )
                    is WorkbenchAssertion.JsonPathEquals -> {
                        WorkbenchTextField(
                            value = assertion.path,
                            onValueChange = { onChange(assertion.copy(path = it.take(2_048))) },
                            placeholder = "$.data.status",
                            modifier = Modifier.fillMaxWidth()
                        )
                        WorkbenchTextField(
                            value = assertion.expectedJson,
                            onValueChange = { onChange(assertion.copy(expectedJson = it.take(16_384))) },
                            placeholder = strings.workbenchJsonLiteralHint,
                            modifier = Modifier.fillMaxWidth(),
                            secret = assertion.sensitive
                        )
                        SensitiveOperandToggle(assertion.sensitive) { onChange(assertion.copy(sensitive = it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssertionHeaderFields(
    name: String,
    expected: String,
    ignoreCase: Boolean,
    sensitive: Boolean,
    onName: (String) -> Unit,
    onExpected: (String) -> Unit,
    onIgnoreCase: (Boolean) -> Unit,
    onSensitive: (Boolean) -> Unit
) {
    val strings = LocalAppStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WorkbenchTextField(name, onName, strings.workbenchHeaderName, Modifier.weight(0.8f))
        WorkbenchTextField(
            expected,
            onExpected,
            LocalAppStrings.current.workbenchExpected,
            Modifier.weight(1.2f),
            secret = sensitive
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IgnoreCaseToggle(ignoreCase, onIgnoreCase)
        Spacer(Modifier.weight(1f))
        SensitiveOperandToggle(sensitive, onSensitive)
    }
}

@Composable
private fun IgnoreCaseToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, modifier = Modifier.size(28.dp))
        Text(
            LocalAppStrings.current.workbenchIgnoreCase,
            style = LocalAppTypography.current.caption.copy(color = LocalAppPalette.current.textSecondary)
        )
    }
}

@Composable
private fun SensitiveOperandToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalAppPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconToggleButton(checked = checked, onCheckedChange = onChange, modifier = Modifier.size(28.dp)) {
            Icon(
                if (checked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = LocalAppStrings.current.workbenchSecret,
                tint = if (checked) palette.warning else palette.textTertiary,
                modifier = Modifier.size(15.dp)
            )
        }
        Text(
            LocalAppStrings.current.workbenchSecret,
            style = LocalAppTypography.current.caption.copy(color = palette.textSecondary)
        )
    }
}

@Composable
private fun AssertionNumberField(
    spec: WorkbenchNumericFieldSpec,
    text: String,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onTextChange: (String) -> Unit,
    onCommit: (Long) -> Unit
) {
    val invalid = parseWorkbenchBoundedLong(text, spec.minimum, spec.maximum) == null
    val rangeMessage = "${spec.minimum}–${spec.maximum}"
    fun commit() {
        val parsed = parseWorkbenchBoundedLong(text, spec.minimum, spec.maximum)
        if (parsed != null && parsed != spec.committedValue) onCommit(parsed)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { valueText ->
            val filtered = valueText.filter(Char::isDigit).take(9)
            onTextChange(filtered)
            parseWorkbenchBoundedLong(filtered, spec.minimum, spec.maximum)
                ?.takeIf { it != spec.committedValue }
                ?.let(onCommit)
        },
        modifier = modifier
            .semantics { contentDescription = placeholder }
            .onFocusChanged { if (!it.isFocused) commit() }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                    commit()
                    true
                } else false
            }
            .then(if (invalid) Modifier.workbenchErrorSemantics(rangeMessage) else Modifier),
        singleLine = true,
        isError = invalid,
        supportingText = if (invalid) ({ Text(rangeMessage) }) else null,
        placeholder = { Text(placeholder, style = LocalAppTypography.current.codeSmall) },
        textStyle = LocalAppTypography.current.codeSmall.copy(color = LocalAppPalette.current.textPrimary),
        colors = workbenchFieldColors(),
        shape = RoundedCornerShape(AppDimensions.inputRadius)
    )
}

private fun newAssertion(kind: AssertionKind): WorkbenchAssertion {
    val id = java.util.UUID.randomUUID().toString()
    return when (kind) {
        AssertionKind.STATUS_EQUALS -> WorkbenchAssertion.StatusEquals(id = id)
        AssertionKind.STATUS_RANGE -> WorkbenchAssertion.StatusRange(id = id)
        AssertionKind.RESPONSE_TIME -> WorkbenchAssertion.ResponseTimeMax(id = id)
        AssertionKind.HEADER_EXISTS -> WorkbenchAssertion.HeaderExists(id = id)
        AssertionKind.HEADER_EQUALS -> WorkbenchAssertion.HeaderEquals(id = id)
        AssertionKind.HEADER_CONTAINS -> WorkbenchAssertion.HeaderContains(id = id)
        AssertionKind.BODY_CONTAINS -> WorkbenchAssertion.BodyContains(id = id)
        AssertionKind.JSON_PATH_EXISTS -> WorkbenchAssertion.JsonPathExists(id = id)
        AssertionKind.JSON_PATH_EQUALS -> WorkbenchAssertion.JsonPathEquals(id = id)
    }
}

private fun WorkbenchAssertion.withEnabled(enabled: Boolean): WorkbenchAssertion = when (this) {
    is WorkbenchAssertion.StatusEquals -> copy(enabled = enabled)
    is WorkbenchAssertion.StatusRange -> copy(enabled = enabled)
    is WorkbenchAssertion.ResponseTimeMax -> copy(enabled = enabled)
    is WorkbenchAssertion.HeaderExists -> copy(enabled = enabled)
    is WorkbenchAssertion.HeaderEquals -> copy(enabled = enabled)
    is WorkbenchAssertion.HeaderContains -> copy(enabled = enabled)
    is WorkbenchAssertion.BodyContains -> copy(enabled = enabled)
    is WorkbenchAssertion.JsonPathExists -> copy(enabled = enabled)
    is WorkbenchAssertion.JsonPathEquals -> copy(enabled = enabled)
}

private fun assertionLabel(assertion: WorkbenchAssertion, strings: AppStrings): String = assertionKindLabel(
    when (assertion) {
        is WorkbenchAssertion.StatusEquals -> AssertionKind.STATUS_EQUALS
        is WorkbenchAssertion.StatusRange -> AssertionKind.STATUS_RANGE
        is WorkbenchAssertion.ResponseTimeMax -> AssertionKind.RESPONSE_TIME
        is WorkbenchAssertion.HeaderExists -> AssertionKind.HEADER_EXISTS
        is WorkbenchAssertion.HeaderEquals -> AssertionKind.HEADER_EQUALS
        is WorkbenchAssertion.HeaderContains -> AssertionKind.HEADER_CONTAINS
        is WorkbenchAssertion.BodyContains -> AssertionKind.BODY_CONTAINS
        is WorkbenchAssertion.JsonPathExists -> AssertionKind.JSON_PATH_EXISTS
        is WorkbenchAssertion.JsonPathEquals -> AssertionKind.JSON_PATH_EQUALS
    },
    strings
)

private fun assertionKindLabel(kind: AssertionKind, strings: AppStrings): String = when (kind) {
    AssertionKind.STATUS_EQUALS -> strings.workbenchAssertionStatusEquals
    AssertionKind.STATUS_RANGE -> strings.workbenchAssertionStatusRange
    AssertionKind.RESPONSE_TIME -> strings.workbenchAssertionResponseTime
    AssertionKind.HEADER_EXISTS -> strings.workbenchAssertionHeaderExists
    AssertionKind.HEADER_EQUALS -> strings.workbenchAssertionHeaderEquals
    AssertionKind.HEADER_CONTAINS -> strings.workbenchAssertionHeaderContains
    AssertionKind.BODY_CONTAINS -> strings.workbenchAssertionBodyContains
    AssertionKind.JSON_PATH_EXISTS -> strings.workbenchAssertionJsonPathExists
    AssertionKind.JSON_PATH_EQUALS -> strings.workbenchAssertionJsonPathEquals
}

@Composable
private fun ResponseMeta(response: WorkbenchResponse) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val statusColor = when (response.statusCode) {
        in 200..399 -> palette.success
        in 400..499 -> palette.warning
        in 500..599 -> palette.error
        else -> palette.info
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val status = "${response.statusCode} ${response.statusText}".trim()
        val size = humanBytes(response.receivedBytes)
        MetaChip(status, statusColor, strings.workbenchResponseStatusLabel(status))
        MetaChip(
            "${response.durationMillis} ms",
            palette.info,
            strings.workbenchResponseTimeLabel(response.durationMillis)
        )
        MetaChip(size, palette.textSecondary, strings.workbenchResponseSizeLabel(size))
        Spacer(Modifier.weight(1f))
        Text(
            response.contentType ?: "—",
            style = typography.caption.copy(color = palette.textTertiary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeaderViewer(headers: List<WorkbenchRequestField>) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(headers) { header ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        header.key,
                        modifier = Modifier.weight(0.75f),
                        style = typography.codeSmall.copy(color = palette.info),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        header.value,
                        modifier = Modifier.weight(1.25f),
                        style = typography.codeSmall.copy(color = palette.textSecondary)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecksViewer(state: WorkbenchState) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val report = state.assertionReport
    val ranAnyAssertion = report?.results?.any { it.outcome != WorkbenchAssertionOutcome.SKIPPED } == true
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
        item(key = "contract-heading") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Api, contentDescription = null, tint = palette.accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.workbenchContract, modifier = Modifier.weight(1f), style = typography.label.copy(color = palette.textPrimary))
                when {
                    state.selectedOperation == null -> MetaChip("—", palette.textTertiary)
                    state.contractViolations.none { it.severity == ContractViolationSeverity.ERROR } ->
                        MetaChip(strings.passed, palette.success)
                    else -> MetaChip("${state.contractViolations.size}", palette.error)
                }
            }
        }
        if (state.selectedOperation == null) {
            item(key = "contract-empty") {
                Text(strings.workbenchLoadContract, style = typography.caption.copy(color = palette.textTertiary))
            }
        } else if (state.contractViolations.isEmpty()) {
            item(key = "contract-pass") {
                CheckMessage(strings.workbenchContractPassed, WorkbenchAssertionOutcome.PASSED)
            }
        } else {
            items(state.contractViolations, key = { "${it.code}:${it.jsonPath}:${it.message.hashCode()}" }) { violation ->
                val color = if (violation.severity == ContractViolationSeverity.ERROR) palette.error else palette.warning
                Surface(
                    modifier = if (violation.severity == ContractViolationSeverity.ERROR) {
                        Modifier.workbenchErrorSemantics(violation.message, LiveRegionMode.Polite)
                    } else {
                        Modifier
                    },
                    color = color.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(violation.code, style = typography.label.copy(color = color))
                        }
                        Text(violation.message, style = typography.bodySmall.copy(color = palette.textPrimary))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(violation.jsonPath, style = typography.codeSmall.copy(color = palette.textTertiary))
                        }
                    }
                }
            }
        }

        item(key = "checks-divider") { HorizontalDivider(color = palette.divider, modifier = Modifier.padding(vertical = 3.dp)) }
        item(key = "assertions-heading") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, tint = palette.accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.workbenchAssertions, modifier = Modifier.weight(1f), style = typography.label.copy(color = palette.textPrimary))
                when {
                    report == null -> MetaChip("—", palette.textTertiary)
                    !ranAnyAssertion -> MetaChip("0", palette.textTertiary)
                    report.allPassed -> MetaChip("${report.passedCount}/${report.results.count { it.outcome != WorkbenchAssertionOutcome.SKIPPED }}", palette.success)
                    else -> MetaChip("${report.failedCount + report.errorCount}", palette.error)
                }
            }
        }
        when {
            state.assertions.isEmpty() -> item(key = "assertions-empty") {
                Text(strings.workbenchNoAssertions, style = typography.caption.copy(color = palette.textTertiary))
            }
            report == null -> item(key = "assertions-pending") {
                Text(strings.workbenchNoResponse, style = typography.caption.copy(color = palette.textTertiary))
            }
            !ranAnyAssertion -> item(key = "assertions-skipped") {
                Text(strings.workbenchNoEnabledChecks, style = typography.caption.copy(color = palette.textTertiary))
            }
            else -> items(report.results, key = { "${it.assertionIndex}:${it.assertionId}" }) { result ->
                CheckMessage(result.message, result.outcome)
            }
        }
    }
}

@Composable
private fun CheckMessage(message: String, outcome: WorkbenchAssertionOutcome) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    val color = when (outcome) {
        WorkbenchAssertionOutcome.PASSED -> palette.success
        WorkbenchAssertionOutcome.FAILED -> palette.error
        WorkbenchAssertionOutcome.ERROR -> palette.warning
        WorkbenchAssertionOutcome.SKIPPED -> palette.textTertiary
    }
    val state = when (outcome) {
        WorkbenchAssertionOutcome.PASSED -> strings.workbenchCheckPassed
        WorkbenchAssertionOutcome.FAILED -> strings.workbenchCheckFailed
        WorkbenchAssertionOutcome.ERROR -> strings.workbenchCheckError
        WorkbenchAssertionOutcome.SKIPPED -> strings.workbenchCheckSkipped
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = message
            stateDescription = state
            if (outcome == WorkbenchAssertionOutcome.FAILED || outcome == WorkbenchAssertionOutcome.ERROR) {
                liveRegion = LiveRegionMode.Polite
                error(message)
            }
        },
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (outcome) {
                    WorkbenchAssertionOutcome.PASSED -> Icons.Default.CheckCircle
                    WorkbenchAssertionOutcome.FAILED -> Icons.Default.Cancel
                    WorkbenchAssertionOutcome.ERROR -> Icons.Default.ErrorOutline
                    WorkbenchAssertionOutcome.SKIPPED -> Icons.Default.RemoveCircleOutline
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(message, style = LocalAppTypography.current.bodySmall.copy(color = LocalAppPalette.current.textPrimary))
        }
    }
}

@Composable
private fun CodeViewer(body: String) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val chunks = remember(body) {
        body.ifEmpty { "—" }
            .take(MAX_BODY_PREVIEW_CHARS)
            .chunked(BODY_PREVIEW_CHUNK_CHARS)
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .background(palette.codeBg, RoundedCornerShape(8.dp))
                .border(1.dp, palette.borderSubtle, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(chunks) { chunk ->
                Text(chunk, style = typography.code.copy(color = palette.textPrimary))
            }
        }
    }
}

@Composable
private fun MethodPicker(method: WorkbenchHttpMethod, onSelect: (WorkbenchHttpMethod) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val color = appHttpMethodColor(method.name)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.width(94.dp).height(50.dp),
                border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 9.dp)
            ) {
                Text(method.name, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = color)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                WorkbenchHttpMethod.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name, color = appHttpMethodColor(option.name), fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyLocationPicker(location: ApiKeyLocation, onSelect: (ApiKeyLocation) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val strings = LocalAppStrings.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.width(180.dp)) {
                Text(apiKeyLocationLabel(location, strings), modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ApiKeyLocation.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(apiKeyLocationLabel(option, strings)) }, onClick = {
                        expanded = false
                        onSelect(option)
                    })
                }
            }
        }
    }
}

private fun apiKeyLocationLabel(location: ApiKeyLocation, strings: AppStrings): String = when (location) {
    ApiKeyLocation.HEADER -> strings.workbenchApiKeyLocationHeader
    ApiKeyLocation.QUERY -> strings.workbenchApiKeyLocationQuery
    ApiKeyLocation.COOKIE -> strings.workbenchApiKeyLocationCookie
}

@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    WorkbenchTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = Modifier.fillMaxWidth(),
        secret = true,
        technical = true
    )
}

@Composable
private fun WorkbenchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    technical: Boolean = false
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val field: @Composable () -> Unit = {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.semantics {
                contentDescription = placeholder
                if (secret) password()
            },
            singleLine = true,
            textStyle = typography.codeSmall.copy(color = palette.textPrimary),
            placeholder = { Text(placeholder, style = typography.codeSmall, color = palette.textTertiary, maxLines = 1) },
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            colors = workbenchFieldColors(),
            shape = RoundedCornerShape(AppDimensions.inputRadius)
        )
    }
    if (technical) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { field() }
    } else {
        field()
    }
}

private fun Modifier.workbenchErrorSemantics(
    message: String,
    mode: LiveRegionMode = LiveRegionMode.Assertive
): Modifier = semantics {
    liveRegion = mode
    error(message)
}

@Composable
private fun WorkbenchAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit),
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    val register = LocalWorkbenchDialogRegistration.current
    val token = remember { Any() }
    DisposableEffect(register, token) {
        register(token, true)
        onDispose { register(token, false) }
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

@Composable
private fun SaveRequestDialog(
    state: WorkbenchState,
    saveAs: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onCreateAndSave: (String, String) -> Unit
) {
    val strings = LocalAppStrings.current
    val palette = LocalAppPalette.current
    val activeSaved = state.activeSavedRequestId?.let { id ->
        state.savedWorkspace.requests.firstOrNull { it.id == id }
    }
    var requestName by remember { mutableStateOf(state.draft.name) }
    var collectionId by remember {
        mutableStateOf(activeSaved?.collectionId ?: state.savedWorkspace.collections.firstOrNull()?.id)
    }
    var collectionName by remember { mutableStateOf("") }
    var collectionMenu by remember { mutableStateOf(false) }
    val selectedCollection = state.savedWorkspace.collections.firstOrNull { it.id == collectionId }

    WorkbenchAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (saveAs) strings.workbenchSaveRequestAs else strings.workbenchSaveRequest) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WorkbenchTextField(
                    value = requestName,
                    onValueChange = { requestName = it.take(256) },
                    placeholder = strings.workbenchRequest,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.savedWorkspace.collections.isEmpty()) {
                    WorkbenchTextField(
                        value = collectionName,
                        onValueChange = { collectionName = it.take(256) },
                        placeholder = strings.workbenchCollectionName,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box {
                        OutlinedButton(onClick = { collectionMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = palette.accent, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                selectedCollection?.name ?: strings.workbenchCollections,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = collectionMenu, onDismissRequest = { collectionMenu = false }) {
                            state.savedWorkspace.collections.forEach { collection ->
                                DropdownMenuItem(
                                    text = { Text(collection.name) },
                                    onClick = {
                                        collectionId = collection.id
                                        collectionMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                state.libraryErrorMessage?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.workbenchErrorSemantics(message),
                        style = LocalAppTypography.current.caption.copy(color = palette.error)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = requestName.isNotBlank() &&
                    (collectionId != null || collectionName.isNotBlank()) && !state.isLibrarySaving,
                onClick = {
                    val selectedId = collectionId
                    if (selectedId != null) onSave(selectedId, requestName)
                    else onCreateAndSave(collectionName, requestName)
                }
            ) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}

@Composable
private fun ScrollableWorkbenchTabs(
    tabs: List<RequestEditorTab>,
    selected: RequestEditorTab,
    label: (RequestEditorTab) -> String,
    onSelect: (RequestEditorTab) -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val layoutDirection = LocalLayoutDirection.current
    val focusRequesters = remember(tabs) { List(tabs.size) { FocusRequester() } }
    var pendingFocusIndex by remember(tabs) { mutableStateOf<Int?>(null) }
    LaunchedEffect(selected, pendingFocusIndex, tabs) {
        val index = pendingFocusIndex ?: return@LaunchedEffect
        if (tabs.getOrNull(index) == selected) {
            focusRequesters.getOrNull(index)?.requestFocus()
            pendingFocusIndex = null
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = tab == selected
            Column(
                modifier = Modifier.widthIn(min = 76.dp)
                    .focusRequester(focusRequesters[index])
                    .focusProperties { canFocus = active }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val direction = workbenchTabDirection(event.key) ?: return@onPreviewKeyEvent false
                        val target = resolveWorkbenchTabIndex(
                            currentIndex = index,
                            tabCount = tabs.size,
                            direction = direction,
                            isRtl = layoutDirection == LayoutDirection.Rtl
                        ) ?: return@onPreviewKeyEvent false
                        pendingFocusIndex = target
                        onSelect(tabs[target])
                        true
                    }
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = {
                            pendingFocusIndex = index
                            onSelect(tab)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label(tab),
                    style = typography.caption.copy(
                        color = if (active) palette.accent else palette.textSecondary,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    maxLines = 1
                )
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) palette.accent else Color.Transparent))
            }
        }
    }
}

@Composable
private fun workbenchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = LocalAppPalette.current.inputBackground,
    unfocusedContainerColor = LocalAppPalette.current.inputBackground,
    disabledContainerColor = LocalAppPalette.current.surfaceVariant,
    focusedBorderColor = LocalAppPalette.current.accent,
    unfocusedBorderColor = LocalAppPalette.current.inputBorder,
    cursorColor = LocalAppPalette.current.accent,
    disabledTextColor = LocalAppPalette.current.textTertiary
)

@Composable
private fun <T> TabStrip(
    tabs: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val layoutDirection = LocalLayoutDirection.current
    val focusRequesters = remember(tabs) { List(tabs.size) { FocusRequester() } }
    var pendingFocusIndex by remember(tabs) { mutableStateOf<Int?>(null) }
    LaunchedEffect(selected, pendingFocusIndex, tabs) {
        val index = pendingFocusIndex ?: return@LaunchedEffect
        if (tabs.getOrNull(index) == selected) {
            focusRequesters.getOrNull(index)?.requestFocus()
            pendingFocusIndex = null
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).selectableGroup()) {
        tabs.forEachIndexed { index, tab ->
            val active = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequesters[index])
                    .focusProperties { canFocus = active }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val direction = workbenchTabDirection(event.key) ?: return@onPreviewKeyEvent false
                        val target = resolveWorkbenchTabIndex(
                            currentIndex = index,
                            tabCount = tabs.size,
                            direction = direction,
                            isRtl = layoutDirection == LayoutDirection.Rtl
                        ) ?: return@onPreviewKeyEvent false
                        pendingFocusIndex = target
                        onSelect(tabs[target])
                        true
                    }
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = {
                            pendingFocusIndex = index
                            onSelect(tab)
                        }
                    )
                    .padding(top = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label(tab),
                    style = typography.caption.copy(
                        color = if (active) palette.accent else palette.textSecondary,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                        .background(if (active) palette.accent else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(icon: ImageVector, title: String) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(title, style = typography.label.copy(color = palette.textPrimary, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun EmptyRailMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = LocalAppTypography.current.caption.copy(color = LocalAppPalette.current.textTertiary),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun EmptyPanel(message: String, icon: ImageVector, color: Color = LocalAppPalette.current.textTertiary) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(9.dp))
            Text(
                message,
                style = LocalAppTypography.current.bodySmall.copy(color = color),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun MethodBadge(method: String) {
    val color = appHttpMethodColor(method)
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
        Text(
            method.take(4),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SavedRequestRow(
    request: WorkbenchSavedRequest,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) palette.sidebarSelected else Color.Transparent, RoundedCornerShape(7.dp))
            .padding(start = 8.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MethodBadge(request.draft.method.name)
        Spacer(Modifier.width(7.dp))
        Column(
            modifier = Modifier.weight(1f)
                .semantics {
                    role = Role.Button
                    this.selected = selected
                    contentDescription = "${request.draft.method.name} ${request.name}"
                }
                .clickable(onClick = onOpen)
                .padding(vertical = 4.dp)
        ) {
            Text(
                request.name,
                style = typography.label.copy(color = palette.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    request.draft.url,
                    style = typography.codeSmall.copy(color = palette.textTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = strings.delete, tint = palette.textTertiary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun HistoryRow(entry: WorkbenchHistoryEntry, onClick: () -> Unit) {
    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = "${entry.request.method.name} ${entry.request.url}"
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MethodBadge(entry.request.method.name)
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    entry.request.url,
                    style = typography.codeSmall.copy(color = palette.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                HISTORY_TIME_FORMATTER.format(Instant.ofEpochMilli(entry.createdAtEpochMillis)),
                style = typography.caption.copy(color = palette.textTertiary)
            )
        }
        val color = when {
            entry.response?.statusCode in 200..399 -> palette.success
            entry.response != null -> palette.warning
            else -> palette.error
        }
        Text(
            entry.response?.statusCode?.toString() ?: "!",
            style = typography.label.copy(color = color)
        )
    }
}

@Composable
private fun MetaChip(text: String, color: Color, accessibleLabel: String? = null) {
    Surface(
        modifier = if (accessibleLabel == null) Modifier else Modifier.clearAndSetSemantics {
            contentDescription = accessibleLabel
        },
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = LocalAppTypography.current.caption.copy(color = color, fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}

private fun formatVariableError(error: VariableResolutionError, strings: AppStrings): String = when (error) {
    is VariableResolutionError.Missing -> strings.workbenchMissingVariable(error.variableName, error.location.toString())
    is VariableResolutionError.Cycle -> strings.workbenchVariableCycle(error.variableChain.joinToString(" → "))
    is VariableResolutionError.ExpansionLimit -> strings.workbenchVariableExpansionLimit(error.variableName)
    is VariableResolutionError.SecretRequired -> strings.workbenchRestoreSecret(error.secretName)
}

private fun humanBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MiB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KiB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

private val HISTORY_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("MMM d · HH:mm")
    .withZone(ZoneId.systemDefault())
