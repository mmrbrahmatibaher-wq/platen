package com.mahdi.platen.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A soft, layered drop shadow under a sheet: several stacked translucent rounded rects growing
 * outwards and downwards (a contact shadow plus a wide ambient one). Stacking avoids blur mask
 * filters, which are unreliable on hardware canvases before API 28.
 */
fun Modifier.paperShadow(dark: Boolean): Modifier = drawBehind {
    val d = density
    val base = if (dark) 0.10f else 0.045f
    for (i in 1..9) {
        val spread = i * 1.3f * d
        val dy = i * 0.7f * d
        val a = base * (1f - i / 10f)
        drawRoundRect(
            Color.Black.copy(alpha = a),
            topLeft = Offset(-spread, -spread * 0.4f + dy),
            size = Size(size.width + spread * 2, size.height + spread * 1.4f),
            cornerRadius = CornerRadius(3f * d + spread),
        )
    }
    // Tight contact line along the edges.
    drawRect(Color.Black.copy(alpha = if (dark) 0.35f else 0.12f), topLeft = Offset(-0.6f * d, 0f), size = Size(size.width + 1.2f * d, size.height))
}

/** A barely-there vignette plus a lit top-left, so the sheet reads as a curved physical page. */
fun Modifier.paperVignette(): Modifier = drawWithContent {
    drawContent()
    val edge = 22f * density
    // side falloff
    drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.07f), 1f to Color.Transparent, startX = 0f, endX = edge), size = Size(edge, size.height))
    drawRect(
        Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.08f), startX = size.width - edge, endX = size.width),
        topLeft = Offset(size.width - edge, 0f), size = Size(edge, size.height),
    )
    // top edge and a broad warm highlight
    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.06f), 1f to Color.Transparent, startY = 0f, endY = edge), size = Size(size.width, edge))
    drawRect(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.10f), 1f to Color.Transparent,
            center = Offset(size.width * 0.3f, size.height * 0.35f), radius = size.width * 0.9f,
        ),
    )
}
