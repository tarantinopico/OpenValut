package com.openvault.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openvault.app.ui.OpenVaultViewModel
import com.openvault.app.ui.components.EmptyStateView
import com.openvault.app.ui.components.MarkdownRenderer
import com.openvault.app.ui.components.SwipeableVaultCard
import com.openvault.app.ui.navigation.VaultModalSheet
import com.openvault.app.ui.navigation.VaultScreen
import com.openvault.app.ui.theme.JetBrainsMonoFontFamily
import com.openvault.app.ui.theme.LocalOpenVaultSpacing
import com.openvault.app.ui.theme.StatusErrorRed
import java.nio.charset.StandardCharsets

/**
 * Screen #17: Soubory – Prohlížeč (VFS)
 */
@Composable
fun FilesBrowserScreen(
    viewModel: OpenVaultViewModel,
    initialFolderId: String?
) {
    val context = LocalContext.current
    val files by viewModel.files.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val spacing = LocalOpenVaultSpacing.current

    var currentFolderId by remember(initialFolderId) { mutableStateOf(initialFolderId) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }
    var sortMode by remember { mutableIntStateOf(0) } // 0 = date, 1 = name, 2 = size
    var showCreateTextFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("tajny_dokument.md") }
    var newFileBody by remember { mutableStateOf("# Šifrovaný VFS soubor\nObsah chráněný AES-256-GCM.") }
    var newFolderName by remember { mutableStateOf("") }

    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "soubor_${System.currentTimeMillis()}.bin"
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) {
                viewModel.uploadFileToVfs(
                    fileName = name,
                    mimeType = mime,
                    bytes = bytes,
                    folderId = currentFolderId
                )
            }
        }
    }

    val subFolders = remember(collections, currentFolderId) {
        collections.filter { it.parentId == currentFolderId }
    }

    val displayedFiles = remember(files, currentFolderId, searchQuery, sortMode) {
        files.filter { vf ->
            val inFolder = if (searchQuery.isNotBlank()) true else vf.folderId == currentFolderId
            val matchesQuery = searchQuery.isBlank() ||
                vf.fileName.contains(searchQuery, ignoreCase = true) ||
                vf.mimeType.contains(searchQuery, ignoreCase = true)
            inFolder && matchesQuery
        }.let { list ->
            when (sortMode) {
                1 -> list.sortedBy { it.fileName.lowercase() }
                2 -> list.sortedByDescending { it.sizeBytes }
                else -> list.sortedByDescending { it.updatedAt }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = 10.dp)
            .testTag("files_browser_screen")
    ) {
        // Breadcrumbs navigation
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            AssistChip(
                onClick = { currentFolderId = null },
                label = { Text("Kořen VFS (/)") }
            )
            val activeFolder = collections.find { it.id == currentFolderId }
            if (activeFolder != null) {
                Text("›", color = MaterialTheme.colorScheme.primary)
                AssistChip(
                    onClick = {},
                    label = { Text(activeFolder.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Hledat v šifrovaných souborech…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { isGridView = !isGridView }) {
                Icon(
                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = "Přepnout zobrazení seznam / mřížka",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action & Sort bar
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Button(
                onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.testTag("vfs_upload_saf_btn")
            ) {
                Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nahrát ze zařízení")
            }
            OutlinedButton(
                onClick = { showCreateTextFileDialog = true },
                modifier = Modifier.testTag("vfs_create_encrypted_file_btn")
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nový šifrovaný soubor")
            }
            OutlinedButton(onClick = { showNewFolderDialog = true }) {
                Icon(Icons.Default.CreateNewFolder, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nová podsložka")
            }
            FilterChip(
                selected = true,
                onClick = { sortMode = (sortMode + 1) % 3 },
                label = {
                    Text(
                        when (sortMode) {
                            1 -> "Řazení: Název"
                            2 -> "Řazení: Velikost"
                            else -> "Řazení: Datum"
                        }
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Subfolders
        if (subFolders.isNotEmpty() && searchQuery.isBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp)
            ) {
                subFolders.forEach { folder ->
                    AssistChip(
                        onClick = { currentFolderId = folder.id },
                        label = { Text("📁 ${folder.name}") }
                    )
                }
            }
        }

        if (displayedFiles.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.AttachFile,
                title = "Žádné šifrované soubory ve VFS",
                subtitle = "Nahrajte soubor ze zařízení nebo vytvořte nový šifrovaný dokument (bucket vault-files).",
                actionLabel = "Vytvořit šifrovaný soubor",
                onAction = { showCreateTextFileDialog = true }
            )
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedFiles, key = { it.id }) { vf ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(VaultScreen.FileDetail(vf.id)) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = vf.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${vf.sizeBytes} B • ${vf.mimeType}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedFiles, key = { it.id }) { vf ->
                    SwipeableVaultCard(
                        onClick = { viewModel.navigateTo(VaultScreen.FileDetail(vf.id)) },
                        onLongClick = {
                            viewModel.openModalSheet(VaultModalSheet.ContextMenu(vf.id, vf.fileName, "file"))
                        },
                        onSwipeLeftAction = { viewModel.deleteFile(vf.id) },
                        onSwipeRightFavorite = {}
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(spacing.cardPadding)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vf.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${vf.sizeBytes} B • ${vf.mimeType} • SHA-256: ${vf.sha256Hex.take(10)}…",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    if (showCreateTextFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateTextFileDialog = false },
            title = { Text("Vytvořit šifrovaný VFS soubor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("Název souboru (např. seed_backup.txt)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newFileBody,
                        onValueChange = { newFileBody = it },
                        label = { Text("Obsah souboru (bude zašifrován AES-256-GCM)") },
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.uploadFileToVfs(
                            fileName = newFileName.ifBlank { "dokument.txt" },
                            mimeType = if (newFileName.endsWith(".md")) "text/markdown"
                            else if (newFileName.endsWith(".json")) "application/json"
                            else "text/plain",
                            bytes = newFileBody.toByteArray(StandardCharsets.UTF_8),
                            folderId = currentFolderId
                        )
                        showCreateTextFileDialog = false
                    }
                ) {
                    Text("Zašifrovat a uložit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTextFileDialog = false }) {
                    Text("Zrušit")
                }
            }
        )
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Nová šifrovaná podsložka") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Název podsložky") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.saveCollection(
                                name = newFolderName,
                                parentId = currentFolderId
                            )
                            newFolderName = ""
                            showNewFolderDialog = false
                        }
                    }
                ) {
                    Text("Vytvořit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Zrušit")
                }
            }
        )
    }
}

/**
 * Screen #18: Soubor – Detail (with in-memory decrypted preview via SecurityGate & cryptographic metadata)
 */
@Composable
fun FileDetailScreen(
    viewModel: OpenVaultViewModel,
    fileId: String
) {
    val files by viewModel.files.collectAsState()
    val vf = remember(files, fileId) { files.find { it.id == fileId } }

    var decryptedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var renameInput by remember(vf) { mutableStateOf(vf?.fileName ?: "") }

    LaunchedEffect(fileId) {
        decryptedBytes = viewModel.container.repository.decryptFileBytesInMemory(fileId)
    }

    if (vf == null) {
        EmptyStateView(
            icon = Icons.Default.AttachFile,
            title = "Soubor nenalezen",
            subtitle = "Položka byla smazána.",
            actionLabel = "Zpět do VFS",
            onAction = { viewModel.navigateTo(VaultScreen.FilesBrowser()) }
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("file_detail_screen")
    ) {
        Text(
            text = vf.fileName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Kryptografická metadata (AES-256-GCM)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Velikost: ${vf.sizeBytes} bajtů • MIME: ${vf.mimeType}")
                Text(
                    text = "SHA-256: ${vf.sha256Hex}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "Supabase Storage cesta: vault-files/${vf.storagePath}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Číslo revize: #${vf.rev}")
            }
        }

        // In-memory decrypted preview (no unencrypted disk cache)
        val previewString = remember(decryptedBytes, vf.previewText) {
            decryptedBytes?.let { runCatching { it.decodeToString() }.getOrNull() } ?: vf.previewText
        }
        if (!previewString.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Dešifrovaný náhled v RAM (SecurityGate):",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (vf.fileName.endsWith(".md", true)) {
                        MarkdownRenderer(markdown = previewString)
                    } else {
                        Text(
                            text = previewString,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = renameInput,
                onValueChange = { renameInput = it },
                label = { Text("Přejmenovat soubor") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    viewModel.renameOrMoveFile(vf.id, renameInput, vf.folderId)
                }
            ) {
                Text("Přejmenovat")
            }
        }

        OutlinedButton(
            onClick = {
                viewModel.deleteFile(vf.id)
                viewModel.navigateBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, null, tint = StatusErrorRed)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Trvale smazat šifrovaný soubor")
        }
    }
}

/**
 * Collections & Folders Hierarchical Screen
 */
@Composable
fun CollectionsManagerScreen(viewModel: OpenVaultViewModel) {
    val collections by viewModel.collections.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val passwords by viewModel.passwords.collectAsState()
    val files by viewModel.files.collectAsState()
    val spacing = LocalOpenVaultSpacing.current

    var newCollectionName by remember { mutableStateOf("") }
    var selectedParentId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = 10.dp)
            .testTag("collections_manager_screen")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = "Vytvořit novou šifrovanou kolekci / složku",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    label = { Text("Název kolekce") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_collection_name_input")
                )
                if (collections.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = selectedParentId == null,
                            onClick = { selectedParentId = null },
                            label = { Text("Kořenová úroveň") }
                        )
                        collections.forEach { parent ->
                            FilterChip(
                                selected = selectedParentId == parent.id,
                                onClick = { selectedParentId = parent.id },
                                label = { Text("Pod: ${parent.name}") }
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        if (newCollectionName.isNotBlank()) {
                            viewModel.saveCollection(
                                name = newCollectionName,
                                parentId = selectedParentId,
                                sortOrder = collections.size
                            )
                            newCollectionName = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_collection_btn")
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Přidat kolekci")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (collections.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Folder,
                title = "Žádné kolekce ani složky",
                subtitle = "Váš trezor je čistý. Vytvořte vlastní hierarchii kolekcí pro poznámky, hesla a VFS soubory."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
                modifier = Modifier.fillMaxSize()
            ) {
                items(collections, key = { it.id }) { col ->
                    val parentName = collections.find { it.id == col.parentId }?.name
                    val itemCount = notes.count { it.collectionId == col.id } +
                        passwords.count { it.collectionId == col.id } +
                        files.count { it.folderId == col.id }

                    SwipeableVaultCard(
                        onClick = { viewModel.navigateTo(VaultScreen.FilesBrowser(col.id)) },
                        onLongClick = {},
                        onSwipeLeftAction = { viewModel.deleteCollection(col.id) },
                        onSwipeRightFavorite = {}
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (parentName != null) "$parentName › ${col.name}" else col.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "$itemCount položek • Pořadí #${col.sortOrder} • rev ${col.rev}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deleteCollection(col.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = StatusErrorRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
