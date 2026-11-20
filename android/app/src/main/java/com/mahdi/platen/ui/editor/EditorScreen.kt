package com.mahdi.platen.ui.editor

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import com.mahdi.platen.ui.stage.StageGeometry
import com.mahdi.platen.domain.MorseState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mahdi.platen.appContainer
import com.mahdi.platen.data.PaperTheme
import com.mahdi.platen.data.Settings
import com.mahdi.platen.domain.CorrectionMode
import com.mahdi.platen.domain.CreateResult
import com.mahdi.platen.domain.NoteLimits
import com.mahdi.platen.domain.NoteTitle
import com.mahdi.platen.domain.Prompts
import com.mahdi.platen.domain.TypeEvent
import com.mahdi.platen.export.ExportColors
import com.mahdi.platen.export.ImageFormat
import com.mahdi.platen.export.NoteExporter
import com.mahdi.platen.export.PaperSize
import com.mahdi.platen.ui.lamp.LampControls
import com.mahdi.platen.ui.stage.LampState
import com.mahdi.platen.ui.stage.TypewriterStage
import com.mahdi.platen.ui.theme.CourierPrime
import com.mahdi.platen.ui.theme.LocalPalette
import com.mahdi.platen.ui.theme.TypewriterText
import com.mahdi.platen.ui.theme.paletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.Locale

