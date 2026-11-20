package com.mahdi.platen.export

/** Shareable image sizes, in pixels. */
enum class ImageFormat(val label: String, val widthPx: Int, val heightPx: Int) {
    STANDARD("Image", 1080, 1350),
    STORY("Story 9:16", 1080, 1920);

    val aspect: Float get() = widthPx.toFloat() / heightPx
}

/** Print sizes in PostScript points (1/72"), which is what PdfDocument uses. */
enum class PaperSize(val label: String, val widthPt: Int, val heightPt: Int, val marginPt: Int, val fontPt: Float) {
    A4("A4", 595, 842, 72, 12f),
    LETTER("US Letter", 612, 792, 72, 12f),
    A5("A5", 420, 595, 46, 10f);

    val contentWidth: Int get() = widthPt - 2 * marginPt
    val contentHeight: Int get() = heightPt - 2 * marginPt
}

object Pagination {
    /**
     * Splits lines into pages. [lineTops]/[lineBottoms] are the vertical extents of each laid
     * out line; a page holds as many whole lines as fit in [pageHeight]. Always returns at least
     * one page, and a line taller than a page still gets its own page.
     */
    fun pages(lineTops: List<Float>, lineBottoms: List<Float>, pageHeight: Float): List<IntRange> {
        require(lineTops.size == lineBottoms.size)
        require(pageHeight > 0f)
        if (lineTops.isEmpty()) return listOf(IntRange.EMPTY)
        val result = ArrayList<IntRange>()
        var start = 0
        while (start < lineTops.size) {
            val origin = lineTops[start]
            var end = start
            while (end + 1 < lineTops.size && lineBottoms[end + 1] - origin <= pageHeight) end++
            result += start..end
            start = end + 1
        }
        return result
    }

    /** Font scale that makes [contentHeight] fit in [available], clamped to [minScale]..1. */
    fun fitScale(contentHeight: Float, available: Float, minScale: Float = 0.4f): Float {
        if (contentHeight <= available || contentHeight <= 0f) return 1f
        return (available / contentHeight).coerceIn(minScale, 1f)
    }
}
