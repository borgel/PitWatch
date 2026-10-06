package com.pitwatch.app.ui

import android.os.Build
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PromotionHintTest {
    @Test
    fun `only on devices that can promote, when promotion is off`() {
        assertFalse(PromotionHint.shouldShow(Build.VERSION_CODES_FULL.BAKLAVA, canPostPromoted = false)) // 36.0: no promotion
        assertTrue(PromotionHint.shouldShow(Build.VERSION_CODES_FULL.BAKLAVA_1, canPostPromoted = false))
        assertFalse(PromotionHint.shouldShow(Build.VERSION_CODES_FULL.BAKLAVA_1, canPostPromoted = true))
    }
}
