package com.mahdi.platen

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mahdi.platen.audio.SoundMix
import com.mahdi.platen.data.PaperTheme
import com.mahdi.platen.domain.Carriage
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.GlyphJitter
import com.mahdi.platen.domain.TypeEvent
import com.mahdi.platen.domain.TypewriterEngine
import com.mahdi.platen.ui.settings.CREDITS
import com.mahdi.platen.ui.theme.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestPlatenApp::class)
class V2AssetsAndMotionTest {
    private val ctx = ApplicationProvider.getApplicationContext<TestPlatenApp>()

    private fun glyphs(s: String) = TypewriterEngine().apply { type(s) }.glyphs

    @Test fun carriageColumnCountsFromLastNewline() {
        assertEquals(0, Carriage.column(emptyList()))
        assertEquals(5, Carriage.column(glyphs("hello")))
        assertEquals(2, Carriage.column(glyphs("hello\nab")))
        assertEquals(0, Carriage.column(glyphs("hello\n")))
        // struck glyphs still occupy their position on the line
        val e = TypewriterEngine().apply { type("abc"); backspace() }
        assertEquals(3, Carriage.column(e.glyphs))
    }

    @Test fun carriageProgressIsBoundedAndWraps() {
        assertEquals(0f, Carriage.progress(0, 40), 0f)
        assertEquals(0.5f, Carriage.progress(20, 40), 1e-6f)
        assertTrue(Carriage.progress(39, 40) < 1f)
        assertEquals(0f, Carriage.progress(40, 40), 0f) // soft wrap: carriage returns
        assertEquals(0f, Carriage.progress(10, 0), 0f)  // before layout is known
        assertEquals(0f, Carriage.progress(-3, 40), 0f)
    }

    @Test fun lineFeedsCountReturns() {
        assertEquals(0, Carriage.lineFeeds(glyphs("abc")))
        assertEquals(3, Carriage.lineFeeds(glyphs("a\nb\n\nc")))
    }

    @Test fun ghostStrikesAreAMinorityAndDeterministic() {
        val n = 5000
        val ghosts = (0 until n).count { GlyphJitter.ghostAlpha(it, 42) > 0f }
        val share = ghosts.toFloat() / n
        assertTrue("share $share", share in 0.12f..0.24f)
        (0 until 200).forEach {
            val g = GlyphJitter.ghostAlpha(it, 9)
            assertEquals(g, GlyphJitter.ghostAlpha(it, 9), 0f)
            assertTrue(g == 0f || g in 0.22f..0.42f)
            assertTrue(GlyphJitter.ghostDx(it, 9) in -1f..1f && GlyphJitter.ghostDy(it, 9) in -1f..1f)
        }
    }

    @Test fun soundMixVariesWithinRanges() {
        val r = Random(3)
        TypeEvent.entries.forEach { e ->
            val (v, rate) = SoundMix.range(e)
            val mixes = (0 until 50).map { SoundMix.forEvent(e, r) }
            mixes.forEach { assertTrue(it.volume in v && it.rate in rate) }
            assertTrue("volumes must vary for $e", mixes.map { it.volume }.toSet().size > 10)
            assertTrue(v.endInclusive <= 1f && rate.start >= 0.5f && rate.endInclusive <= 2f) // SoundPool limits
        }
        // keys: pitch within ±4 %, volume within ±10 % of its centre
        listOf(TypeEvent.KEY, TypeEvent.STRIKE, TypeEvent.SPACE).forEach { e ->
            val (v, rate) = SoundMix.range(e)
            assertTrue(rate.start >= 0.959f && rate.endInclusive <= 1.041f)
            val mid = (v.start + v.endInclusive) / 2
            assertTrue(v.start >= mid * 0.899f && v.endInclusive <= mid * 1.101f)
        }
    }

    @Test fun everyThemeHasItsRenderedLayers() {
        PaperTheme.entries.forEach { t ->
            val p = paletteFor(t)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            BitmapFactory.decodeResource(ctx.resources, p.paperTex, opts)
            assertEquals("paper $t", 512, opts.outWidth)
            BitmapFactory.decodeResource(ctx.resources, p.carriageTex, opts)
            assertEquals("carriage $t", 832, opts.outWidth)
            assertEquals("carriage $t", 456, opts.outHeight)
            assertTrue(p.charms.isNotEmpty())
        }
        assertEquals(4, PaperTheme.entries.size) // CREAM, MINT, CHARCOAL, GARDEN
    }

    @Test fun recordedSoundsAreBundled() {
        val names = (1..10).map { "key_%02d".format(it) } + listOf("backspace",
            "space", "bell", "bell_2", "bell_3", "carriage_return", "carriage_return_2", "jam")
        val sizes = names.map { n ->
            val id = ctx.resources.getIdentifier(n, "raw", ctx.packageName)
            assertNotEquals("missing raw/$n", 0, id)
            ctx.resources.openRawResource(id).use { it.readBytes() }
        }
        assertTrue(sizes.all { it.size > 1500 })
        assertTrue("key samples must differ", sizes.take(10).map { it.contentHashCode() }.toSet().size == 10)
        // full-quality PCM WAV, not lossy previews
        sizes.forEach { assertEquals("RIFF", String(it, 0, 4)); assertEquals("WAVE", String(it, 8, 4)) }
        // the old lossy v2 samples are gone
        listOf("key_1", "tail_1").forEach { assertEquals(0, ctx.resources.getIdentifier(it, "raw", ctx.packageName)) }
    }

    @Test fun creditsNameSourcesAndLicenses() {
        assertTrue(CREDITS.contains("BigSoundBank"))
        assertTrue(CREDITS.contains("CC0"))
        assertTrue(CREDITS.contains("Special Elite"))
        assertTrue(CREDITS.contains("Apache"))
    }

    @Test fun glyphSerializationSurvivesForV2() {
        val g = listOf(Glyph("a"), Glyph("b", struck = true), Glyph("c", struck = true, over = "d"), Glyph("\n"))
        assertEquals(g, com.mahdi.platen.domain.GlyphCodec.decode(com.mahdi.platen.domain.GlyphCodec.encode(g)))
    }
}
