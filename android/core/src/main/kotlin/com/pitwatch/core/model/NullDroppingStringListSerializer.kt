package com.pitwatch.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Nexus sends `null` for unassigned team slots (practice matches, dropped teams).
 * Drop them so one null doesn't fail the whole event decode.
 */
object NullDroppingStringListSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer().nullable)
    override val descriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder): List<String> = delegate.deserialize(decoder).filterNotNull()
    override fun serialize(encoder: Encoder, value: List<String>) = delegate.serialize(encoder, value)
}
