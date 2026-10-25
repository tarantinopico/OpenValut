package com.openvault.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openvault.app.sync.SyncStatus
import com.openvault.app.ui.theme.JetBrainsMonoFontFamily
import com.openvault.app.ui.theme.StatusErrorRed
import com.openvault.app.ui.theme.StatusInfoCyan
import com.openvault.app.ui.theme.StatusSuccessEmerald
import com.openvault.app.ui.theme.StatusWarningAmber

@Composable
fun SyncStatusBadge(
    syncStatus: SyncStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColor = when (syncStatus) {
        is SyncStatus.Synced -> StatusSuccessEmerald
        is SyncStatus.Syncing -> StatusInfoCyan
        is SyncStatus.ConflictOrWarning -> StatusWarningAmber
        is SyncStatus.Offline -> StatusErrorRed
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = badgeColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f)),
        modifier = modifier.testTag("sync_status_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = syncStatus.badgeSymbol,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = badgeColor
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandableVaultFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNewNote: () -> Unit,
    onNewEvent: () -> Unit,
    onNewPassword: () -> Unit,
    onNewFile: () -> Unit,
    onNewRecord: () -> Unit,
    onLongPressQuickNote: () -> Unit,
    onSwipeUpQuickCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var verticalDragDelta by remember { mutableFloatStateOf(0f) }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FabMenuItem("Poznámka", Icons.Default.Description, "fab_action_note") {
                    onExpandedChange(false)
                    onNewNote()
                }
                FabMenuItem("Událost", Icons.Default.Event, "fab_action_event") {
                    onExpandedChange(false)
                    onNewEvent()
                }
                FabMenuItem("Heslo + TOTP", Icons.Default.Key, "fab_action_password") {
                    onExpandedChange(false)
                    onNewPassword()
                }
                FabMenuItem("Šifrovaný soubor", Icons.Default.AttachFile, "fab_action_file") {
                    onExpandedChange(false)
                    onNewFile()
                }
                FabMenuItem("Záznam databáze", Icons.Default.Storage, "fab_action_record") {
                    onExpandedChange(false)
                    onNewRecord()
                }
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { verticalDragDelta = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            verticalDragDelta += dragAmount
                        },
                        onDragEnd = {
                            if (verticalDragDelta < -45f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onExpandedChange(false)
                                onSwipeUpQuickCapture()
                            }
                            verticalDragDelta = 0f
                        }
                    )
                }
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpandedChange(!expanded)
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onExpandedChange(false)
                        onLongPressQuickNote()
                    }
                )
                .testTag("multifunction_fab")
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = "Rychlé akce OpenVault (klepnutí = menu, podržení = nová poznámka, tažení nahoru = Quick Capture)",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun FabMenuItem(
    label: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        tonalElevation = 6.dp,
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableVaultCard(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeLeftAction: () -> Unit,
    onSwipeRightFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    },
                    onDragEnd = {
                        if (dragAccumulator < -95f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSwipeLeftAction()
                        } else if (dragAccumulator > 95f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSwipeRightFavorite()
                        }
                        dragAccumulator = 0f
                    }
                )
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        content()
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.testTag("empty_state_action_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun LoadingStateView(message: String = "Kryptografická operace probíhá…") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StatusErrorRed.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, StatusErrorRed.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusErrorRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Bezpečnostní upozornění",
                    style = MaterialTheme.typography.titleMedium,
                    color = StatusErrorRed
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onRetry) {
                    Text("Zkusit znovu")
                }
            }
        }
    }
}

