package com.svartifoss.snfell.music

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import com.svartifoss.snfell.notifications.AppGlyphStore

/**
 * The mark of the app a streaming link opens in, and whether it may be tinted, carried together.
 *
 * They were two independent conditions before, which was survivable while the answer was always "a
 * launcher icon, never tint it". It stops being survivable the moment the same slot can hold a
 * flat-white notification template: tint decided separately from image is how a glyph ends up drawn
 * white-on-white.
 */
data class ShortcutAppMark(val drawable: Drawable, val tintable: Boolean) {
    companion object {
        /**
         * The mark of the app [link] opens in: its notification glyph where this phone has learned
         * one (see [AppGlyphStore]), its launcher icon otherwise. A link to a service this app does
         * not know resolves to the system's default handler for it. Null when neither is installed.
         */
        fun forLink(context: Context, link: String): ShortcutAppMark? {
            val knownPackage = StreamingShortcutLinks.detect(link).packageName
            return if (knownPackage != null) {
                forPackage(context, knownPackage)
            } else {
                defaultHandler(context, link)?.let { forPackage(context, it) }
            }
        }

        fun forPackage(context: Context, packageName: String): ShortcutAppMark? {
            AppGlyphStore.drawable(context, packageName)?.let {
                return ShortcutAppMark(it, tintable = true)
            }
            return try {
                ShortcutAppMark(context.packageManager.getApplicationIcon(packageName),
                        tintable = false)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }

        /**
         * The status-bar glyph of the app [target] opens in, as the PNG [AppGlyphStore] keeps, for
         * the watch to draw beside "Open on phone". [target] is a link or the `targetPackage|uri`
         * form a list entry carries, whose package wins: it is the app the phone will actually
         * open, which for a service with several clients need not be the official one.
         *
         * Only the glyph, never the launcher icon: the watch draws this as a white template, and
         * a full-colour icon there would be a white square. Null when this phone has not seen
         * that app post a notification yet; the watch then draws its own mark for the service.
         */
        fun glyphPngFor(context: Context, target: String): ByteArray? {
            val link = StreamingShortcutLinks.unwrapRemoteTarget(target)
            val packageName = StreamingShortcutLinks.remoteTargetPackage(target)
                    ?: StreamingShortcutLinks.detect(link).packageName
                    ?: defaultHandler(context, link)
                    ?: return null
            return AppGlyphStore.glyph(context, packageName)
        }

        /** The package Android would open [link] in, for a link to a service not in the table. */
        fun defaultHandler(context: Context, link: String): String? {
            val canonicalLink = StreamingShortcutLinks.canonicalize(link)
            if (!StreamingShortcutLinks.isSafeLink(canonicalLink)) return null
            return try {
                context.packageManager.resolveActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(canonicalLink)),
                        PackageManager.MATCH_DEFAULT_ONLY
                )?.activityInfo?.packageName
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}
