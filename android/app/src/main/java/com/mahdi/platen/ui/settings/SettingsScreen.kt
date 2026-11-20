package com.mahdi.platen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mahdi.platen.data.PaperTheme
import com.mahdi.platen.data.Settings
import com.mahdi.platen.domain.CorrectionMode
import com.mahdi.platen.ui.theme.LocalPalette
import com.mahdi.platen.ui.theme.paletteFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onChange: ((Settings) -> Settings) -> Unit, onBack: () -> Unit, onOpenStudio: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = p.onBackground)
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = p.onBackground)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Section("Sound & feel") {
                ToggleRow("Key sounds", "Strikes, space bar, carriage return and the margin bell", settings.sound, "sw_sound") { v -> onChange { it.copy(sound = v) } }
                ToggleRow("Play in silent mode", "Keep the clack even when the ringer is off", settings.playInSilentMode, "sw_silent", enabled = settings.sound) { v ->
                    onChange { it.copy(playInSilentMode = v) }
                }
                ToggleRow("Haptics", "A small tick under every key", settings.haptics, "sw_haptics") { v -> onChange { it.copy(haptics = v) } }
            }
            Section("Typing") {
                ToggleRow(
                    "Backspace erases",
                    if (settings.backspaceErases) "Backspace removes the last letter, across lines too. Hold it to keep erasing."
                    else "Off: backspace steps the carriage back and typing over a letter overstrikes it with a dark block, like a real machine.",
                    settings.backspaceErases, "sw_erase",
                ) { v -> onChange { it.copy(correction = if (v) CorrectionMode.DELETE else CorrectionMode.STRIKE) } }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Autocorrect", "Off: every keystroke lands exactly as typed", settings.autocorrect, "sw_autocorrect") { v ->
                    onChange { it.copy(autocorrect = v) }
                }
            }
            Section("Paper & machine") {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PaperTheme.entries.forEach { t -> ThemeSwatch(t, settings.theme == t) { onChange { it.copy(theme = t) } } }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Charms", "Little enamel pins on the machine", settings.charms, "sw_charms") { v -> onChange { it.copy(charms = v) } }
            }
            Section("Night lamp") {
                Text("Tip: tap the lamp in the toolbar (hold it for these sliders), or long-press the carriage.", style = MaterialTheme.typography.bodySmall, color = p.muted)
                ToggleRow("Lamp on", null, settings.lampOn, "sw_lamp") { v -> onChange { it.copy(lampOn = v) } }
                SliderRow("Warmth", settings.lampWarmth, "set_warmth") { v -> onChange { it.copy(lampWarmth = v) } }
                SliderRow("Intensity", settings.lampIntensity, "set_intensity") { v -> onChange { it.copy(lampIntensity = v) } }
                SliderRow("Beam", settings.lampBeam, "set_beam") { v -> onChange { it.copy(lampBeam = v) } }
            }
            Section("Studio") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpenStudio).testTag("openStudio").padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (settings.studio) "Studio unlocked (demo)" else "Platen Studio", style = MaterialTheme.typography.titleSmall, color = p.onBackground)
                        Text("More notes and print-ready PDF letters", style = MaterialTheme.typography.bodySmall, color = p.muted)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = p.muted)
                }
            }
            Section("Credits") {
                Text(
                    CREDITS,
                    style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(vertical = 4.dp).testTag("credits"),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = p.muted, modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(horizontal = 16.dp, vertical = 10.dp),
    ) { content() }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, tag: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) p.onBackground else p.muted)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(checkedTrackColor = p.led, checkedThumbColor = p.paper),
        )
    }
}

@Composable
private fun SliderRow(label: String, value: Float, tag: String, onChange: (Float) -> Unit) {
    val p = LocalPalette.current
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = p.onBackground)
        Slider(value = value, onValueChange = onChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun ThemeSwatch(theme: PaperTheme, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val t = paletteFor(theme)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(t.body)
                .border(if (selected) 3.dp else 1.dp, if (selected) p.indicator else t.bodyShade, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .semantics { role = Role.RadioButton; this.selected = selected; contentDescription = "${theme.label} theme" }
                .testTag("theme_${theme.name}"),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 40.dp, height = 28.dp).background(t.paper))
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(width = 48.dp, height = 8.dp).clip(CircleShape).background(t.roller))
            Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(7.dp).clip(CircleShape).background(t.led))
        }
        Text(theme.label, style = MaterialTheme.typography.labelMedium, color = p.onBackground, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Third-party assets bundled with the app (full texts in CREDITS.md and licenses/). */
const val CREDITS =
    "Typewriter sounds: real recordings of a Hermes Precisa 305 by Joseph Sardin, BigSoundBank.com " +
        "(CC0 1.0 public domain), plus a bell by _stubb on Freesound (CC0), trimmed and loudness-matched for Platen.\n" +
        "Typeface: Special Elite by Astigmatic (Apache License 2.0).\n" +
        "UI typeface: Courier Prime by Alan Dague-Greene (SIL Open Font License 1.1).\n" +
        "Machine, paper and icon artwork rendered for this app."
