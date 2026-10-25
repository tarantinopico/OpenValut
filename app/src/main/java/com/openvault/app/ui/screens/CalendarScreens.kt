package com.openvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewAgenda
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openvault.app.domain.model.RecurrenceRule
import com.openvault.app.ui.OpenVaultViewModel
import com.openvault.app.ui.components.EmptyStateView
import com.openvault.app.ui.components.SwipeableVaultCard
import com.openvault.app.ui.navigation.VaultModalSheet
import com.openvault.app.ui.navigation.VaultScreen
import com.openvault.app.ui.theme.JetBrainsMonoFontFamily
import com.openvault.app.ui.theme.LocalOpenVaultSpacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Screen #11: Kalendář – Měsíční pohled
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarMonthScreen(viewModel: OpenVaultViewModel) {
    val events by viewModel.events.collectAsState()
    val spacing = LocalOpenVaultSpacing.current

    val todayCal = remember { Calendar.getInstance() }
    var displayedYear by remember { mutableIntStateOf(todayCal.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(todayCal.get(Calendar.MONTH)) }
    var selectedDayOfMonth by remember { mutableIntStateOf(todayCal.get(Calendar.DAY_OF_MONTH)) }

    val monthCal = remember(displayedYear, displayedMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeekOffset = ((monthCal.get(Calendar.DAY_OF_WEEK) + 5) % 7) // Monday = 0

    val monthNamesCs = listOf(
        "Leden", "Únor", "Březen", "Duben", "Květen", "Červen",
        "Červenec", "Srpen", "Září", "Říjen", "Listopad", "Prosinec"
    )
    val dayHeadersCs = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")

    fun dayEpochRange(day: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(displayedYear, displayedMonth, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + 86_400_000L - 1L
        return start to end
    }

    val selectedDayRange = remember(displayedYear, displayedMonth, selectedDayOfMonth) {
        dayEpochRange(selectedDayOfMonth.coerceAtMost(daysInMonth))
    }
    val selectedDayEvents = remember(events, selectedDayRange) {
        events.filter { ev ->
            ev.startAt <= selectedDayRange.second && ev.endAt >= selectedDayRange.first
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = 10.dp)
            .testTag("calendar_month_screen")
    ) {
        // Header with Month Switcher & View Toggle
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (displayedMonth == 0) {
                        displayedMonth = 11
                        displayedYear -= 1
                    } else {
                        displayedMonth -= 1
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Předchozí měsíc")
                }
                Text(
                    text = "${monthNamesCs[displayedMonth]} $displayedYear",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = {
                    if (displayedMonth == 11) {
                        displayedMonth = 0
                        displayedYear += 1
                    } else {
                        displayedMonth += 1
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Další měsíc")
                }
            }

            AssistChip(
                onClick = { viewModel.navigateTo(VaultScreen.CalendarAgenda) },
                label = { Text("Agenda") },
                leadingIcon = { Icon(Icons.Default.ViewAgenda, null, Modifier.size(16.dp)) },
                modifier = Modifier.testTag("switch_to_agenda_btn")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar Grid Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    dayHeadersCs.forEach { dayName ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = dayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                val totalCells = ((firstDayOfWeekOffset + daysInMonth + 6) / 7) * 7
                for (week in 0 until (totalCells / 7)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (dow in 0..6) {
                            val cellIndex = week * 7 + dow
                            val dayNum = cellIndex - firstDayOfWeekOffset + 1
                            if (dayNum in 1..daysInMonth) {
                                val range = dayEpochRange(dayNum)
                                val dayEvents = events.filter {
                                    it.startAt <= range.second && it.endAt >= range.first
                                }
                                val isSelected = dayNum == selectedDayOfMonth
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .combinedClickable(
                                            onClick = { selectedDayOfMonth = dayNum },
                                            onLongClick = {
                                                // Long-press on day = create new event on that day
                                                val targetStart = range.first + 9 * 3_600_000L
                                                viewModel.navigateTo(
                                                    VaultScreen.EventEditor(initialDateMs = targetStart)
                                                )
                                            }
                                        )
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = dayNum.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (dayEvents.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                dayEvents.take(3).forEach { _ ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).height(44.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Události $selectedDayOfMonth. ${monthNamesCs[displayedMonth]} (podržte den pro rychlé vytvoření)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        if (selectedDayEvents.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Event,
                title = "Žádné události v tento den",
                subtitle = "Klepněte níže nebo podržte prst na dni v kalendáři pro vytvoření šifrované události.",
                actionLabel = "Nová událost",
                onAction = {
                    val startMs = selectedDayRange.first + 9 * 3_600_000L
                    viewModel.navigateTo(VaultScreen.EventEditor(initialDateMs = startMs))
                }
            )
        } else {
            val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(selectedDayEvents, key = { it.id }) { ev ->
                    SwipeableVaultCard(
                        onClick = { viewModel.navigateTo(VaultScreen.EventDetail(ev.id)) },
                        onLongClick = {
                            viewModel.openModalSheet(VaultModalSheet.ContextMenu(ev.id, ev.title, "event"))
                        },
                        onSwipeLeftAction = { viewModel.deleteEvent(ev.id) },
                        onSwipeRightFavorite = {}
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = ev.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (ev.allDay) "Celý den • ${ev.recurrenceRule.labelCs}"
                                    else "${timeFormatter.format(Date(ev.startAt))} – ${timeFormatter.format(Date(ev.endAt))} • ${ev.recurrenceRule.labelCs}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen #12: Kalendář – Agenda
 */
@Composable
fun CalendarAgendaScreen(viewModel: OpenVaultViewModel) {
    val events by viewModel.events.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val spacing = LocalOpenVaultSpacing.current
    val dateFormatter = remember { SimpleDateFormat("EEE dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = 10.dp)
            .testTag("calendar_agenda_screen")
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Chronologická agenda (${events.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            AssistChip(
                onClick = { viewModel.navigateTo(VaultScreen.CalendarMonth) },
                label = { Text("Měsíční mřížka") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)) }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (events.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Event,
                title = "Agenda je prázdná",
                subtitle = "Naplánujte šifrovanou událost nebo importujte události ze systémového kalendáře.",
                actionLabel = "Přidat událost",
                onAction = { viewModel.navigateTo(VaultScreen.EventEditor()) }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
                modifier = Modifier.fillMaxSize()
            ) {
                items(events, key = { it.id }) { ev ->
                    SwipeableVaultCard(
                        onClick = { viewModel.navigateTo(VaultScreen.EventDetail(ev.id)) },
                        onLongClick = {
                            viewModel.openModalSheet(VaultModalSheet.ContextMenu(ev.id, ev.title, "event"))
                        },
                        onSwipeLeftAction = { viewModel.deleteEvent(ev.id) },
                        onSwipeRightFavorite = {}
                    ) {
                        Column(modifier = Modifier.padding(spacing.cardPadding)) {
                            Text(
                                text = dateFormatter.format(Date(ev.startAt)),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = ev.title, style = MaterialTheme.typography.titleMedium)
                            if (ev.description.isNotBlank()) {
                                Text(
                                    text = ev.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            val linkedNotes = notes.filter { it.id in ev.linkedNoteIds }
                            if (linkedNotes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    linkedNotes.forEach { n ->
                                        AssistChip(
                                            onClick = { viewModel.navigateTo(VaultScreen.NoteDetail(n.id)) },
                                            label = { Text("📝 ${n.title}") }
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
 * Screen #13: Událost – Editor
 */
@Composable
fun EventEditorScreen(
    viewModel: OpenVaultViewModel,
    eventId: String?,
    initialDateMs: Long?
) {
    val events by viewModel.events.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val records by viewModel.records.collectAsState()

    val existing = remember(events, eventId) { events.find { it.id == eventId } }
    var title by remember(existing) { mutableStateOf(existing?.title ?: "") }
    var description by remember(existing) { mutableStateOf(existing?.description ?: "") }
    var startAt by remember(existing) {
        mutableLongStateOf(existing?.startAt ?: initialDateMs ?: (System.currentTimeMillis() + 3_600_000L))
    }
    var durationMinutes by remember { mutableIntStateOf(60) }
    var allDay by remember(existing) { mutableStateOf(existing?.allDay ?: false) }
    var recurrence by remember(existing) {
        mutableStateOf(existing?.recurrenceRule ?: RecurrenceRule.NONE)
    }
    var selectedReminders by remember(existing) {
        mutableStateOf(existing?.remindersMinutesBefore ?: listOf(15))
    }
    var linkedNotes by remember(existing) {
        mutableStateOf(existing?.linkedNoteIds ?: emptyList())
    }
    var linkedRecords by remember(existing) {
        mutableStateOf(existing?.linkedRecordIds ?: emptyList())
    }

    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("event_editor_screen")
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Název šifrované události") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("event_editor_title_input")
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Popis a poznámky k události") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Začátek: ${dateFormatter.format(Date(startAt))}",
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Quick duration chips (15m, 30m, 1h, 2h, Celý den)
        Text("Trvání události:", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf(15 to "15 m", 30 to "30 m", 60 to "1 h", 120 to "2 h").forEach { (mins, label) ->
                FilterChip(
                    selected = !allDay && durationMinutes == mins,
                    onClick = {
                        allDay = false
                        durationMinutes = mins
                    },
                    label = { Text(label) }
                )
            }
            FilterChip(
                selected = allDay,
                onClick = { allDay = !allDay },
                label = { Text("Celý den") }
            )
        }

        // Reminders (5m, 15m, 30m, 1h, 1d)
        Text("Šifrované připomínky předem:", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf(5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 hod", 1440 to "1 den").forEach { (mins, label) ->
                val active = mins in selectedReminders
                FilterChip(
                    selected = active,
                    onClick = {
                        selectedReminders = if (active) selectedReminders - mins else selectedReminders + mins
                    },
                    label = { Text(label) }
                )
            }
        }

        // Recurrence
        Text("Opakování:", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            RecurrenceRule.entries.forEach { rule ->
                FilterChip(
                    selected = recurrence == rule,
                    onClick = { recurrence = rule },
                    label = { Text(rule.labelCs) }
                )
            }
        }

        // Link Notes
        if (notes.isNotEmpty()) {
            Text("Propojit s poznámkami:", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                notes.forEach { n ->
                    val linked = n.id in linkedNotes
                    FilterChip(
                        selected = linked,
                        onClick = {
                            linkedNotes = if (linked) linkedNotes - n.id else linkedNotes + n.id
                        },
                        label = { Text(n.title) }
                    )
                }
            }
        }

        // Link Database Records
        if (records.isNotEmpty()) {
            Text("Propojit se záznamy databází:", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                records.forEach { rec ->
                    val linked = rec.id in linkedRecords
                    val label = rec.values.values.firstOrNull() ?: rec.id.take(8)
                    FilterChip(
                        selected = linked,
                        onClick = {
                            linkedRecords = if (linked) linkedRecords - rec.id else linkedRecords + rec.id
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(onClick = { viewModel.navigateBack() }) {
                Text("Zrušit")
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = {
                    val endAt = startAt + durationMinutes * 60_000L
                    viewModel.saveEvent(
                        id = existing?.id,
                        title = title,
                        description = description,
                        startAt = startAt,
                        endAt = endAt,
                        allDay = allDay,
                        remindersMinutesBefore = selectedReminders,
                        recurrenceRule = recurrence,
                        linkedNoteIds = linkedNotes,
                        linkedRecordIds = linkedRecords
                    ) { saved ->
                        viewModel.navigateTo(VaultScreen.EventDetail(saved.id))
                    }
                },
                modifier = Modifier.testTag("event_editor_save_btn")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Uložit událost")
            }
        }
    }
}

/**
 * Screen #14: Událost – Detail
 */
@Composable
fun EventDetailScreen(
    viewModel: OpenVaultViewModel,
    eventId: String
) {
    val events by viewModel.events.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val records by viewModel.records.collectAsState()

    val event = remember(events, eventId) { events.find { it.id == eventId } }
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    if (event == null) {
        EmptyStateView(
            icon = Icons.Default.Event,
            title = "Událost nenalezena",
            subtitle = "Položka byla smazána.",
            actionLabel = "Zpět do kalendáře",
            onAction = { viewModel.navigateTo(VaultScreen.CalendarMonth) }
        )
        return
    }

    val diffMinutes = ((event.startAt - System.currentTimeMillis()) / 60_000L)
    val countdownLabel = when {
        diffMinutes > 60 -> "Začíná za ${diffMinutes / 60} hod ${diffMinutes % 60} min"
        diffMinutes in 1..60 -> "Začíná za $diffMinutes min"
        diffMinutes in -60..0 -> "Právě probíhá"
        else -> "Proběhlá událost"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("event_detail_screen")
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$countdownLabel • Revize #${event.rev}",
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            AssistChip(
                onClick = { viewModel.navigateTo(VaultScreen.EventEditor(event.id)) },
                label = { Text("Upravit") },
                leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = { viewModel.exportEventToSystemCalendar(event) },
                label = { Text("Export do systémového kalendáře") },
                leadingIcon = { Icon(Icons.Default.IosShare, null, Modifier.size(16.dp)) },
                modifier = Modifier.testTag("export_event_system_calendar_btn")
            )
            AssistChip(
                onClick = {
                    viewModel.deleteEvent(event.id)
                    viewModel.navigateBack()
                },
                label = { Text("Smazat") },
                leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)) }
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Začátek: ${dateFormatter.format(Date(event.startAt))}")
                Text("Konec: ${dateFormatter.format(Date(event.endAt))}")
                Text("Opakování: ${event.recurrenceRule.labelCs}")
                Text(
                    "Připomínky: ${
                        if (event.remindersMinutesBefore.isEmpty()) "Žádné"
                        else event.remindersMinutesBefore.joinToString(", ") { "$it min předem" }
                    }"
                )
                if (event.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        val linkedNotes = notes.filter { it.id in event.linkedNoteIds }
        if (linkedNotes.isNotEmpty()) {
            Text("Propojené šifrované poznámky:", style = MaterialTheme.typography.titleMedium)
            linkedNotes.forEach { n ->
                AssistChip(
                    onClick = { viewModel.navigateTo(VaultScreen.NoteDetail(n.id)) },
                    label = { Text(n.title) },
                    leadingIcon = { Icon(Icons.Default.Description, null, Modifier.size(16.dp)) }
                )
            }
        }

        val linkedRecs = records.filter { it.id in event.linkedRecordIds }
        if (linkedRecs.isNotEmpty()) {
            Text("Propojené databázové záznamy:", style = MaterialTheme.typography.titleMedium)
            linkedRecs.forEach { r ->
                AssistChip(
                    onClick = { viewModel.navigateTo(VaultScreen.RecordDetail(r.databaseId, r.id)) },
                    label = { Text(r.values.values.firstOrNull() ?: r.id.take(8)) },
                    leadingIcon = { Icon(Icons.Default.Storage, null, Modifier.size(16.dp)) }
                )
            }
        }
    }
}
