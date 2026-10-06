package com.pitwatch.app.data

import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoresTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Opens the stores, runs [block], then closes them so the files can be reopened. */
    private fun <R> withStores(block: suspend (Stores) -> R): R = runBlocking {
        val job = SupervisorJob()
        try {
            block(Stores(tmp.root, CoroutineScope(Dispatchers.IO + job)))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `missing files read as defaults`() = withStores { stores ->
        assertEquals(UserConfig(), stores.config.data.first())
        assertEquals(EventCache(), stores.cache.data.first())
        assertEquals(LiveControl(), stores.liveControl.data.first())
    }

    @Test
    fun `writes survive reopening`() {
        withStores { it.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k") } }
        assertEquals(5507, withStores { it.config.data.first().teamNumber })
    }

    @Test
    fun `corrupt file falls back to the default`() {
        File(tmp.root, "team_config.json").writeText("{not json")
        assertEquals(UserConfig(), withStores { it.config.data.first() })
    }

    @Test
    fun `config is stored as readable JSON`() {
        withStores { it.config.updateData { UserConfig(teamNumber = 5507) } }
        assertTrue("\"teamNumber\":5507" in File(tmp.root, "team_config.json").readText())
    }

    @Test
    fun `notification prefs default to off`() = withStores { stores ->
        assertEquals(NotificationPrefs(), stores.notificationPrefs.data.first())
        assertEquals(false, stores.notificationPrefs.data.first().scheduleEnabled)
    }
}
