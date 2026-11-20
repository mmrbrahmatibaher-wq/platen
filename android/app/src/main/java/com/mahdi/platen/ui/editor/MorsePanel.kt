package com.mahdi.platen.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahdi.platen.domain.MorseCodec
import com.mahdi.platen.domain.MorseDir
import com.mahdi.platen.ui.theme.CourierPrime
import com.mahdi.platen.ui.theme.SpecialElite

private val PanelBg = Color(0xFF0E0C0A)
private val TileBg = Color(0xFF2A2724)
private val TileInk = Color(0xFFF1E9DC)
private val CodeInk = Color(0xFFD8CFC1)
private val Muted = Color(0xFF9C9488)
private val Accent = Color(0xFFFFC27A)

/** Rows of 4 tiles, A–Z, then the dot / dash / word-gap keys. */
internal const val MORSE_COLUMNS = 4
internal val MorseRows = (MorseCodec.letters.size + MORSE_COLUMNS - 1) / MORSE_COLUMNS

/** The height a full grid needs for a given tile height (header + 7 rows + footer). */
internal fun morseGridHeight(tile: Dp): Dp = 44.dp + tile * MorseRows + 8.dp * (MorseRows - 1) + 76.dp + 24.dp

/**
 * Reference card for Morse mode: every letter with its code. Tapping a tile types that code
 * (plus the space that ends a letter) as if keyed; the footer types a dot, a dash or a word gap.
 * When there is no room for the grid (keyboard open on a phone) it becomes a single scrolling row.
 */
@Composable
fun MorsePanel(
    dir: MorseDir,
    enabled: Boolean,
    width: Dp,
    tileHeight: Dp?,
    onKey: (String) -> Unit,
    onCollapse: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(width)
            .clip(RoundedCornerShape(22.dp))
            .background(PanelBg)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .testTag("morsePanel"),
    ) {
        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Morse", fontFamily = SpecialElite, fontSize = 17.sp, color = TileInk)
            Spacer(Modifier.width(8.dp))
            Text(
                if (dir == MorseDir.MORSE_TO_ENGLISH) "· – → ABC" else "ABC → · –",
                fontFamily = CourierPrime, fontSize = 12.sp, color = Muted,
                modifier = Modifier.weight(1f).testTag("morseDir"),
            )
            HeaderIcon(Icons.Outlined.ExpandLess, "Hide Morse chart", "morseCollapse", onCollapse)
            HeaderIcon(Icons.Outlined.Close, "Leave Morse mode", "morseExit", onExit)
        }
        Spacer(Modifier.height(6.dp))
        if (tileHeight != null) {
            MorseCodec.letters.chunked(MORSE_COLUMNS).forEachIndexed { r, row ->
                if (r > 0) Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (c, code) ->
                        LetterTile(c, code, enabled, Modifier.weight(1f).height(tileHeight), onKey)
                    }
                    repeat(MORSE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FooterKey(".", "Dot · short", "morse_dot", enabled) { onKey(".") }
                FooterKey("-", "Dash · long", "morse_dash", enabled) { onKey("-") }
                FooterKey("/", "Word gap", "morse_word", enabled) { onKey("/ ") }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(".", "-", "/")) { k ->
                    LetterTile(null, k, enabled, Modifier.size(44.dp, 50.dp)) { onKey(if (k == "/") "/ " else k) }
                }
                items(MorseCodec.letters) { (c, code) -> LetterTile(c, code, enabled, Modifier.size(52.dp, 50.dp), onKey) }
            }
        }
    }
}

/** The collapsed panel: one pill that brings the chart back. */
@Composable
fun MorsePill(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(PanelBg)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .clickable(onClick = onExpand)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("morseExpand"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Morse  · –", fontFamily = CourierPrime, fontSize = 13.sp, color = Accent)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.ExpandMore, "Show Morse chart", tint = Accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick)
            .semantics { contentDescription = label }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Muted, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun LetterTile(c: Char?, code: String, enabled: Boolean, modifier: Modifier, onKey: (String) -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TileBg)
            .clickable(enabled = enabled) { onKey(if (c != null) "$code " else code) }
            .semantics { contentDescription = if (c != null) "$c, $code" else code }
            .testTag(if (c != null) "morse_$c" else "morse_key_$code"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (c != null) Text(c.toString(), fontFamily = SpecialElite, fontSize = 19.sp, color = TileInk)
        Text(code, fontFamily = CourierPrime, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp, color = CodeInk, maxLines = 1)
    }
}

@Composable
private fun FooterKey(symbol: String, label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(symbol, fontFamily = CourierPrime, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = TileInk)
        Text(label, fontFamily = SpecialElite, fontSize = 12.sp, color = Muted, maxLines = 1, softWrap = false)
    }
}
