package com.mahdi.platen.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.imageResource
import com.mahdi.platen.ui.theme.PaperTexture

/** Procedural fallback texture (used when no bitmap is available, and by unit tests). */
fun paperBitmap(color: Color, dark: Boolean, size: Int = 256): Bitmap {
    val px = PaperTexture.generate(size, color.toArgb(), seed = 11, dark = dark)
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

/** The theme's rendered, seamlessly tiling paper (512 px WebP with fibres, mottling and specks). */
@Composable
fun rememberPaperBrush(@DrawableRes texture: Int): ShaderBrush {
    val img: ImageBitmap = ImageBitmap.imageResource(texture)
    return remember(img) { ShaderBrush(ImageShader(img, TileMode.Repeated, TileMode.Repeated)) }
}
