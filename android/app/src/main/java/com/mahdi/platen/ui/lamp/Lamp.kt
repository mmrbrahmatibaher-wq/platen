package com.mahdi.platen.ui.lamp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object LampMath {
    /** Light colour from cool white (0) to candle amber (1). */
    fun lightColor(warmth: Float): Color = lerp(Color(0xFFFFF6E6), Color(0xFFFFA94D), warmth.coerceIn(0f, 1f))

    /** Beam radius as a fraction of the shorter screen side. */
    fun beamFraction(beam: Float): Float = 0.32f + 0.68f * beam.coerceIn(0f, 1f)

    fun darkness(intensity: Float): Float = 0.93f - 0.1f * intensity.coerceIn(0f, 1f)
}

/**
 * Night lamp: darkens everything and cuts a soft pool of warm light around the typing line.
 * It is draw-only, so touches pass straight through to the page and machine underneath.
 */
@Composable
fun LampControls(
    warmth: Float,
    intensity: Float,
    beam: Float,
    onWarmth: (Float) -> Unit,
    onIntensity: (Float) -> Unit,
    onBeam: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.width(260.dp).testTag("lampControls"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF21E1C1A), contentColor = Color(0xFFF4EBDD)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Night lamp", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDone) { Text("Done", color = Color(0xFFFFC27A)) }
            }
            LampSlider("Warmth", warmth, onWarmth, "lampWarmth")
            LampSlider("Intensity", intensity, onIntensity, "lampIntensity")
            LampSlider("Beam", beam, onBeam, "lampBeam")
        }
    }
}

@Composable
private fun LampSlider(label: String, value: Float, onChange: (Float) -> Unit, tag: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value, onValueChange = onChange, modifier = Modifier.testTag(tag),
            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFC27A), activeTrackColor = Color(0xFFFFA94D), inactiveTrackColor = Color(0x55FFFFFF)),
        )
    }
}
