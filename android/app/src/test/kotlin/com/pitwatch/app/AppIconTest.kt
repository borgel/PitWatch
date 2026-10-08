package com.pitwatch.app

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppIconTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `launcher icon is adaptive with a themed layer`() {
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
        for (res in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val icon = assertIs<AdaptiveIconDrawable>(context.getDrawable(res))
            assertNotNull(icon.monochrome, "themed icons need a monochrome layer")
        }
    }
}