@Composable
fun EditorRoute(
    noteId: Long,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStudio: () -> Unit,
    onNewSheet: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val c = remember { context.appContainer() }
    val vm: EditorViewModel = viewModel(
        key = "editor-$noteId",
        factory = viewModelFactory { initializer { EditorViewModel(noteId, c.notes, c.settings, c.sound, c.appScope) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val palette = LocalPalette.current
    var limitDialog by rememberSaveable { mutableStateOf(false) }

    // Never lose the last keystrokes when the app is backgrounded inside the autosave window.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { c.appScope.launch { vm.saveNow() } }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            // A short tick per escapement step, a stronger thump for the carriage return.
            if (vm.settings.value.haptics && e != TypeEvent.BELL) {
                view.performHapticFeedback(
                    if (e == TypeEvent.LIMIT || e == TypeEvent.RETURN) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP,
                )
            }
        }
    }

    EditorScreen(
        state = state,
        settings = settings,
        onFieldChange = vm::onFieldChange,
        onInsert = vm::insertText,
        onCharsPerLine = vm::setCharsPerLine,
        onSettingsChange = vm::updateSettings,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onOpenStudio = onOpenStudio,
        onMorseKey = vm::typeKeys,
        onExitMorse = vm::exitMorse,
        onNewSheet = {
            scope.launch {
                vm.saveNow()
                when (val r = c.notes.create(settings.studio)) {
                    is CreateResult.Created -> onNewSheet(r.id)
                    CreateResult.LimitReached -> limitDialog = true
                }
            }
        },
        onExport = { choice ->
            if (choice.paper != null && !settings.studio) {
                onOpenStudio()
            } else {
                scope.launch {
                    vm.saveNow()
                    val glyphs = vm.currentGlyphs()
                    val stem = NoteTitle.fileStem(glyphs, noteId)
                    val title = NoteTitle.display(glyphs)
                    val colors = ExportColors(palette.paper.toArgb(), palette.ink.toArgb(), palette.background.toArgb(), palette.muted.toArgb(), palette.isDark, palette.paperTex)
                    val result = runCatching {
                        withContext(Dispatchers.Default) {
                            if (choice.paper != null) {
                                val f = File(NoteExporter.exportDir(context), "$stem-${choice.paper.name.lowercase()}.pdf")
                                NoteExporter.writePdf(context, glyphs, noteId, choice.paper, f)
                                f to "application/pdf"
                            } else {
                                val bmp = NoteExporter.renderImage(context, glyphs, noteId, choice.image!!, colors)
                                NoteExporter.saveImage(context, bmp, "$stem-${choice.image.name.lowercase()}.png") to "image/png"
                            }
                        }
                    }
                    result.onSuccess { (file, mime) -> context.startActivity(NoteExporter.shareIntent(context, file, mime, title)) }
                        .onFailure { Toast.makeText(context, "Couldn't export: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }
        },
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

data class ExportChoice(val image: ImageFormat? = null, val paper: PaperSize? = null)

/** Panels that open beside the right-hand toolbar. */
enum class ToolPanel { CHARMS, THEME, WRITING, LAMP, SOUND }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    settings: Settings,
    onFieldChange: (TextFieldValue) -> Unit,
    onInsert: (String) -> Unit,
    onCharsPerLine: (Int) -> Unit,
    onSettingsChange: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onExport: (ExportChoice) -> Unit,
    autoFocus: Boolean = true,
    onOpenStudio: () -> Unit = {},
    onNewSheet: () -> Unit = {},
    /** Hard on/off cursor blink while idle (screenshots turn it off for a deterministic frame). */
    cursorBlink: Boolean = true,
    initialPanel: ToolPanel? = null,
    onMorseKey: (String) -> Unit = {},
    onExitMorse: () -> Unit = {},
    initialMorseCollapsed: Boolean = false,
) {
    val palette = LocalPalette.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var showPrompt by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf(initialPanel) }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var morseCollapsed by rememberSaveable { mutableStateOf(initialMorseCollapsed) }

    fun summonKeyboard() {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    LaunchedEffect(state.loading) {
        if (!state.loading && autoFocus) summonKeyboard()
    }
    // The toolbar steps back while you type and returns when you pause.
    var typing by remember { mutableStateOf(false) }
    LaunchedEffect(state.strokes) {
        if (state.strokes == 0L) return@LaunchedEffect
        typing = true; panel = null
        delay(1800); typing = false
    }
    val toolbarFade by animateFloatAsState(if (typing) 0.32f else 1f, tween(350), label = "toolbar")
    // Layout family follows the window, not the space left over by the keyboard.
    val config = LocalConfiguration.current
    val portrait = config.screenHeightDp > config.screenWidthDp
    val stageBg = if (portrait) lerp(palette.stageSide, Color(0xFF1B1B1B), 0.8f) else palette.stageSide

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(stageBg)
            // An open keyboard pushes the whole machine up, so the typing line stays in view.
            .imePadding(),
    ) {
        val w = maxWidth
        val h = maxHeight
        val wide = w > h && w >= 600.dp
        val density = LocalDensity.current
        val geo = with(density) { StageGeometry.of(w.toPx(), h.toPx(), density.density, density.fontScale, portrait) }
        TypewriterStage(
            portrait = portrait,
            glyphs = state.glyphs,
            cursor = state.cursor,
            seed = state.noteId,
            strokes = state.strokes,
            lamp = LampState(settings.lampOn, settings.lampWarmth, settings.lampIntensity, settings.lampBeam),
            charms = settings.charms,
            blink = cursorBlink,
            showPrompt = state.glyphs.isEmpty() && !state.loading,
            onColumns = onCharsPerLine,
            onTapPaper = { panel = null; summonKeyboard() },
            onTapCarriage = { summonKeyboard() },
            onLongPressCarriage = { onSettingsChange { it.copy(lampOn = !it.lampOn) } },
            onPrompt = { showPrompt = true },
        )

        val toolbarAlpha = if (portrait) 0.6f + 0.4f * toolbarFade else toolbarFade
        // ---- top chrome. Never dimmed by the lamp.
        if (portrait) {
            // Portrait: Box left; share + Studio right, the counter under them.
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                StagePill("Box", Icons.Outlined.Inventory2, "back", false, onBack)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShareButton(palette.stageInk, 21.dp) { showExport = true }
                        Spacer(Modifier.width(6.dp))
                        StagePill("Studio", Icons.Outlined.WorkspacePremium, "studio", false, onOpenStudio)
                    }
                    Spacer(Modifier.height(8.dp))
                    Counter(state, 11.sp)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = if (wide) 18.dp else 12.dp, vertical = if (wide) 26.dp else 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                StagePill("Box", Icons.Outlined.Inventory2, "back", wide, onBack)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    StagePill("Studio", Icons.Outlined.WorkspacePremium, "studio", wide, onOpenStudio)
                    Spacer(Modifier.height(if (wide) 16.dp else 10.dp))
                    Counter(state, if (wide) 13.sp else 11.sp)
                    Spacer(Modifier.height(h * 0.05f))
                    ShareButton(palette.stageInk, if (wide) 25.dp else 22.dp) { showExport = true }
                }
            }
        }

        val panelBody: @Composable () -> Unit = {
            when (panel) {
                ToolPanel.LAMP -> LampControls(
                    warmth = settings.lampWarmth, intensity = settings.lampIntensity, beam = settings.lampBeam,
                    onWarmth = { v -> onSettingsChange { it.copy(lampWarmth = v) } },
                    onIntensity = { v -> onSettingsChange { it.copy(lampIntensity = v) } },
                    onBeam = { v -> onSettingsChange { it.copy(lampBeam = v) } },
                    onDone = { panel = null },
                )
                ToolPanel.CHARMS -> PanelCard("Charms", "panel_charms", onClose = { panel = null }) {
                    PanelToggle("Charms on the carriage", settings.charms, "panel_sw_charms") { v -> onSettingsChange { it.copy(charms = v) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        palette.charms.distinct().forEach { res ->
                            Image(painterResource(res), null, Modifier.size(34.dp).alpha(if (settings.charms) 1f else 0.4f))
                        }
                    }
                }
                ToolPanel.THEME -> PanelCard("Paper & machine", "panel_theme", onClose = { panel = null }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PaperTheme.entries.forEach { t -> ThemeChip(t, settings.theme == t) { onSettingsChange { it.copy(theme = t) } } }
                    }
                }
                ToolPanel.WRITING -> PanelCard("Writing", "panel_writing", onClose = { panel = null }) {
                    PanelToggle("Autocorrect", settings.autocorrect, "panel_sw_autocorrect") { v -> onSettingsChange { it.copy(autocorrect = v) } }
                    PanelToggle("Backspace erases", settings.backspaceErases, "panel_sw_erase") { v ->
                        onSettingsChange { it.copy(correction = if (v) CorrectionMode.DELETE else CorrectionMode.STRIKE) }
                    }
                    Text(
                        if (settings.backspaceErases) "Backspace removes the last letter, across lines too. Hold it to keep erasing."
                        else "Backspace steps the carriage back; typing over a letter overstrikes it.",
                        fontFamily = CourierPrime, fontSize = 11.sp, color = PanelInk.copy(alpha = 0.6f),
                    )
                    Text(
                        "The first line is the title.",
                        fontFamily = CourierPrime, fontSize = 11.sp, color = PanelInk.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(onClick = { panel = null; showPrompt = true }, modifier = Modifier.testTag("panel_prompt")) {
                        Text("Need a first line?", color = Color(0xFFFFC27A), fontFamily = CourierPrime)
                    }
                }
                ToolPanel.SOUND -> PanelCard("Sound & feel", "panel_sound", onClose = { panel = null }) {
                    PanelToggle("Key sounds", settings.sound, "panel_sw_sound") { v -> onSettingsChange { it.copy(sound = v) } }
                    PanelToggle("Play in silent mode", settings.playInSilentMode, "panel_sw_silent", enabled = settings.sound) { v -> onSettingsChange { it.copy(playInSilentMode = v) } }
                    PanelToggle("Haptics", settings.haptics, "panel_sw_haptics") { v -> onSettingsChange { it.copy(haptics = v) } }
                }
                null -> Unit
            }
        }

        if (portrait) {
            // ---- portrait toolbar: a cream pill right under the rod, above the keyboard.
            val pillTop = with(density) { geo.toolbarTop.toDp() }
            val pillH = with(density) { geo.toolbarHeight.toDp() }
            val pillW = (w * 0.78f).coerceIn(300.dp, 620.dp).coerceAtMost(w - 16.dp)
            val pillBg = palette.capsule
            val pillInk = if (pillBg.luminance() > 0.45f) Color(0xFF4A4541) else Color(0xFFEDE6DA)
            Row(
                Modifier
                    .offset(x = (w - pillW) / 2, y = pillTop)
                    .size(pillW, pillH)
                    .alpha(toolbarAlpha)
                    .shadow(3.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(pillBg)
                    .padding(horizontal = 8.dp)
                    .testTag("toolbar"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val itemW = (pillW - 16.dp - 13.dp) / 9
                @Composable fun item(icon: ImageVector, label: String, tag: String, active: Boolean = false, onLong: (() -> Unit)? = null, onClick: () -> Unit) =
                    ToolButton(icon, label, tag, pillH, active, activeTint = Color(0xFFC9731F), onLongClick = onLong, width = itemW, tint = pillInk, onClick = onClick)
                item(if (collapsed) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, if (collapsed) "Show tools" else "Hide tools", "tb_collapse") {
                    collapsed = !collapsed; panel = null
                }
                if (!collapsed) {
                    item(Icons.Outlined.Inventory2, "Box: all notes", "tb_box", onClick = onBack)
                    item(Icons.Outlined.LocalFlorist, "Charms", "tb_charms", panel == ToolPanel.CHARMS) { panel = toggle(panel, ToolPanel.CHARMS) }
                    item(Icons.AutoMirrored.Outlined.NoteAdd, "New sheet", "tb_newSheet", onClick = onNewSheet)
                    item(Icons.Outlined.DonutLarge, "Paper and machine", "tb_theme", panel == ToolPanel.THEME) { panel = toggle(panel, ToolPanel.THEME) }
                    item(
                        Icons.Outlined.Lightbulb, if (settings.lampOn) "Night lamp off" else "Night lamp on", "tb_lamp",
                        active = settings.lampOn || panel == ToolPanel.LAMP, onLong = { panel = toggle(panel, ToolPanel.LAMP) },
                    ) { onSettingsChange { it.copy(lampOn = !it.lampOn) } }
                    item(Icons.Outlined.Tune, "Settings", "tb_settings", onClick = onOpenSettings)
                    item(Icons.Outlined.NotificationsNone, "Sound and feel", "tb_sound", panel == ToolPanel.SOUND) { panel = toggle(panel, ToolPanel.SOUND) }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(1.dp, pillH * 0.55f).background(pillInk.copy(alpha = 0.18f)))
                    Spacer(Modifier.width(6.dp))
                    item(Icons.Outlined.Explore, "Writing", "tb_writing", panel == ToolPanel.WRITING) { panel = toggle(panel, ToolPanel.WRITING) }
                }
            }
            // ---- panels open above the pill.
            AnimatedVisibility(
                visible = panel != null,
                enter = fadeIn(tween(160)), exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = h - pillTop + 10.dp),
            ) { panelBody() }
        } else {
            // ---- landscape toolbar: a vertical capsule in the margin, centred at 92.4 % of the width.
            val itemH = ((h * 0.585f) / 9).coerceIn(34.dp, 60.dp)
            val tbW = if (wide) 46.dp else 40.dp
            val tbX = w * 0.924f - tbW / 2
            val tbY = (h * 0.31f).coerceAtLeast(150.dp).coerceAtMost(h - itemH * 9 - 8.dp)
            Column(
                Modifier
                    .offset(x = tbX, y = tbY)
                    .width(tbW)
                    .alpha(toolbarAlpha)
                    .clip(RoundedCornerShape(tbW / 2))
                    .background(Color.White.copy(alpha = 0.018f))
                    .border(1.dp, palette.stageInk.copy(alpha = 0.13f), RoundedCornerShape(tbW / 2))
                    .padding(vertical = 4.dp)
                    .testTag("toolbar"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ToolButton(if (collapsed) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, if (collapsed) "Show tools" else "Hide tools", "tb_collapse", itemH) {
                    collapsed = !collapsed; panel = null
                }
                if (!collapsed) {
                    ToolButton(Icons.Outlined.Inventory2, "Box: all notes", "tb_box", itemH, onClick = onBack)
                    ToolButton(Icons.Outlined.LocalFlorist, "Charms", "tb_charms", itemH, panel == ToolPanel.CHARMS) { panel = toggle(panel, ToolPanel.CHARMS) }
                    ToolButton(Icons.AutoMirrored.Outlined.NoteAdd, "New sheet", "tb_newSheet", itemH, onClick = onNewSheet)
                    ToolButton(Icons.Outlined.DonutLarge, "Paper and machine", "tb_theme", itemH, panel == ToolPanel.THEME) { panel = toggle(panel, ToolPanel.THEME) }
                    ToolButton(Icons.Outlined.Gesture, "Writing", "tb_writing", itemH, panel == ToolPanel.WRITING) { panel = toggle(panel, ToolPanel.WRITING) }
                    ToolButton(
                        Icons.Outlined.Lightbulb, if (settings.lampOn) "Night lamp off" else "Night lamp on", "tb_lamp", itemH,
                        active = settings.lampOn || panel == ToolPanel.LAMP,
                        activeTint = Color(0xFFFFC27A),
                        onLongClick = { panel = toggle(panel, ToolPanel.LAMP) },
                    ) { onSettingsChange { it.copy(lampOn = !it.lampOn) } }
                    ToolButton(Icons.Outlined.Tune, "Settings", "tb_settings", itemH, onClick = onOpenSettings)
                    ToolButton(Icons.Outlined.Explore, "Sound and feel", "tb_sound", itemH, panel == ToolPanel.SOUND) { panel = toggle(panel, ToolPanel.SOUND) }
                }
            }
            // ---- tool panels, opening to the left of the toolbar.
            AnimatedVisibility(
                visible = panel != null,
                enter = fadeIn(tween(160)), exit = fadeOut(tween(120)),
                modifier = Modifier.offset(x = (tbX - 12.dp - PanelWidth).coerceAtLeast(8.dp), y = tbY),
            ) { panelBody() }
        }

        // ---- Morse reference chart: only while Morse mode is on, kept clear of the typing line.
        val morseOn = state.morse as? MorseState.On
        if (morseOn != null) {
            val tbX = w * 0.924f - (if (wide) 46.dp else 40.dp) / 2
            // Leave the typing line and the two lines above it (the last translation) uncovered.
            val bottom = with(density) { (geo.baseline - geo.fontPx - 2.6f * geo.lineHeight).toDp() }
            val top = if (wide) h * 0.10f else 104.dp
            val mx = if (wide) (tbX - 12.dp - 300.dp).coerceAtLeast(8.dp) else 10.dp
            val mw = if (wide) 300.dp else if (portrait) (w - 20.dp).coerceAtMost(360.dp) else (tbX - 8.dp - mx).coerceAtMost(330.dp)
            val avail = bottom - top
            val tile = ((avail - morseGridHeight(0.dp)) / MorseRows).takeIf { it >= 34.dp }?.coerceAtMost(52.dp)
            Box(Modifier.statusBarsPadding().offset(x = mx, y = top)) {
                if (morseCollapsed) {
                    MorsePill(onExpand = { morseCollapsed = false })
                } else {
                    MorsePanel(
                        dir = morseOn.dir, enabled = !state.printing, width = mw, tileHeight = tile,
                        onKey = onMorseKey, onCollapse = { morseCollapsed = true }, onExit = onExitMorse,
                    )
                }
            }
        }

        // The invisible field that receives every keystroke from the soft or hardware keyboard.
        BasicTextField(
            value = state.field,
            onValueChange = onFieldChange,
            enabled = !state.loading && !state.missing,
            keyboardOptions = KeyboardOptions(
                capitalization = if (settings.autocorrect) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                autoCorrect = settings.autocorrect,
                keyboardType = KeyboardType.Text,
            ),
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = TypewriterText.copy(color = Color.Transparent),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focus)
                .testTag("typeInput"),
        )
    }

    if (showPrompt) {
        PromptDialog(
            onUse = { p -> showPrompt = false; onInsert(p); summonKeyboard() },
            onDismiss = { showPrompt = false },
        )
    }
    if (showExport) {
        ExportDialog(
            studio = settings.studio,
            onChoose = { showExport = false; onExport(it) },
            onDismiss = { showExport = false },
        )
    }
}

private fun toggle(cur: ToolPanel?, p: ToolPanel): ToolPanel? = if (cur == p) null else p

@Composable
private fun Counter(state: EditorUiState, size: androidx.compose.ui.unit.TextUnit) {
    val palette = LocalPalette.current
    Text(
        "${grouped(state.activeCount)} / ${grouped(state.limit)}",
        fontFamily = CourierPrime, fontSize = size,
        color = if (state.activeCount >= state.limit - 100) Color(0xFFE5654D) else palette.stageInk.copy(alpha = 0.75f),
        modifier = Modifier.padding(end = 4.dp).testTag("counter"),
    )
}

@Composable
private fun ShareButton(ink: Color, iconSize: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).testTag("export")
            .semantics { contentDescription = "Share or export" },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.IosShare, null, tint = ink.copy(alpha = 0.9f), modifier = Modifier.size(iconSize)) }
}

