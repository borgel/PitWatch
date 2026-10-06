package com.pitwatch.app.ui

import android.os.Build

object PromotionHint {
    /** Live Update promotion exists from Android 16 QPR1 (36.1); before that the hint would be misleading. */
    fun shouldShow(sdkIntFull: Int, canPostPromoted: Boolean): Boolean =
        sdkIntFull >= Build.VERSION_CODES_FULL.BAKLAVA_1 && !canPostPromoted
}
