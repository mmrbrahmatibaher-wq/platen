package com.mahdi.platen.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.mahdi.platen.R
import com.mahdi.platen.ui.components.paperShadow
import com.mahdi.platen.ui.components.paperVignette
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mahdi.platen.appContainer
import com.mahdi.platen.data.Note
import com.mahdi.platen.domain.CreateResult
import com.mahdi.platen.domain.NoteLimits
import com.mahdi.platen.domain.NoteTitle
import com.mahdi.platen.domain.TypewriterEngine
import com.mahdi.platen.ui.components.rememberPaperBrush
import com.mahdi.platen.ui.theme.LocalPalette
import com.mahdi.platen.ui.theme.TypewriterText
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun NotesRoute(onOpen: (Long) -> Unit, onOpenSettings: () -> Unit, onOpenStudio: () -> Unit) {
    val context = LocalContext.current
    val c = remember { context.appContainer() }
    val vm: NotesViewModel = viewModel(factory = viewModelFactory { initializer { NotesViewModel(c.notes, c.settings) } })
    val notes by vm.notes.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var limitDialog by rememberSaveable { mutableStateOf(false) }
    NotesListScreen(
        notes = notes,
        studio = settings.studio,
        onNew = {
            scope.launch {
                when (val r = vm.create()) {
                    is CreateResult.Created -> onOpen(r.id)
                    CreateResult.LimitReached -> limitDialog = true
                }
            }
        },
        onOpen = onOpen,
        onDelete = vm::delete,
        onOpenSettings = onOpenSettings,
        onOpenStudio = onOpenStudio,
    )
    if (limitDialog) {
        AlertDialog(
            onDismissRequest = { limitDialog = false },
            modifier = Modifier.testTag("limitDialog"),
            title = { Text("Three sheets on the desk") },
            text = { Text("The free version keeps ${NoteLimits.FREE_ACTIVE_NOTES} active notes. Finish or remove one, or open Studio for more.") },
            confirmButton = { TextButton(onClick = { limitDialog = false; onOpenStudio() }) { Text("See Studio") } },
            dismissButton = { TextButton(onClick = { limitDialog = false }) { Text("OK") } },
        )
    }
}

@Composable
fun NotesListScreen(
    notes: List<Note>?,
    studio: Boolean,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStudio: () -> Unit,
) {
    val palette = LocalPalette.current
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    // The inked wordmark (same bitmap as on the machine and the icon).
                    Image(
                        painterResource(R.drawable.wordmark), contentDescription = "platen",
                        colorFilter = if (palette.isDark) ColorFilter.tint(palette.onBackground) else null,
                        modifier = Modifier.height(40.dp),
                    )
                }
                Surface(
                    onClick = onOpenStudio,
                    shape = RoundedCornerShape(50),
                    color = if (studio) palette.indicator else palette.card,
                    contentColor = if (studio) palette.paper else palette.onBackground,
                    modifier = Modifier.testTag("studioChip"),
                ) {
                    val count = notes?.size ?: 0
                    Text(
                        if (studio) "Studio" else "Free · $count/${NoteLimits.FREE_ACTIVE_NOTES}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings")) {
                    Icon(Icons.Outlined.Settings, "Settings", tint = palette.onBackground)
                }
            }
            Text(
                "Short notes. Real keys. No going back.",
                style = MaterialTheme.typography.bodyMedium, color = palette.muted,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(10.dp))
            when {
                notes == null -> Unit
                notes.isEmpty() -> EmptyDesk(onNew, Modifier.weight(1f))
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).testTag("notesList"),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(notes, key = { it.id }) { n ->
                        NoteCard(n, onOpen = { onOpen(n.id) }, onDelete = { pendingDelete = n.id })
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text("Insert paper") },
            containerColor = palette.roller,
            contentColor = palette.capsule,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).testTag("newNote"),
        )
    }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Throw this sheet away?") },
            text = { Text("The note will be deleted. There's no undo on a typewriter.") },
            confirmButton = {
                TextButton(onClick = { onDelete(id); pendingDelete = null }, modifier = Modifier.testTag("confirmDelete")) {
                    Text("Delete", color = palette.indicator)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun EmptyDesk(onNew: () -> Unit, modifier: Modifier) {
    val palette = LocalPalette.current
    Column(modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("A blank sheet is waiting.", style = MaterialTheme.typography.titleLarge, color = palette.onBackground, modifier = Modifier.testTag("emptyState"))
        Spacer(Modifier.height(8.dp))
        Text("Letters, lists, small thoughts. Up to 3000 characters each.", style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onNew) { Text("Start typing", color = palette.indicator) }
    }
}

@Composable
private fun NoteCard(note: Note, onOpen: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalPalette.current
    val brush = rememberPaperBrush(palette.paperTex)
    val title = remember(note.glyphs) { NoteTitle.display(note.glyphs) }
    val preview = remember(note.glyphs) {
        buildAnnotatedString {
            NoteTitle.body(note.glyphs).take(400).forEach { g ->
                if (g.struck) withStyle(SpanStyle(background = palette.ink.copy(alpha = 0.55f))) { append(g.over ?: g.text) } else append(g.text)
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .paperShadow(palette.isDark)
            .clip(RoundedCornerShape(3.dp))
            .background(brush)
            .paperVignette()
            .clickable(onClick = onOpen)
            .testTag("note_${note.id}")
            .padding(start = 18.dp, top = 14.dp, bottom = 6.dp, end = 4.dp),
    ) {
        // The first line is the note's name, typed bolder; the rest is the preview.
        Text(
            if (note.glyphs.isEmpty()) "(blank sheet)" else title,
            style = TypewriterText.copy(fontSize = 17.sp, lineHeight = 24.sp, color = palette.ink, fontWeight = FontWeight.Bold),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 14.dp).testTag("noteTitle_${note.id}"),
        )
        if (preview.isNotEmpty()) {
            Text(
                preview,
                style = TypewriterText.copy(fontSize = 15.sp, lineHeight = 22.sp, color = palette.ink),
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 14.dp, top = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.updatedAt)) +
                    "  ·  ${note.activeCount}/${TypewriterEngine.MAX_ACTIVE}",
                style = MaterialTheme.typography.labelMedium, color = palette.muted, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_${note.id}")) {
                Icon(Icons.Outlined.Delete, "Delete note", tint = palette.muted)
            }
        }
    }
}