private fun grouped(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)

private val PanelWidth = 248.dp
private val PanelInk = Color(0xFFF1E9DC)

@Composable
private fun StagePill(label: String, icon: ImageVector, tag: String, wide: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, p.stageInk.copy(alpha = 0.16f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = if (wide) 20.dp else 14.dp, vertical = if (wide) 12.dp else 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.stageAccent, modifier = Modifier.size(if (wide) 19.dp else 16.dp))
        Spacer(Modifier.width(if (wide) 10.dp else 8.dp))
        Text(label, color = p.stageAccent, fontFamily = CourierPrime, fontSize = if (wide) 17.sp else 14.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    tag: String,
    height: Dp,
    active: Boolean = false,
    activeTint: Color = Color(0xFFF1E9DC),
    onLongClick: (() -> Unit)? = null,
    width: Dp = 44.dp,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(width, height)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) activeTint else tint ?: p.stageInk.copy(alpha = 0.5f), modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun PanelCard(title: String, tag: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .widthIn(max = PanelWidth)
            .width(PanelWidth)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xF21E1C1A))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PanelInk, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose, modifier = Modifier.testTag("panelDone")) { Text("Done", color = Color(0xFFFFC27A)) }
        }
        content()
    }
}

@Composable
private fun PanelToggle(label: String, checked: Boolean, tag: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PanelInk.copy(alpha = if (enabled) 1f else 0.45f), fontFamily = CourierPrime, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF5BBF6A), checkedThumbColor = Color(0xFFF1E9DC)),
        )
    }
}

