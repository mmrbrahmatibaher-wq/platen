package com.mahdi.platen.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.annotation.DrawableRes
import com.mahdi.platen.R
import com.mahdi.platen.data.PaperTheme

@Immutable
data class PlatenPalette(
    val background: Color,
    val paper: Color,
    val paperEdge: Color,
    val ink: Color,
    val body: Color,
    val bodyShade: Color,
    val bodyHighlight: Color,
    val roller: Color,
    val capsule: Color,
    val indicator: Color,
    val led: Color,
    val onBackground: Color,
    val muted: Color,
    val card: Color,
    val isDark: Boolean,
    /** Rendered bitmap layers for this theme (see scripts/render_assets.py). */
    @DrawableRes val paperTex: Int,
    /** Carriage pill (with lever slot and LED sockets) for this theme. */
    @DrawableRes val carriageTex: Int,
    /** The machine stage behind the sheet: side walls and the dark column behind the paper. */
    val stageSide: Color,
    val stageVoid: Color,
    /** Light UI text/icons on the stage (Box / Studio pills, counter, toolbar). */
    val stageInk: Color,
    val stageAccent: Color,
    /** Enamel pins shown on the body when charms are on. */
    val charms: List<Int>,
)

fun paletteFor(theme: PaperTheme): PlatenPalette = when (theme) {
    PaperTheme.CREAM -> PlatenPalette(
        background = Color(0xFFE8DFCB), paper = Color(0xFFF8F2E4), paperEdge = Color(0xFFE2D6BC),
        ink = Color(0xFF2A2420), body = Color(0xFFF1E6CC), bodyShade = Color(0xFFD5C4A1),
        bodyHighlight = Color(0xFFFFF8E8), roller = Color(0xFF1D1B1A), capsule = Color(0xFFF5ECD8),
        indicator = Color(0xFFD0432C), led = Color(0xFF41C463), onBackground = Color(0xFF2A2420),
        muted = Color(0xFF7C7162), card = Color(0xFFF6EFDF), isDark = false,
        paperTex = R.drawable.paper_cream, carriageTex = R.drawable.carriage_cream,
        stageSide = Color(0xFF1D1A15), stageVoid = Color(0xFF050304), stageInk = Color(0xFFCFC8BC), stageAccent = Color(0xFFA9D79C),
        charms = listOf(R.drawable.charm_coffee, R.drawable.charm_star),
    )
    PaperTheme.MINT -> PlatenPalette(
        background = Color(0xFFD6E8DD), paper = Color(0xFFF5F8EF), paperEdge = Color(0xFFD9E3D3),
        ink = Color(0xFF1F2E28), body = Color(0xFFBCE0CB), bodyShade = Color(0xFF94C4AB),
        bodyHighlight = Color(0xFFE4F5EA), roller = Color(0xFF1C2421), capsule = Color(0xFFEAF5EC),
        indicator = Color(0xFFD24E36), led = Color(0xFF3FCB6A), onBackground = Color(0xFF1F2E28),
        muted = Color(0xFF5F7369), card = Color(0xFFEFF6EE), isDark = false,
        paperTex = R.drawable.paper_mint, carriageTex = R.drawable.carriage_mint,
        stageSide = Color(0xFF141C17), stageVoid = Color(0xFF030504), stageInk = Color(0xFFCFC8BC), stageAccent = Color(0xFFA9D79C),
        charms = listOf(R.drawable.charm_heart, R.drawable.charm_flower),
    )
    PaperTheme.CHARCOAL -> PlatenPalette(
        background = Color(0xFF161617), paper = Color(0xFF2B2A28), paperEdge = Color(0xFF3A3936),
        ink = Color(0xFFEDE6D6), body = Color(0xFF3B3B3D), bodyShade = Color(0xFF262628),
        bodyHighlight = Color(0xFF55555A), roller = Color(0xFF0C0C0D), capsule = Color(0xFF4D4C4F),
        indicator = Color(0xFFE6654D), led = Color(0xFF58E27D), onBackground = Color(0xFFEDE6D6),
        muted = Color(0xFF9C968B), card = Color(0xFF242425), isDark = true,
        paperTex = R.drawable.paper_charcoal, carriageTex = R.drawable.carriage_charcoal,
        stageSide = Color(0xFF121213), stageVoid = Color(0xFF030303), stageInk = Color(0xFFCFC8BC), stageAccent = Color(0xFFA9D79C),
        charms = listOf(R.drawable.charm_moon, R.drawable.charm_star),
    )
    PaperTheme.GARDEN -> PlatenPalette(
        background = Color(0xFFD3DDC2), paper = Color(0xFFF6F1E2), paperEdge = Color(0xFFDCD3B8),
        ink = Color(0xFF29271F), body = Color(0xFFA9BC94), bodyShade = Color(0xFF86996F),
        bodyHighlight = Color(0xFFD2E0C0), roller = Color(0xFF1A1C17), capsule = Color(0xFFEFE8D4),
        indicator = Color(0xFFD24A30), led = Color(0xFF3FCB6A), onBackground = Color(0xFF29271F),
        muted = Color(0xFF656B55), card = Color(0xFFF1EEDF), isDark = false,
        paperTex = R.drawable.paper_garden, carriageTex = R.drawable.carriage_garden,
        stageSide = Color(0xFF191C13), stageVoid = Color(0xFF040503), stageInk = Color(0xFFCFC8BC), stageAccent = Color(0xFFA9D79C),
        charms = listOf(R.drawable.charm_flower, R.drawable.charm_heart, R.drawable.charm_flower),
    )
}

val LocalPalette = staticCompositionLocalOf { paletteFor(PaperTheme.CREAM) }
