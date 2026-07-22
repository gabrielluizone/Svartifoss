package com.svartifoss.snfell.common

import android.content.SharedPreferences
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Immediate phone → watch preference delivery over the low-latency MessageClient, complementing the
 * durable DataClient (DataItem) sync.
 *
 * DataItems are eventually-consistent and, under doze, are not delivered to the watch until it next
 * wakes or the user interacts - which is why a setting changed on the phone used to apply on the
 * watch only after a delay/interaction. A MessageClient message is delivered promptly whenever the
 * two are connected (the same transport the transport-control commands already use), so sending the
 * snapshot this way makes the change land immediately. The DataItem stays the source of truth for
 * durability (it survives a watch reboot / process death), so this message is purely an accelerant.
 *
 * A monotonic [sequence] lets the watch drop an out-of-order message, so a delayed older snapshot
 * can never clobber a newer one. Application is additive (keys present are written); key *removals*
 * are left to the DataItem path's inventory logic, so a dropped message can never wrongly delete a
 * preference.
 */
object WatchPreferenceMessage {

    private const val FORMAT_VERSION = 1

    private const val TYPE_INT = 1
    private const val TYPE_LONG = 2
    private const val TYPE_BOOLEAN = 3
    private const val TYPE_FLOAT = 4
    private const val TYPE_STRING = 5
    private const val TYPE_STRING_SET = 6

    data class Snapshot(val sequence: Long, val values: Map<String, Any>)

    /** Encodes the watch-synced preference values plus a monotonic [sequence] into a compact blob. */
    fun encode(sequence: Long, values: Map<String, *>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { stream ->
            stream.writeByte(FORMAT_VERSION)
            stream.writeLong(sequence)
            val encodable = values.entries.filter { it.value != null && isSupported(it.value!!) }
            stream.writeInt(encodable.size)
            for ((key, value) in encodable) {
                stream.writeUTF(key)
                writeValue(stream, value!!)
            }
        }
        return out.toByteArray()
    }

    /** Decodes a blob produced by [encode], or null if it is malformed / a newer format. */
    fun decode(bytes: ByteArray?): Snapshot? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { stream ->
                if (stream.readByte().toInt() != FORMAT_VERSION) return null
                val sequence = stream.readLong()
                val count = stream.readInt()
                val values = HashMap<String, Any>(count)
                repeat(count) {
                    val key = stream.readUTF()
                    readValue(stream)?.let { values[key] = it }
                }
                Snapshot(sequence, values)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Writes the snapshot's values into [editor] with the correct SharedPreferences type. Additive
     * only - it never removes keys. Mirrors PreferenceReceiverService's own type dispatch so the
     * two delivery paths write identical values.
     */
    fun applyTo(editor: SharedPreferences.Editor, values: Map<String, Any>) {
        for ((key, value) in values) {
            when (value) {
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value.filterIsInstanceTo(HashSet(), String::class.java) as Set<String>)
                }
            }
        }
    }

    private fun isSupported(value: Any): Boolean = value is Int || value is Long ||
            value is Boolean || value is Float || value is String ||
            (value is Set<*> && value.all { it is String })

    private fun writeValue(stream: DataOutputStream, value: Any) {
        when (value) {
            is Int -> { stream.writeByte(TYPE_INT); stream.writeInt(value) }
            is Long -> { stream.writeByte(TYPE_LONG); stream.writeLong(value) }
            is Boolean -> { stream.writeByte(TYPE_BOOLEAN); stream.writeBoolean(value) }
            is Float -> { stream.writeByte(TYPE_FLOAT); stream.writeFloat(value) }
            is String -> { stream.writeByte(TYPE_STRING); stream.writeUTF(value) }
            is Set<*> -> {
                stream.writeByte(TYPE_STRING_SET)
                val strings = value.filterIsInstance<String>()
                stream.writeInt(strings.size)
                strings.forEach(stream::writeUTF)
            }
        }
    }

    private fun readValue(stream: DataInputStream): Any? = when (stream.readByte().toInt()) {
        TYPE_INT -> stream.readInt()
        TYPE_LONG -> stream.readLong()
        TYPE_BOOLEAN -> stream.readBoolean()
        TYPE_FLOAT -> stream.readFloat()
        TYPE_STRING -> stream.readUTF()
        TYPE_STRING_SET -> {
            val size = stream.readInt()
            HashSet<String>(size).apply { repeat(size) { add(stream.readUTF()) } }
        }
        else -> null
    }

    private fun <C : MutableCollection<String>> Set<*>.filterIsInstanceTo(
            destination: C,
            klass: Class<String>
    ): C {
        for (element in this) if (klass.isInstance(element)) destination.add(element as String)
        return destination
    }
}