@Composable
private fun ThemeChip(theme: PaperTheme, selected: Boolean, onClick: () -> Unit) {
    val t = paletteFor(theme)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(t.stageSide)
                .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFFFC27A) else Color.White.copy(alpha = 0.2f), CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "${theme.label} theme" }
                .testTag("panel_theme_${theme.name}"),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 22.dp, height = 26.dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(t.paper))
            Box(Modifier.align(Alignment.Center).offset(y = 9.dp).size(width = 26.dp, height = 9.dp).clip(CircleShape).background(t.capsule))
        }
        Text(theme.label, color = PanelInk, fontFamily = CourierPrime, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun PromptDialog(onUse: (String) -> Unit, onDismiss: () -> Unit) {
    var prompt by rememberSaveable { mutableStateOf(Prompts.random()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Need a first line?") },
        text = {
            Text(
                prompt, style = TypewriterText, modifier = Modifier.testTag("promptText"),
            )
        },
        confirmButton = { TextButton(onClick = { onUse(prompt) }, modifier = Modifier.testTag("usePrompt")) { Text("Use it") } },
        dismissButton = {
            TextButton(onClick = { prompt = Prompts.random(except = prompt) }, modifier = Modifier.testTag("anotherPrompt")) { Text("Another") }
        },
    )
}

@Composable
private fun ExportDialog(studio: Boolean, onChoose: (ExportChoice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share this note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ExportRow("Image", "4:5, for chats and posts", "export_image") { onChoose(ExportChoice(image = ImageFormat.STANDARD)) }
                ExportRow("Story", "9:16, Instagram Story size", "export_story") { onChoose(ExportChoice(image = ImageFormat.STORY)) }
                val lock = if (studio) "" else "  · Studio"
                PaperSize.entries.forEach { p ->
                    ExportRow("PDF ${p.label}$lock", "Print-ready typed letter", "export_${p.name}") { onChoose(ExportChoice(paper = p)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ExportRow(title: String, subtitle: String, tag: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
    }
}
