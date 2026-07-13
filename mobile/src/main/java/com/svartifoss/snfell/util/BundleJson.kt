package com.svartifoss.snfell.util

import android.os.PersistableBundle
import org.json.JSONObject

/**
 * Portable, cross-Android-version serialization of a [PersistableBundle] to/from JSON.
 *
 * The on-disk config files ([BundleFileSerialization]) and the legacy [com.svartifoss.snfell.config.ConfigBackup]
 * blobs are raw `Parcel.marshall()` bytes, whose layout is **not stable across Android OS
 * versions** - a blob written on one version can fail to decode on another (this is what crashed
 * the Watch tab and made the bundled default config unusable on many devices). This walks the
 * bundle into a structured, version-independent JSON tree instead: every value carries an explicit
 * type tag, so ints, longs, booleans and strings all round-trip losslessly (a plain JSON number
 * can't distinguish an int from a long). Rebuilding the bundle from JSON on the target device
 * produces a parcel marshalled by *that* device's OS, so it is always decodable there.
 *
 * [PersistableBundle] only accepts a fixed value set (int / long / double / boolean / String and
 * nested [PersistableBundle]); those are the types handled here. An unexpected type throws rather
 * than being silently dropped, so a config that can't be represented fails loudly.
 */
object BundleJson {
    private const val TYPE = "t"
    private const val VALUE = "v"

    fun toJson(bundle: PersistableBundle): JSONObject {
        val obj = JSONObject()
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            val value = bundle.get(key) ?: continue
            obj.put(key, encode(value))
        }
        return obj
    }

    fun fromJson(obj: JSONObject): PersistableBundle {
        val bundle = PersistableBundle()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = obj.getJSONObject(key)
            when (val type = entry.getString(TYPE)) {
                "b" -> bundle.putBoolean(key, entry.getBoolean(VALUE))
                "i" -> bundle.putInt(key, entry.getInt(VALUE))
                "l" -> bundle.putLong(key, entry.getLong(VALUE))
                "d" -> bundle.putDouble(key, entry.getDouble(VALUE))
                "s" -> bundle.putString(key, entry.getString(VALUE))
                "p" -> bundle.putPersistableBundle(key, fromJson(entry.getJSONObject(VALUE)))
                else -> throw IllegalArgumentException("Unknown BundleJson value type tag: $type")
            }
        }
        return bundle
    }

    private fun encode(value: Any): JSONObject {
        val entry = JSONObject()
        when (value) {
            is Boolean -> entry.put(TYPE, "b").put(VALUE, value)
            is Int -> entry.put(TYPE, "i").put(VALUE, value)
            is Long -> entry.put(TYPE, "l").put(VALUE, value)
            is Double -> entry.put(TYPE, "d").put(VALUE, value)
            is String -> entry.put(TYPE, "s").put(VALUE, value)
            is PersistableBundle -> entry.put(TYPE, "p").put(VALUE, toJson(value))
            else -> throw IllegalArgumentException(
                    "Unsupported PersistableBundle value type: ${value.javaClass.name}")
        }
        return entry
    }
}
