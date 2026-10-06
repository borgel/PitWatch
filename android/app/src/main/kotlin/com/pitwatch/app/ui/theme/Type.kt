package com.pitwatch.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pitwatch.app.R

/** Bundled (OFL, see android/third_party/barlow): scoreboard display type. */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
)

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

/** Condensed Barlow for display, headline, title and label styles; Barlow for body. */
val PitWatchTypography: Typography = Typography().let { base ->
    fun TextStyle.condensed(weight: FontWeight) = copy(fontFamily = BarlowCondensed, fontWeight = weight)
    fun TextStyle.body() = copy(fontFamily = Barlow)
    base.copy(
        displayLarge = base.displayLarge.condensed(FontWeight.ExtraBold),
        displayMedium = base.displayMedium.condensed(FontWeight.ExtraBold),
        displaySmall = base.displaySmall.condensed(FontWeight.ExtraBold),
        headlineLarge = base.headlineLarge.condensed(FontWeight.Bold),
        headlineMedium = base.headlineMedium.condensed(FontWeight.Bold),
        headlineSmall = base.headlineSmall.condensed(FontWeight.Bold),
        titleLarge = base.titleLarge.condensed(FontWeight.Bold),
        titleMedium = base.titleMedium.condensed(FontWeight.Bold),
        titleSmall = base.titleSmall.condensed(FontWeight.Bold),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.condensed(FontWeight.Bold),
        labelMedium = base.labelMedium.condensed(FontWeight.Bold),
        labelSmall = base.labelSmall.condensed(FontWeight.Bold),
    )
}

object PitWatchType {
    /** The hero's big countdown; tabular figures so digits don't jitter. */
    val countdownHero = TextStyle(
        fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 128.sp,
        lineHeight = 0.95.em, letterSpacing = (-2).sp, fontFeatureSettings = "tnum",
    )

    /** "SATURDAY", "LAST": letter-spaced condensed caps. */
    val sectionLabel = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 2.sp)
}
