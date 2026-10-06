package com.pitwatch.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.PitMap
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class LiveControl(
    /** Auto-start stays off while this is the next match (the user pressed Stop or dismissed). */
    val suppressedMatchKey: String? = null,
)

/** The last pit map fetched, kept so the map still shows offline at the venue. */
@Serializable
data class PitMapCache(val eventKey: String? = null, val map: PitMap? = null)

/** All persisted state, one JSON file per store under [dir]. A corrupt file resets to its default. */
class Stores(dir: File, scope: CoroutineScope) {
    val config: DataStore<UserConfig> = create(dir, "team_config.json", UserConfig.serializer(), UserConfig(), scope)
    val cache: DataStore<EventCache> = create(dir, "event_cache.json", EventCache.serializer(), EventCache(), scope)
    val refreshState: DataStore<RefreshState> = create(dir, "last_refresh.json", RefreshState.serializer(), RefreshState(), scope)
    val liveControl: DataStore<LiveControl> = create(dir, "live_control.json", LiveControl.serializer(), LiveControl(), scope)
    val pitMap: DataStore<PitMapCache> = create(dir, "pit_map.json", PitMapCache.serializer(), PitMapCache(), scope)
    val notificationPrefs: DataStore<NotificationPrefs> =
        create(dir, "notification_prefs.json", NotificationPrefs.serializer(), NotificationPrefs(), scope)

    private companion object {
        fun <T> create(dir: File, name: String, serializer: KSerializer<T>, default: T, scope: CoroutineScope): DataStore<T> =
            DataStoreFactory.create(
                serializer = JsonSerializer(serializer, default),
                corruptionHandler = ReplaceFileCorruptionHandler { default },
                scope = scope,
                produceFile = { File(dir, name) },
            )
    }
}
