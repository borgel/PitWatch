package com.pitwatch.app

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmokeTest {
    @Test
    fun `runs on API 36 with our package`() {
        assertEquals(36, Build.VERSION.SDK_INT)
        assertEquals("com.pitwatch.app", ApplicationProvider.getApplicationContext<Context>().packageName)
    }

    @Test
    fun `main activity launches`() {
        Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }
}
