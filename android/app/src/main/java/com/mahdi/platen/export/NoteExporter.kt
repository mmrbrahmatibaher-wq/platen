package com.mahdi.platen.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RadialGradient
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.mahdi.platen.R
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.GlyphJitter
import com.mahdi.platen.domain.NoteTitle
import com.mahdi.platen.ui.components.paperBitmap
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

/** Colours an export needs, decoupled from Compose. */
data class ExportColors(
    val paper: Int, val ink: Int, val background: Int, val muted: Int, val dark: Boolean,
    /** Rendered paper bitmap resource; 0 falls back to the procedural texture. */
    val paperTex: Int = 0,
)

object NoteExporter {

    fun typeface(context: Context, bold: Boolean = false): Typeface =
        runCatching { ResourcesCompat.getFont(context, if (bold) R.font.courier_prime_bold else R.font.special_elite) }
            .getOrNull() ?: Typeface.MONOSPACE

    /** Glyphs as styled text: overstruck cells print as a dark ink block, every glyph its own ink density. */
    fun spannable(glyphs: List<Glyph>, ink: Int, seed: Long): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val titleLen = NoteTitle.length(glyphs)
        glyphs.forEachIndexed { i, g ->
            val start = sb.length
            sb.append(if (g.struck && g.over != null) g.over else g.text)
            if (g.isNewline) return@forEachIndexed
            val a = if (i < titleLen) Color.alpha(ink) else (Color.alpha(ink) * GlyphJitter.alpha(i, seed)).toInt().coerceIn(0, 255)
            sb.setSpan(ForegroundColorSpan(Color.argb(a, Color.red(ink), Color.green(ink), Color.blue(ink))), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (i < titleLen) {
                // The title line: double-struck look (bold) and a touch larger.
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(1.08f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (g.struck) {
                val block = Color.argb((Color.alpha(ink) * 0.62f).toInt(), Color.red(ink), Color.green(ink), Color.blue(ink))
                sb.setSpan(BackgroundColorSpan(block), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setLineSpacing(0f, 1.3f)
            .setIncludePad(false)
            .build()

    /** Renders the note as a shareable bitmap ([ImageFormat.STANDARD] or 9:16 story). */
    fun renderImage(context: Context, glyphs: List<Glyph>, seed: Long, format: ImageFormat, colors: ExportColors): Bitmap {
        val w = format.widthPx
        val h = format.heightPx
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val sheet = if (format == ImageFormat.STORY) {
            c.drawColor(colors.background)
            RectF(72f, 150f, w - 72f, h - 210f)
        } else RectF(0f, 0f, w.toFloat(), h.toFloat())

        val rendered = if (colors.paperTex != 0) {
            runCatching {
                BitmapFactory.decodeResource(context.resources, colors.paperTex, BitmapFactory.Options().apply { inScaled = false })
            }.getOrNull()
        } else null
        val tex = rendered ?: paperBitmap(androidx.compose.ui.graphics.Color(colors.paper), colors.dark)
        val shader = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { if (rendered == null) setScale(2f, 2f) })
        }
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        if (format == ImageFormat.STORY) {
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
            c.drawRoundRect(RectF(sheet.left + 6, sheet.top + 14, sheet.right + 6, sheet.bottom + 14), 28f, 28f, shadow)
            c.drawRoundRect(sheet, 24f, 24f, paperPaint)
        } else c.drawRect(sheet, paperPaint)

        val margin = 84f
        val footer = 110f
        val contentW = (sheet.width() - 2 * margin).toInt()
        val contentH = sheet.height() - margin - footer
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = typeface(context); color = colors.ink }
        val text = spannable(glyphs, colors.ink, seed)
        var size = 42f
        paint.textSize = size
        var lay = layout(text, paint, contentW)
        var guard = 0
        while (lay.height > contentH && size > 14f && guard++ < 12) {
            size = (size * Pagination.fitScale(lay.height.toFloat(), contentH, 0.5f) * 0.98f).coerceAtLeast(14f)
            paint.textSize = size
            lay = layout(text, paint, contentW)
        }
        c.save()
        c.clipRect(sheet.left + margin, sheet.top + margin * 0.5f, sheet.right - margin, sheet.top + margin + contentH)
        c.translate(sheet.left + margin, sheet.top + margin)
        lay.draw(c)
        c.restore()

        val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface(context, bold = true); textSize = 30f; color = colors.muted; letterSpacing = 0.15f
            textAlign = Paint.Align.CENTER
        }
        val markY = if (format == ImageFormat.STORY) h - 110f else sheet.bottom - 52f
        c.drawText("platen", w / 2f, markY, mark)
        // Same soft vignette as the on-screen sheet.
        val vig = Paint(Paint.ANTI_ALIAS_FLAG)
        vig.shader = RadialGradient(
                sheet.centerX(), sheet.centerY(), maxOf(sheet.width(), sheet.height()) * 0.75f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(if (colors.dark) 60 else 26, 0, 0, 0)),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
            )
        if (format == ImageFormat.STORY) c.drawRoundRect(sheet, 24f, 24f, vig) else c.drawRect(sheet, vig)
        return bmp
    }

    /** Writes a print-ready letter PDF, paginating as needed. Returns the number of pages. */
    fun writePdf(context: Context, glyphs: List<Glyph>, seed: Long, paper: PaperSize, out: File, date: Date = Date()): Int {
        val ink = Color.rgb(28, 24, 22)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = typeface(context); textSize = paper.fontPt; color = ink }
        val lay = layout(spannable(glyphs, ink, seed), paint, paper.contentWidth)
        val headerGap = paper.fontPt * 3f
        val available = paper.contentHeight - headerGap
        val tops = (0 until lay.lineCount).map { lay.getLineTop(it).toFloat() }
        val bottoms = (0 until lay.lineCount).map { lay.getLineBottom(it).toFloat() }
        val pages = Pagination.pages(tops, bottoms, available)
        val doc = PdfDocument()
        try {
            val datePaint = TextPaint(paint).apply { color = Color.rgb(90, 84, 78); textAlign = Paint.Align.RIGHT }
            val dateText = DateFormat.getDateInstance(DateFormat.LONG).format(date)
            pages.forEachIndexed { index, range ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(paper.widthPt, paper.heightPt, index + 1).create())
                val c = page.canvas
                c.drawColor(Color.WHITE)
                val m = paper.marginPt.toFloat()
                if (index == 0) c.drawText(dateText, paper.widthPt - m, m, datePaint)
                if (!range.isEmpty()) {
                    val top = lay.getLineTop(range.first).toFloat()
                    val bottom = lay.getLineBottom(range.last).toFloat()
                    c.save()
                    c.translate(m, m + headerGap - top)
                    c.clipRect(0f, top, paper.contentWidth.toFloat(), bottom)
                    lay.draw(c)
                    c.restore()
                }
                if (pages.size > 1) {
                    val num = TextPaint(datePaint).apply { textAlign = Paint.Align.CENTER; textSize = paper.fontPt * 0.8f }
                    c.drawText("— ${index + 1} —", paper.widthPt / 2f, paper.heightPt - m / 2f, num)
                }
                doc.finishPage(page)
            }
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return pages.size
    }

    fun exportDir(context: Context): File = File(context.cacheDir, "exports").apply { mkdirs() }

    fun saveImage(context: Context, bitmap: Bitmap, name: String): File {
        val f = File(exportDir(context), name)
        FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun shareIntent(context: Context, file: File, mime: String, title: String? = null): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            if (!title.isNullOrBlank()) { putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TITLE, title) }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title?.takeIf { it.isNotBlank() } ?: "Share note").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
