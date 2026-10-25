package com.openvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openvault.app.ui.OpenVaultViewModel
import com.openvault.app.ui.components.EmptyStateView
import com.openvault.app.ui.components.MarkdownFormattingToolbar
import com.openvault.app.ui.components.MarkdownRenderer
import com.openvault.app.ui.components.SwipeableVaultCard
import com.openvault.app.ui.navigation.VaultModalSheet
import com.openvault.app.ui.navigation.VaultScreen
import com.openvault.app.ui.theme.JetBrainsMonoFontFamily
import com.openvault.app.ui.theme.LocalOpenVaultSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Screen #8: Poznámky – Seznam
 */
@Composable
fun NotesListScreen(viewModel: OpenVaultViewModel) {
    val notes by viewModel.notes.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val spacing = LocalOpenVaultSpacing.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedCollectionId by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableIntStateOf(0) } // 0 = updated, 1 = created, 2 = alpha

    val allTags = remember(notes) {
        notes.flatMap { it.tags }.distinct().sorted()
    }

    val filteredNotes = remember(notes, searchQuery, selectedTag, selectedCollectionId, sortMode) {
        notes.filter { note ->
            val matchesQuery = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.body.contains(searchQuery, ignoreCase = true) ||
                note.tags.any { it.contains(searchQuery, ignoreCase = true) }
            val matchesTag = selectedTag == null || selectedTag in note.tags
            val matchesCol = selectedCollectionId == null || note.collectionId == selectedCollectionId
            matchesQuery && matchesTag && matchesCol
        }.let { list ->
            when (sortMode) {
                1 -> list.sortedWith(compareByDescending<com.openvault.app.domain.model.Note> { it.isPinned }.thenByDescending { it.createdAt })
                2 -> list.sortedWith(compareByDescending<com.openvault.app.domain.model.Note> { it.isPinned }.thenBy { it.title.lowercase() })
                else -> list.sortedWith(compareByDescending<com.openvault.app.domain.model.Note> { it.isPinned }.thenByDescending { it.updatedAt })
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = 10.dp)
            .testTag("notes_list_screen")
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Fulltextové vyhledávání v šifrovaných poznámkách…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notes_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter & Sort Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = sortMode == 0,
                onClick = { sortMode = (sortMode + 1) % 3 },
                label = {
                    Text(
                        when (sortMode) {
                            1 -> "Řazení: Datum vytvoření"
                            2 -> "Řazení: A–Z"
                            else -> "Řazení: Poslední úprava"
                        }
                    )
                }
            )
            collections.forEach { col ->
                FilterChip(
                    selected = selectedCollectionId == col.id,
                    onClick = {
                        selectedCollectionId = if (selectedCollectionId == col.id) null else col.id
                    },
                    label = { Text("📁 ${col.name}") }
                )
            }
            allTags.forEach { tag ->
                FilterChip(
                    selected = selectedTag == tag,
                    onClick = { selectedTag = if (selectedTag == tag) null else tag },
                    label = { Text("#$tag") }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredNotes.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Description,
                title = "Žádné šifrované poznámky",
                subtitle = "Vytvořte poznámku s podporou Markdownu, příloh z VFS a historie revizí.",
                actionLabel = "Nová poznámka",
                onAction = { viewModel.navigateTo(VaultScreen.NoteEditor()) }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredNotes, key = { it.id }) { note ->
                    SwipeableVaultCard(
                        onClick = { viewModel.navigateTo(VaultScreen.NoteDetail(note.id)) },
                        onLongClick = {
                            viewModel.openModalSheet(
                                VaultModalSheet.ContextMenu(note.id, note.title, "note")
                            )
                        },
                        onSwipeLeftAction = { viewModel.deleteNote(note.id) },
                        onSwipeRightFavorite = { viewModel.toggleNotePinned(note.id) },
                        modifier = Modifier.testTag("note_card_${note.id}")
                    ) {
                        Column(modifier = Modifier.padding(spacing.cardPadding)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (note.isPinned) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Připnuto",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                if (note.isFavorite) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Oblíbené",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "rev ${note.rev}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (note.body.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = note.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (note.tags.isNotEmpty() || note.attachmentFileIds.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    note.tags.take(4).forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "#$tag",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (note.attachmentFileIds.isNotEmpty()) {
                                        Text(
                                            text = "📎 ${note.attachmentFileIds.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }
}

/**
 * Screen #9: Poznámka – Editor (with 3s debounce autosave, Markdown toolbar, templates, attachments & event links)
 */
@Composable
fun NoteEditorScreen(
    viewModel: OpenVaultViewModel,
    noteId: String?,
    initialCollectionId: String?
) {
    val notes by viewModel.notes.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val files by viewModel.files.collectAsState()
    val events by viewModel.events.collectAsState()

    val existingNote = remember(notes, noteId) { notes.find { it.id == noteId } }
    val templates = remember { viewModel.container.repository.getNoteTemplates() }

    var currentNoteId by remember { mutableStateOf(existingNote?.id ?: noteId) }
    var title by remember(existingNote) { mutableStateOf(existingNote?.title ?: "") }
    var body by remember(existingNote) { mutableStateOf(existingNote?.body ?: "") }
    var tagsInput by remember(existingNote) {
        mutableStateOf(existingNote?.tags?.joinToString(", ") ?: "")
    }
    var selectedCollectionId by remember(existingNote) {
        mutableStateOf(existingNote?.collectionId ?: initialCollectionId)
    }
    var attachmentIds by remember(existingNote) {
        mutableStateOf(existingNote?.attachmentFileIds ?: emptyList())
    }
    var linkedEventIds by remember(existingNote) {
        mutableStateOf(existingNote?.linkedEventIds ?: emptyList())
    }
    var isPinned by remember(existingNote) { mutableStateOf(existingNote?.isPinned ?: false) }
    var isFavorite by remember(existingNote) { mutableStateOf(existingNote?.isFavorite ?: false) }
    var isPreviewMode by remember { mutableStateOf(false) }
    var lastAutosaveStatus by remember { mutableStateOf("Připraveno k automatickému ukládání") }

    // 3-second debounce autosave when user edits content
    LaunchedEffect(title, body, tagsInput, selectedCollectionId, attachmentIds, linkedEventIds, isPinned, isFavorite) {
        if (title.isBlank() && body.isBlank()) return@LaunchedEffect
        delay(3_000L)
        val parsedTags = tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        viewModel.saveNote(
            id = currentNoteId,
            title = title,
            body = body,
            tags = parsedTags,
            collectionId = selectedCollectionId,
            attachmentFileIds = attachmentIds,
            linkedEventIds = linkedEventIds,
            isPinned = isPinned,
            isFavorite = isFavorite
        ) { saved ->
            currentNoteId = saved.id
            lastAutosaveStatus = "Automaticky uloženo a zašifrováno (rev ${saved.rev})"
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("note_editor_screen")
    ) {
        // Templates strip for new notes
        if (currentNoteId == null && body.isBlank()) {
            Text(
                text = "Šablony poznámek:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                templates.forEach { tpl ->
                    AssistChip(
                        onClick = {
                            if (tpl.defaultTitle.isNotEmpty()) title = tpl.defaultTitle
                            body = tpl.markdownContent
                            if (tpl.defaultTags.isNotEmpty()) {
                                tagsInput = tpl.defaultTags.joinToString(", ")
                            }
                        },
                        label = { Text(tpl.name) }
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = lastAutosaveStatus,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = isPreviewMode,
                    onClick = { isPreviewMode = !isPreviewMode },
                    label = { Text(if (isPreviewMode) "Režim: Náhled" else "Režim: Editace") }
                )
                FilterChip(
                    selected = isPinned,
                    onClick = { isPinned = !isPinned },
                    label = { Text("Připnout") }
                )
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Titulek poznámky (nepovinný – odvodí se automaticky)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("note_editor_title_input")
        )

        if (!isPreviewMode) {
            MarkdownFormattingToolbar { snippet ->
                body += snippet
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Obsah poznámky (Markdown)") },
                minLines = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_editor_body_input")
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    MarkdownRenderer(markdown = body.ifBlank { "*Prázdný obsah poznámky*" })
                }
            }
        }

        OutlinedTextField(
            value = tagsInput,
            onValueChange = { tagsInput = it },
            label = { Text("Štítky oddělené čárkou (např. projekt, krypto, osobní)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Collection selector
        if (collections.isNotEmpty()) {
            Text("Kolekce / Složka:", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = selectedCollectionId == null,
                    onClick = { selectedCollectionId = null },
                    label = { Text("Bez kolekce") }
                )
                collections.forEach { col ->
                    FilterChip(
                        selected = selectedCollectionId == col.id,
                        onClick = { selectedCollectionId = col.id },
                        label = { Text(col.name) }
                    )
                }
            }
        }

        // VFS File Attachments
        if (files.isNotEmpty()) {
            Text("Připojené šifrované soubory z VFS:", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                files.forEach { vf ->
                    val attached = vf.id in attachmentIds
                    FilterChip(
                        selected = attached,
                        onClick = {
                            attachmentIds = if (attached) attachmentIds - vf.id else attachmentIds + vf.id
                        },
                        label = { Text("📎 ${vf.fileName}") }
                    )
                }
            }
        }

        // Linked Calendar Events
        if (events.isNotEmpty()) {
            Text("Propojené události v kalendáři:", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                events.forEach { ev ->
                    val linked = ev.id in linkedEventIds
                    FilterChip(
                        selected = linked,
                        onClick = {
                            linkedEventIds = if (linked) linkedEventIds - ev.id else linkedEventIds + ev.id
                        },
                        label = { Text("📅 ${ev.title}") }
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(onClick = { viewModel.navigateBack() }) {
                Text("Zavřít")
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = {
                    val parsedTags = tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    viewModel.saveNote(
                        id = currentNoteId,
                        title = title,
                        body = body,
                        tags = parsedTags,
                        collectionId = selectedCollectionId,
                        attachmentFileIds = attachmentIds,
                        linkedEventIds = linkedEventIds,
                        isPinned = isPinned,
                        isFavorite = isFavorite
                    ) { saved ->
                        viewModel.navigateTo(VaultScreen.NoteDetail(saved.id), clearStack = false)
                    }
                },
                modifier = Modifier.testTag("note_editor_save_btn")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Uložit a zobrazit detail")
            }
        }
    }
}

/**
 * Screen #10: Poznámka – Detail (with rendered Markdown, interactive checkboxes, revision history, and double-tap title edit)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteDetailScreen(
    viewModel: OpenVaultViewModel,
    noteId: String
) {
    val notes by viewModel.notes.collectAsState()
    val files by viewModel.files.collectAsState()
    val events by viewModel.events.collectAsState()

    val note = remember(notes, noteId) { notes.find { it.id == noteId } }
    var showRevisionModal by remember { mutableStateOf(false) }
    var inlineEditingTitle by remember { mutableStateOf(false) }
    var editedTitle by remember(note) { mutableStateOf(note?.title ?: "") }

    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()) }

    if (note == null) {
        EmptyStateView(
            icon = Icons.Default.Description,
            title = "Poznámka nenalezena",
            subtitle = "Položka byla smazána nebo není v aktuální relaci dostupná.",
            actionLabel = "Zpět na seznam",
            onAction = { viewModel.navigateTo(VaultScreen.NotesList) }
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("note_detail_screen")
    ) {
        // Double-tap on title to switch to inline edit mode
        if (inlineEditingTitle) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("Inline úprava titulku") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.saveNote(
                            id = note.id,
                            title = editedTitle,
                            body = note.body,
                            tags = note.tags,
                            collectionId = note.collectionId,
                            attachmentFileIds = note.attachmentFileIds,
                            linkedEventIds = note.linkedEventIds,
                            isPinned = note.isPinned,
                            isFavorite = note.isFavorite
                        )
                        inlineEditingTitle = false
                    }
                ) {
                    Text("Uložit")
                }
            }
        } else {
            Text(
                text = note.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onDoubleClick = {
                            editedTitle = note.title
                            inlineEditingTitle = true
                        }
                    )
                    .testTag("note_detail_title")
            )
            Text(
                text = "Dvojklik na titulek = rychlá úprava • Revize #${note.rev} • Upraveno ${dateFormatter.format(Date(note.updatedAt))}",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Action bar
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            AssistChip(
                onClick = { viewModel.navigateTo(VaultScreen.NoteEditor(note.id)) },
                label = { Text("Upravit") },
                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = { showRevisionModal = true },
                label = { Text("Historie revizí (${note.history.size})") },
                leadingIcon = { Icon(Icons.Default.History, null, Modifier.size(16.dp)) },
                modifier = Modifier.testTag("note_revision_history_btn")
            )
            AssistChip(
                onClick = {
                    viewModel.deleteNote(note.id)
                    viewModel.navigateBack()
                },
                label = { Text("Smazat") },
                leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)) }
            )
        }

        // Tags
        if (note.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                note.tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Markdown Body with interactive checklist toggling
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                MarkdownRenderer(
                    markdown = note.body,
                    onToggleChecklistLine = { lineIdx ->
                        val lines = note.body.lines().toMutableList()
                        if (lineIdx in lines.indices) {
                            val line = lines[lineIdx]
                            lines[lineIdx] = when {
                                line.trimStart().startsWith("- [ ]") -> line.replaceFirst("- [ ]", "- [x]")
                                line.trimStart().startsWith("- [x]", true) -> line.replaceFirst("- [x]", "- [ ]", true)
                                else -> line
                            }
                            viewModel.saveNote(
                                id = note.id,
                                title = note.title,
                                body = lines.joinToString("\n"),
                                tags = note.tags,
                                collectionId = note.collectionId,
                                attachmentFileIds = note.attachmentFileIds,
                                linkedEventIds = note.linkedEventIds,
                                isPinned = note.isPinned,
                                isFavorite = note.isFavorite
                            )
                        }
                    }
                )
            }
        }

        // Linked VFS Files
        val attachedFiles = files.filter { it.id in note.attachmentFileIds }
        if (attachedFiles.isNotEmpty()) {
            Text("Připojené šifrované soubory (VFS):", style = MaterialTheme.typography.titleMedium)
            attachedFiles.forEach { vf ->
                AssistChip(
                    onClick = { viewModel.navigateTo(VaultScreen.FileDetail(vf.id)) },
                    label = { Text("${vf.fileName} (${vf.sizeBytes} B)") },
                    leadingIcon = { Icon(Icons.Default.AttachFile, null, Modifier.size(16.dp)) }
                )
            }
        }

        // Linked Calendar Events
        val linkedEvents = events.filter { it.id in note.linkedEventIds }
        if (linkedEvents.isNotEmpty()) {
            Text("Propojené události v kalendáři:", style = MaterialTheme.typography.titleMedium)
            linkedEvents.forEach { ev ->
                AssistChip(
                    onClick = { viewModel.navigateTo(VaultScreen.EventDetail(ev.id)) },
                    label = { Text(ev.title) },
                    leadingIcon = { Icon(Icons.Default.Event, null, Modifier.size(16.dp)) }
                )
            }
        }
    }

    if (showRevisionModal) {
        AlertDialog(
            onDismissRequest = { showRevisionModal = false },
            title = { Text("Historie šifrovaných revizí poznámky") },
            text = {
                if (note.history.isEmpty()) {
                    Text("Zatím nejsou uloženy žádné předchozí revize.")
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        note.history.forEach { rev ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Revize #${rev.rev} • ${dateFormatter.format(Date(rev.timestamp))}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = rev.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = rev.body.take(180),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.restoreNoteRevision(note.id, rev)
                                            showRevisionModal = false
                                        }
                                    ) {
                                        Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Obnovit tuto revizi")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRevisionModal = false }) {
                    Text("Zavřít")
                }
            }
        )
    }
}
