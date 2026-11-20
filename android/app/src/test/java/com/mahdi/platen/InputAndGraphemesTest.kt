package com.mahdi.platen

import com.mahdi.platen.domain.EditDiff
import com.mahdi.platen.domain.Graphemes
import com.mahdi.platen.domain.InputDiff
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class InputAndGraphemesTest {
    @Test fun splitsPlainText() = assertEquals(listOf("a", "b", " ", "c"), Graphemes.split("ab c"))

    @Test fun splitsEmptyString() = assertEquals(emptyList<String>(), Graphemes.split(""))

    @Test fun crlfIsOneCluster() = assertEquals(listOf("a", "\r\n", "b"), Graphemes.split("a\r\nb"))

    @Test fun flagsPairUp() = assertEquals(listOf("🇮🇷", "🇺🇸", "🇫"), Graphemes.split("🇮🇷🇺🇸🇫"))

    @Test fun keycapAndVariationSelector() = assertEquals(listOf("1️⃣", "❤️"), Graphemes.split("1️⃣❤️"))

    @Test fun boundariesMatchClusters() = assertArrayEquals(intArrayOf(0, 1, 3, 4), Graphemes.boundaries("a😀b"))

    @Test fun appendIsInsert() = assertEquals(EditDiff(0, "c"), InputDiff.diff("ab", "abc"))

    @Test fun multiCharCommit() = assertEquals(EditDiff(0, "llo "), InputDiff.diff("he", "hello "))

    @Test fun singleBackspace() = assertEquals(EditDiff(1, ""), InputDiff.diff("abc", "ab"))

    @Test fun backspaceOverEmojiRemovesWholeCluster() = assertEquals(EditDiff(1, ""), InputDiff.diff("a😀", "a"))

    @Test fun imeDeletingHalfASurrogatePairStillCountsAsOneGlyph() =
        assertEquals(EditDiff(1, ""), InputDiff.diff("a😀", "a\uD83D"))

    @Test fun autocorrectReplacement() = assertEquals(EditDiff(2, "he "), InputDiff.diff("teh", "the "))

    @Test fun clearAll() = assertEquals(EditDiff(3, ""), InputDiff.diff("abc", ""))

    @Test fun identicalTextIsNoOp() = assertEquals(EditDiff(0, ""), InputDiff.diff("abc", "abc"))

    @Test fun combiningMarkAddedIsInsertedNotReplacement() = assertEquals(EditDiff(0, "\u0301"), InputDiff.diff("e", "e\u0301"))

    @Test fun skinToneAddedToEmoji() = assertEquals(EditDiff(0, "🏽"), InputDiff.diff("👍", "👍🏽"))

    @Test fun changeInsideClusterReplacesCluster() =
        assertEquals(EditDiff(1, "a\u0300"), InputDiff.diff("xa\u0301", "xa\u0300"))

    @Test fun persianDeletion() = assertEquals(EditDiff(1, ""), InputDiff.diff("سلام", "سلا"))
}