@Composable
fun MarkdownFormattingToolbar(
    onInsertSnippet: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
    ) {
        val buttons = listOf(
            "H1" to "\n# ",
            "H2" to "\n## ",
            "Bold" to "**tučně**",
            "Italic" to "*kurzíva*",
            "Checklist" to "\n- [ ] ",
            "Code" to "\n```\nkód\n```\n",
            "Quote" to "\n> ",
            "Link" to "[odkaz](https://)"
        )
        for ((label, snippet) in buttons) {
            Surface(
                onClick = { onInsertSnippet(snippet) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = label,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun MarkdownRenderer(
    markdown: String,
    onToggleChecklistLine: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lines = remember(markdown) { markdown.lines() }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        var inCodeBlock = false
        lines.forEachIndexed { index, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                return@forEachIndexed
            }
            if (inCodeBlock) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF090B10))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = rawLine,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (trimmed.startsWith("# ")) {
                Text(
                    text = trimmed.removePrefix("# "),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (trimmed.startsWith("## ")) {
                Text(
                    text = trimmed.removePrefix("## "),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (trimmed.startsWith("### ")) {
                Text(
                    text = trimmed.removePrefix("### "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]", ignoreCase = true)) {
                val checked = trimmed.startsWith("- [x]", ignoreCase = true)
                val itemText = trimmed.drop(5).trim()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = onToggleChecklistLine != null) {
                            onToggleChecklistLine?.invoke(index)
                        }
                        .padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = itemText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (trimmed.startsWith("> ")) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(22.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = trimmed.removePrefix("> "),
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (trimmed.startsWith("- ")) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("- ")),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (trimmed.isNotEmpty()) {
                Text(
                    text = formatInlineMarkdown(rawLine),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatInlineMarkdown(input: String) = buildAnnotatedString {
    var i = 0
    while (i < input.length) {
        if (input.startsWith("**", i)) {
            val end = input.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(input.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        if (input.startsWith("*", i)) {
            val end = input.indexOf("*", i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(input.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        append(input[i])
        i++
    }
}

/**
 * Screen #35: Quick Capture Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureBottomSheet(
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onQuickSaveNote: (String, String) -> Unit,
    onQuickSavePassword: (String, String, String) -> Unit,
    onQuickSaveEvent: (String, Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var title by remember { mutableStateOf("") }
    var secondaryField by remember { mutableStateOf("") }
    var secretOrBody by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("quick_capture_sheet")
        ) {
            Text(
                text = "Bleskový zápis (Quick Capture)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Poznámka") }
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Heslo") }
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Událost") }
                )
            }

            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titulek (nepovinný)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = secretOrBody,
                        onValueChange = { secretOrBody = it },
                        label = { Text("Obsah poznámky (Markdown)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                1 -> {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Služba / Název") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = secondaryField,
                        onValueChange = { secondaryField = it },
                        label = { Text("Uživatelské jméno / E-mail") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = secretOrBody,
                        onValueChange = { secretOrBody = it },
                        label = { Text("Heslo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                2 -> {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Název události") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Začátek: Dnes za 1 hodinu (upravit můžete v detailu)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Zrušit")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        when (selectedTab) {
                            0 -> if (title.isNotBlank() || secretOrBody.isNotBlank()) {
                                onQuickSaveNote(title, secretOrBody)
                                onDismiss()
                            }
                            1 -> if (title.isNotBlank() && secretOrBody.isNotBlank()) {
                                onQuickSavePassword(title, secondaryField, secretOrBody)
                                onDismiss()
                            }
                            2 -> if (title.isNotBlank()) {
                                onQuickSaveEvent(title, System.currentTimeMillis() + 3_600_000L)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.testTag("quick_capture_save_btn")
                ) {
                    Text("Zašifrovat a uložit")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Screen #36: Sync Progress Modal
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncProgressModalSheet(
    syncStatus: SyncStatus,
    pendingQueueCount: Int,
    resolvedConflicts: List<String>,
    onTriggerSyncNow: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .testTag("sync_progress_modal")
        ) {
            Text(
                text = "Stav E2E synchronizace (Supabase)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${syncStatus.badgeSymbol} ${syncStatus.labelCs}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (syncStatus is SyncStatus.Syncing) {
                LinearProgressIndicator(
                    progress = { (syncStatus.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${syncStatus.stepDescription} (${syncStatus.progressPercent} %)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Položek v offline frontě (SyncQueue): $pendingQueueCount",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (resolvedConflicts.isNotEmpty()) {
                Text(
                    text = "Poslední vyřešené kolize (LWW):",
                    style = MaterialTheme.typography.labelLarge,
                    color = StatusWarningAmber
                )
                resolvedConflicts.take(3).forEach { msg ->
                    Text(
                        text = "• $msg",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Zavřít")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onTriggerSyncNow) {
                    Text("Synchronizovat nyní")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Screen #37: Lock Confirmation Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockConfirmationBottomSheet(
    pendingSyncCount: Int,
    onConfirmLock: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .testTag("lock_confirmation_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = StatusErrorRed
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Uzamknout trezor OpenVault?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Hlavní klíč (Vault Key) v paměti RAM bude okamžitě přepsán nulami (zeroized) a SQLCipher relace bude uzavřena.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (pendingSyncCount > 0) {
                Text(
                    text = "Poznámka: Ve frontě SyncQueue čeká $pendingSyncCount položek k odeslání po dalším odemknutí.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusWarningAmber
                )
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Zůstat v trezoru")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirmLock,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusErrorRed),
                    modifier = Modifier.testTag("confirm_lock_btn")
                ) {
                    Text("Zamknout a vymazat RAM", color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Screen #38: Universal Long-Press Context Menu Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuBottomSheet(
    itemTitle: String,
    itemType: String,
    onEdit: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onMoveToCollection: (() -> Unit)? = null,
    onLinkToEvent: (() -> Unit)? = null,
    onExportOrCopy: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("context_menu_sheet")
        ) {
            Text(
                text = itemTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )

            ContextSheetActionRow(Icons.Default.Edit, "Upravit položku") {
                onDismiss()
                onEdit()
            }
            if (onDuplicate != null) {
                ContextSheetActionRow(Icons.Default.ContentCopy, "Duplikovat") {
                    onDismiss()
                    onDuplicate()
                }
            }
            if (onMoveToCollection != null) {
                ContextSheetActionRow(Icons.Default.DriveFileMove, "Přesunout do kolekce / složky") {
                    onDismiss()
                    onMoveToCollection()
                }
            }
            if (onLinkToEvent != null) {
                ContextSheetActionRow(Icons.Default.Event, "Propojit s kalendářní událostí") {
                    onDismiss()
                    onLinkToEvent()
                }
            }
            if (onExportOrCopy != null) {
                ContextSheetActionRow(
                    icon = Icons.Default.Share,
                    label = if (itemType == "password") "Zkopírovat heslo (30s auto-clear)" else "Exportovat / Sdílet"
                ) {
                    onDismiss()
                    onExportOrCopy()
                }
            }
            ContextSheetActionRow(
                icon = Icons.Default.Delete,
                label = "Smazat položku",
                tint = StatusErrorRed
            ) {
                onDismiss()
                onDelete()
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ContextSheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
        }
    }
}
