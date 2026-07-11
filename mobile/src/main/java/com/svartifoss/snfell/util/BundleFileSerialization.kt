package com.svartifoss.snfell.util

import android.os.PersistableBundle
import com.matejdro.wearutils.messages.ParcelPacker
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Class that helps with serializing [PersistableBundle] into files {
 */
object BundleFileSerialization {
    /**
     * Write bundle to file.
     *
     * Written via a temp file + atomic rename so a crash or kill mid-write can never leave a
     * half-written config behind (a torn parcel fails with [android.os.BadParcelableException]
     * on the next read - and only lazily, at first key access, far away from the read call).
     */
    fun writeToFile(bundle: PersistableBundle, file: File) {
        val tempFile = File(file.parentFile, file.name + ".tmp")

        FileOutputStream(tempFile).use {
            val configBundleBytes = ParcelPacker.getData(bundle)

            it.writeInt(configBundleBytes.size)
            it.write(configBundleBytes)
            it.fd.sync()
        }

        if (!tempFile.renameTo(file)) {
            tempFile.delete()
            throw IOException("Could not move ${tempFile.name} over ${file.name}")
        }
    }

    /**
     * Read bundle from file. Returns *null* (never throws) when the file is missing, truncated
     * or not decodable as a parcel - a file that fails to *decode* is additionally quarantined
     * (renamed to `<name>.corrupt`) so it can't keep failing forever. Unreadable parcels are a
     * real occurrence: [android.os.Parcel.marshall] bytes are not stable across Android
     * versions, and a config backup made on one OS version can be imported on another
     * (see [com.svartifoss.snfell.config.ConfigBackup]).
     *
     * The returned bundle is force-unparceled before being handed out: since Android 13 bundles
     * unmarshal lazily (at first key access), so without this a corrupt parcel would escape this
     * method's guards and crash at some later `get*()` call site instead.
     *
     * @param maxSize Maximum size of the file. If file is bigger than that, we assume there is something else (not the bundle) saved in the file and fail.
     */
    fun readFromFile(file: File, maxSize: Int = 10_000_000): PersistableBundle? {
        if (!file.exists()) {
            return null
        }

        FileInputStream(file).use {
            val configSize = it.readInt()

            if (configSize <= 0 || configSize > maxSize) {
                // Treat empty/negative/extraordinary long config as reading error
                Timber.e("Invalid config size %d! Non-config stream?", configSize)
                quarantine(file)
                return null
            }

            val configData = ByteArray(configSize)
            var filled = 0
            while (filled < configSize) {
                // A single read() may return fewer bytes than requested - looping guarantees we
                // never hand a partially-filled (silently zero-padded) buffer to the unmarshaller.
                val read = it.read(configData, filled, configSize - filled)
                if (read < 0) {
                    Timber.e("Config truncated: expected %d bytes, got %d", configSize, filled)
                    quarantine(file)
                    return null
                }
                filled += read
            }

            return try {
                val bundle = ParcelPacker.getParcelable(configData, PersistableBundle.CREATOR)
                // Trigger the deferred unparcel of the top-level map while still inside this
                // guard. Nested bundles stay lazy, so callers must still catch RuntimeException
                // around deep reads.
                bundle.size()
                bundle
            } catch (e: RuntimeException) {
                Timber.e(e, "Config not decodable as a parcel (cross-version import or corruption)")
                quarantine(file)
                null
            }
        }
    }

    /**
     * Checks whether [blob] - the exact byte layout [writeToFile] produces (4-byte length header
     * + marshalled parcel) - fully decodes on this device, nested bundles included. Used to
     * validate config blobs coming from a backup before they're written anywhere.
     */
    fun isDecodable(blob: ByteArray): Boolean {
        if (blob.size < 4) {
            return false
        }
        val declaredSize = ((blob[0].toInt() and 0xFF) shl 24) or
                ((blob[1].toInt() and 0xFF) shl 16) or
                ((blob[2].toInt() and 0xFF) shl 8) or
                (blob[3].toInt() and 0xFF)
        if (declaredSize <= 0 || declaredSize > blob.size - 4) {
            return false
        }

        return try {
            val bundle = ParcelPacker.getParcelable(
                    blob.copyOfRange(4, 4 + declaredSize),
                    PersistableBundle.CREATOR
            )
            deepUnparcel(bundle)
            true
        } catch (e: RuntimeException) {
            Timber.w(e, "Config blob not decodable on this device")
            false
        }
    }

    /** Forces every value (nested bundles recursively) out of its lazy parceled form. */
    private fun deepUnparcel(bundle: PersistableBundle) {
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            val value = bundle.get(key)
            if (value is PersistableBundle) {
                deepUnparcel(value)
            }
        }
    }

    /** Moves an undecodable file aside instead of deleting it - it stops poisoning every
     *  subsequent read, but the bytes stay around for diagnosis. */
    private fun quarantine(file: File) {
        val target = File(file.parentFile, file.name + ".corrupt")
        target.delete()
        if (!file.renameTo(target)) {
            file.delete()
        }
    }
}
