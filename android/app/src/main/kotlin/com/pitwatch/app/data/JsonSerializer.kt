package com.pitwatch.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.pitwatch.core.PitWatchJson
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer

/** DataStore serializer persisting [T] as JSON with the shared [PitWatchJson] config. */
class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T =
        try {
            PitWatchJson.decodeFromString(serializer, input.readBytes().decodeToString())
        } catch (e: IllegalArgumentException) { // includes SerializationException
            throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
        }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(PitWatchJson.encodeToString(serializer, t).encodeToByteArray())
    }
}
