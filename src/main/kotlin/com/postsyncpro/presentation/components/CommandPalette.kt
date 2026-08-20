package com.postsyncpro.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.postsyncpro.presentation.i18n.LocalAppStrings
import com.postsyncpro.presentation.navigation.NavigationState
import com.postsyncpro.presentation.navigation.Screen
import com.postsyncpro.presentation.theme.*

/**
 * Keyboard-first destination switcher. It intentionally searches product
 * concepts rather than implementation names, so it remains useful as the
 * number of API tools grows.
 */
@Composable
fun CommandPalette(
    visible: Boolean,
    navigationState: NavigationState,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val palette = LocalAppPalette.current
    val typography = LocalAppTypography.current
    val strings = LocalAppStrings.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableStateOf(0) }

    val destinations = remember(navigationState.isOnboardingComplete) {
        Screen.entries.filter { screen ->
            screen != Screen.ONBOARDING &&
                (navigationState.isOnboardingComplete ||
                    screen == Screen.SETTINGS ||
                    screen == Screen.PRO_FEATURES ||
                    screen.section == "PRO")
        }
    }
    val normalizedQuery = query.trim().lowercase()
    val filtered = destinations.filter { screen ->
        normalizedQuery.isEmpty() ||
            screen.title(strings).lowercase().contains(normalizedQuery) ||
            screen.sectionTitle(strings).lowercase().contains(normalizedQuery)
    }

    fun openSelection() {
        filtered.getOrNull(selectedIndex)?.let { destination ->
            navigationState.navigateTo(destination)
            onDismiss()
        }
    }

    fun handlePaletteKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.Escape -> {
                onDismiss()
                true
            }
            Key.Tab -> {
                // Navigation is arrow-key driven; focus never leaves the modal.
                focusRequester.requestFocus()
                true
            }
            Key.DirectionDown -> {
                if (filtered.isNotEmpty()) selectedIndex = (selectedIndex + 1) % filtered.size
                true
            }
            Key.DirectionUp -> {
                if (filtered.isNotEmpty()) selectedIndex = (selectedIndex - 1 + filtered.size) % filtered.size
                true
            }
            Key.Enter -> {
                openSelection()
                true
            }
            else -> false
        }
    }

    LaunchedEffect(visible) {
        if (visible) focusRequester.requestFocus()
    }
    LaunchedEffect(filtered.size) {
        selectedIndex = selectedIndex.coerceIn(0, (filtered.size - 1).coerceAtLeast(0))
    }
    LaunchedEffect(selectedIndex, filtered.size) {
        if (filtered.isNotEmpty()) listState.animateScrollToItem(selectedIndex)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 640.dp)
                .heightIn(max = 560.dp)
                .semantics { paneTitle = strings.quickOpen }
                .onPreviewKeyEvent(::handlePaletteKey),
            shape = RoundedCornerShape(16.dp),
            color = palette.surfaceElevated,
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
        ) {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selectedIndex = 0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent(::handlePaletteKey)
                        .semantics { contentDescription = strings.searchCommands },
                    singleLine = true,
                    textStyle = typography.body.copy(color = palette.textPrimary),
                    placeholder = {
                        Text(strings.searchCommands, style = typography.body, color = palette.textTertiary)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = palette.textSecondary)
                    },
                    trailingIcon = {
                        Surface(
                            color = palette.surfaceVariant,
                            shape = RoundedCornerShape(5.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
                        ) {
                            Text(
                                "ESC",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                style = typography.caption.copy(color = palette.textTertiary)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBackground,
                        unfocusedContainerColor = palette.inputBackground,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.inputBorder,
                        cursorColor = palette.accent
                    )
                )

                HorizontalDivider(color = palette.divider)

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(strings.noCommands, style = typography.body, color = palette.textSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                        state = listState,
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(filtered, key = { _, screen -> screen.name }) { index, screen ->
                            val isSelected = index == selectedIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) palette.sidebarSelected else palette.surfaceElevated,
                                        RoundedCornerShape(9.dp)
                                    )
                                    .semantics {
                                        role = Role.Button
                                        selected = isSelected
                                        contentDescription =
                                            "${screen.title(strings)} · ${screen.sectionTitle(strings)}"
                                    }
                                    .clickable {
                                        navigationState.navigateTo(screen)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clearAndSetSemantics { }
                                        .background(
                                            if (isSelected) palette.accent.copy(alpha = 0.14f)
                                            else palette.surfaceVariant,
                                            RoundedCornerShape(7.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        screen.title(strings).take(1).uppercase(),
                                        style = typography.label.copy(
                                            color = if (isSelected) palette.accent else palette.textSecondary
                                        )
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        screen.title(strings),
                                        style = typography.body.copy(color = palette.textPrimary)
                                    )
                                    Text(
                                        screen.sectionTitle(strings),
                                        style = typography.caption.copy(color = palette.textTertiary)
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.SubdirectoryArrowLeft,
                                        contentDescription = null,
                                        tint = palette.textTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = palette.divider)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.quickOpen, style = typography.caption.copy(color = palette.textTertiary))
                    Spacer(Modifier.weight(1f))
                    Text("↑  ↓", style = typography.caption.copy(color = palette.textTertiary))
                    Spacer(Modifier.width(14.dp))
                    Text("↵  ${strings.continueText}", style = typography.caption.copy(color = palette.textTertiary))
                }
            }
        }
    }
}
