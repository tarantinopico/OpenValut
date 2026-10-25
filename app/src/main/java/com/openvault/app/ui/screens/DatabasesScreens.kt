package com.openvault.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openvault.app.domain.model.DatabaseFieldSchema
import com.openvault.app.domain.model.DatabaseFieldType
import com.openvault.app.security.PasswordAnalyzer
import com.openvault.app.ui.OpenVaultViewModel
import com.openvault.app.ui.components.EmptyStateView
import com.openvault.app.ui.components.MarkdownRenderer
import com.openvault.app.ui.components.SwipeableVaultCard
import com.openvault.app.ui.navigation.VaultModalSheet
import com.openvault.app.ui.navigation.VaultScreen
import com.openvault.app.ui.theme.JetBrainsMonoFontFamily
import com.openvault.app.ui.theme.LocalOpenVaultSpacing
import com.openvault.app.ui.theme.StatusErrorRed
import java.util.UUID

/**
 * Screen #19: Databáze – Seznam
 */
@Composable
fun DatabasesListScreen(viewModel: OpenVaultViewModel) {
    val databases by viewModel.databases.collectAsState()
    val records by viewModel.records.collectAsState()
    val spacing = LocalOpenVaultSpacing.current
    val templates = remember { viewModel.container.repository.getDatabaseTemplates() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = 10.dp)
            .testTag("databases_list_screen")
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Strukturované E2E databáze (${databases.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Button(
                onClick = { viewModel.navigateTo(VaultScreen.DatabaseSchemaBuilder(null)) },
                modifier = Modifier.testTag("create_custom_db_btn")
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nová databáze")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Rychlé vytvoření ze šablony jedním klepnutím:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            templates.forEach { tpl ->
                AssistChip(
                    onClick = {
                        viewModel.saveUserDatabase(
                            name = tpl.name,
                            description = tpl.description,
                            icon = tpl.icon,
                            schema = tpl.schema
                        ) { created ->
                            viewModel.navigateTo(VaultScreen.DatabaseRecordsList(created.id))
                        }
                    },
                    label = { Text("+ ${tpl.name}") }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (databases.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Storage,
                title = "Žádné uživatelské databáze",
                subtitle = "Vytvořte vlastní databázi s vlastním schématem polí (TEXT, NUMBER, PASSWORD, FILE, JSON…) nebo použijte šablonu výše.",
                actionLabel = "Otevřít Schema Builder",
                onAction = { viewModel.navigateTo(VaultScreen.DatabaseSchemaBuilder(null)) }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
                modifier = Modifier.fillMaxSize()
            ) {
                items(databases, key = { it.id }) { db ->
                    val recCount = records.count { it.databaseId == db.id }
                    SwipeableVaultCard(
                        onClick = { viewModel.navigateTo(VaultScreen.DatabaseRecordsList(db.id)) },
                        onLongClick = {},
                        onSwipeLeftAction = { viewModel.deleteUserDatabase(db.id) },
                        onSwipeRightFavorite = {}
                    ) {
                        Column(modifier = Modifier.padding(spacing.cardPadding)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = db.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$recCount záznamů • ${db.schema.size} polí • rev ${db.rev}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.navigateTo(VaultScreen.DatabaseSchemaBuilder(db.id)) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = "Upravit schéma",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (db.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = db.description,
                                    style = MaterialTheme.typography.bodyMedium,
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
}

/**
 * Screen #20: Databáze – Schema Builder
 */
@Composable
fun DatabaseSchemaBuilderScreen(
    viewModel: OpenVaultViewModel,
    databaseId: String?
) {
    val databases by viewModel.databases.collectAsState()
    val existing = remember(databases, databaseId) { databases.find { it.id == databaseId } }

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var description by remember(existing) { mutableStateOf(existing?.description ?: "") }
    var fields by remember(existing) {
        mutableStateOf(
            existing?.schema ?: listOf(
                DatabaseFieldSchema(
                    id = "title_field",
                    name = "Název položky",
                    type = DatabaseFieldType.TEXT,
                    required = true,
                    sortOrder = 0
                ),
                DatabaseFieldSchema(
                    id = "secret_field",
                    name = "Tajný klíč / Hodnota",
                    type = DatabaseFieldType.PASSWORD,
                    required = false,
                    sortOrder = 1
                )
            )
        )
    }

    var newFieldName by remember { mutableStateOf("") }
    var newFieldType by remember { mutableStateOf(DatabaseFieldType.TEXT) }
    var newFieldRequired by remember { mutableStateOf(false) }
    var newFieldDefault by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("database_schema_builder_screen")
    ) {
        Text(
            text = if (existing == null) "Nová strukturovaná databáze" else "Úprava schématu a migrace záznamů",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Název databáze (např. Bankovní karty, Licenční klíče)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("db_schema_name_input")
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Popis účelu databáze") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Definovaná pole schématu (${fields.size}):",
            style = MaterialTheme.typography.titleMedium
        )

        fields.forEachIndexed { index, field ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${index + 1}. ${field.name} (${field.type.name})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${field.type.labelCs}${if (field.required) " • Povinné" else ""}${if (field.defaultValue.isNotEmpty()) " • Výchozí: ${field.defaultValue}" else ""}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        enabled = index > 0,
                        onClick = {
                            val mutable = fields.toMutableList()
                            val tmp = mutable[index - 1]
                            mutable[index - 1] = mutable[index]
                            mutable[index] = tmp
                            fields = mutable
                        }
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Posunout nahoru")
                    }
                    IconButton(
                        enabled = index < fields.lastIndex,
                        onClick = {
                            val mutable = fields.toMutableList()
                            val tmp = mutable[index + 1]
                            mutable[index + 1] = mutable[index]
                            mutable[index] = tmp
                            fields = mutable
                        }
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Posunout dolů")
                    }
                    IconButton(
                        onClick = {
                            fields = fields.filterIndexed { i, _ -> i != index }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Odebrat pole", tint = StatusErrorRed)
                    }
                }
            }
        }

        // Add new field card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = "Přidat nové pole do schématu",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = newFieldName,
                    onValueChange = { newFieldName = it },
                    label = { Text("Název pole") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    DatabaseFieldType.entries.forEach { ft ->
                        FilterChip(
                            selected = newFieldType == ft,
                            onClick = { newFieldType = ft },
                            label = { Text(ft.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = newFieldDefault,
                    onValueChange = { newFieldDefault = it },
                    label = { Text("Výchozí hodnota (nepovinné)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = newFieldRequired, onCheckedChange = { newFieldRequired = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Povinné pole")
                    }
                    OutlinedButton(
                        onClick = {
                            if (newFieldName.isNotBlank()) {
                                fields = fields + DatabaseFieldSchema(
                                    id = "fld_${UUID.randomUUID().toString().take(8)}",
                                    name = newFieldName.trim(),
                                    type = newFieldType,
                                    required = newFieldRequired,
                                    defaultValue = newFieldDefault,
                                    sortOrder = fields.size
                                )
                                newFieldName = ""
                                newFieldDefault = ""
                                newFieldRequired = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Přidat pole")
                    }
                }
            }
        }

        Button(
            onClick = {
                viewModel.saveUserDatabase(
                    id = existing?.id,
                    name = name,
                    description = description,
                    schema = fields
                ) { saved ->
                    viewModel.navigateTo(VaultScreen.DatabaseRecordsList(saved.id))
                }
            },
            enabled = name.isNotBlank() && fields.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_database_schema_btn")
        ) {
            Icon(Icons.Default.Save, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Uložit schéma a migrovat záznamy")
        }
    }
}

/**
 * Screen #21: Databáze – Záznamy (Seznam)
 */
@Composable
fun DatabaseRecordsListScreen(
    viewModel: OpenVaultViewModel,
    databaseId: String
) {
    val databases by viewModel.databases.collectAsState()
    val allRecords by viewModel.records.collectAsState()
    val spacing = LocalOpenVaultSpacing.current

    val db = remember(databases, databaseId) { databases.find { it.id == databaseId } }
    var searchQuery by remember { mutableStateOf("") }
    var sortFieldId by remember(db) { mutableStateOf(db?.schema?.firstOrNull()?.id) }

    val dbRecords = remember(allRecords, databaseId, searchQuery, sortFieldId) {
        allRecords.filter { rec ->
            rec.databaseId == databaseId && (
                searchQuery.isBlank() ||
                    rec.values.values.any { it.contains(searchQuery, ignoreCase = true) }
                )
        }.let { list ->
            val key = sortFieldId
            if (key != null) {
                list.sortedBy { it.values[key]?.lowercase().orEmpty() }
            } else {
                list
            }
        }
    }

    if (db == null) {
        EmptyStateView(
            icon = Icons.Default.Storage,
            title = "Databáze nenalezena",
            subtitle = "Vyberte databázi ze seznamu.",
            actionLabel = "Zpět na databáze",
            onAction = { viewModel.navigateTo(VaultScreen.DatabasesList) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = 10.dp)
            .testTag("database_records_list_screen")
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = db.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${dbRecords.size} záznamů • ${db.schema.size} polí",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { viewModel.navigateTo(VaultScreen.DatabaseSchemaBuilder(db.id)) }
            ) {
                Icon(Icons.Default.Build, null, Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Schéma")
            }
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = { viewModel.navigateTo(VaultScreen.RecordEditor(db.id, null)) },
                modifier = Modifier.testTag("add_database_record_btn")
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Záznam")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Fulltextové vyhledávání v polích záznamů…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (db.schema.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                db.schema.forEach { fld ->
                    FilterChip(
                        selected = sortFieldId == fld.id,
                        onClick = { sortFieldId = fld.id },
                        label = { Text("Řadit: ${fld.name}") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (dbRecords.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Storage,
                title = "Žádné záznamy v databázi",
                subtitle = "Přidejte první strukturovaný záznam podle schématu '${db.name}'.",
                actionLabel = "Nový záznam",
                onAction = { viewModel.navigateTo(VaultScreen.RecordEditor(db.id, null)) }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
                modifier = Modifier.fillMaxSize()
            ) {
                items(dbRecords, key = { it.id }) { rec ->
                    val primaryField = db.schema.firstOrNull()
                    val primaryVal = primaryField?.let { rec.values[it.id] }.orEmpty().ifBlank { "Záznam #${rec.id.take(6)}" }

                    SwipeableVaultCard(
                        onClick = { viewModel.navigateTo(VaultScreen.RecordDetail(db.id, rec.id)) },
                        onLongClick = {
                            viewModel.openModalSheet(VaultModalSheet.ContextMenu(rec.id, primaryVal, "record"))
                        },
                        onSwipeLeftAction = { viewModel.deleteDatabaseRecord(rec.id) },
                        onSwipeRightFavorite = {}
                    ) {
                        Column(modifier = Modifier.padding(spacing.cardPadding)) {
                            Text(
                                text = primaryVal,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            db.schema.drop(1).take(3).forEach { fld ->
                                val rawVal = rec.values[fld.id].orEmpty()
                                val displayVal = if (fld.type == DatabaseFieldType.PASSWORD && rawVal.isNotEmpty()) {
                                    "••••••••"
                                } else {
                                    rawVal
                                }
                                if (displayVal.isNotEmpty()) {
                                    Text(
                                        text = "${fld.name}: $displayVal",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
 * Screen #22: Záznam – Editor (dynamically rendered from UserDatabase schema)
 */
@Composable
fun RecordEditorScreen(
    viewModel: OpenVaultViewModel,
    databaseId: String,
    recordId: String?
) {
    val databases by viewModel.databases.collectAsState()
    val records by viewModel.records.collectAsState()
    val files by viewModel.files.collectAsState()

    val db = remember(databases, databaseId) { databases.find { it.id == databaseId } }
    val existing = remember(records, recordId) { records.find { it.id == recordId } }

    val fieldValues = remember(db, existing) {
        mutableStateMapOf<String, String>().apply {
            db?.schema?.forEach { fld ->
                put(fld.id, existing?.values?.get(fld.id) ?: fld.defaultValue)
            }
        }
    }

    if (db == null) return

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("record_editor_screen")
    ) {
        Text(
            text = if (existing == null) "Nový záznam: ${db.name}" else "Upravit záznam: ${db.name}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        db.schema.sortedBy { it.sortOrder }.forEach { fld ->
            val currentVal = fieldValues[fld.id].orEmpty()
            when (fld.type) {
                DatabaseFieldType.BOOLEAN -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(fld.name, style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = currentVal.equals("true", ignoreCase = true),
                            onCheckedChange = { fieldValues[fld.id] = it.toString() }
                        )
                    }
                }

                DatabaseFieldType.PASSWORD -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentVal,
                            onValueChange = { fieldValues[fld.id] = it },
                            label = { Text("${fld.name} (Tajné pole)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                fieldValues[fld.id] = PasswordAnalyzer.generatePassword()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Vygenerovat silné heslo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                DatabaseFieldType.FILE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${fld.name} (VFS Soubor):", style = MaterialTheme.typography.labelLarge)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            files.forEach { vf ->
                                FilterChip(
                                    selected = currentVal == vf.id,
                                    onClick = { fieldValues[fld.id] = vf.id },
                                    label = { Text("📎 ${vf.fileName}") }
                                )
                            }
                        }
                    }
                }

                DatabaseFieldType.NOTE, DatabaseFieldType.JSON -> {
                    OutlinedTextField(
                        value = currentVal,
                        onValueChange = { fieldValues[fld.id] = it },
                        label = { Text("${fld.name} (${fld.type.labelCs})") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    OutlinedTextField(
                        value = currentVal,
                        onValueChange = { fieldValues[fld.id] = it },
                        label = { Text("${fld.name}${if (fld.required) " *" else ""}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Button(
            onClick = {
                viewModel.saveDatabaseRecord(
                    id = existing?.id,
                    databaseId = db.id,
                    values = fieldValues.toMap()
                ) { saved ->
                    viewModel.navigateTo(VaultScreen.RecordDetail(db.id, saved.id))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_database_record_btn")
        ) {
            Icon(Icons.Default.Save, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Zašifrovat a uložit záznam")
        }
    }
}

/**
 * Screen #23: Záznam – Detail
 */
@Composable
fun RecordDetailScreen(
    viewModel: OpenVaultViewModel,
    databaseId: String,
    recordId: String
) {
    val context = LocalContext.current
    val databases by viewModel.databases.collectAsState()
    val records by viewModel.records.collectAsState()
    val files by viewModel.files.collectAsState()

    val db = remember(databases, databaseId) { databases.find { it.id == databaseId } }
    val rec = remember(records, recordId) { records.find { it.id == recordId } }

    if (db == null || rec == null) {
        EmptyStateView(
            icon = Icons.Default.Storage,
            title = "Záznam nenalezen",
            subtitle = "Položka byla smazána.",
            actionLabel = "Zpět",
            onAction = { viewModel.navigateBack() }
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("record_detail_screen")
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = db.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Záznam rev #${rec.rev}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = { viewModel.navigateTo(VaultScreen.RecordEditor(db.id, rec.id)) },
                label = { Text("Upravit") },
                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) }
            )
        }

        db.schema.sortedBy { it.sortOrder }.forEach { fld ->
            val value = rec.values[fld.id].orEmpty()
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${fld.name} (${fld.type.labelCs})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        when (fld.type) {
                            DatabaseFieldType.NOTE -> MarkdownRenderer(markdown = value)
                            DatabaseFieldType.FILE -> {
                                val linkedFile = files.find { it.id == value }
                                Text(linkedFile?.fileName ?: value)
                            }
                            else -> Text(
                                text = value.ifBlank { "—" },
                                fontFamily = if (fld.type == DatabaseFieldType.PASSWORD || fld.type == DatabaseFieldType.JSON)
                                    JetBrainsMonoFontFamily else MaterialTheme.typography.bodyLarge.fontFamily,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    if (fld.type == DatabaseFieldType.URL && value.startsWith("http")) {
                        IconButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
                                }
                            }
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Otevřít URL")
                        }
                    }
                    IconButton(
                        onClick = { viewModel.copySecretToClipboard(fld.name, value) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Kopírovat hodnotu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
