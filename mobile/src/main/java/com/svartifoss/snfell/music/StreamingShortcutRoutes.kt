package com.svartifoss.snfell.music

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Chooses the Android package that receives a streaming shortcut.
 *
 * A service's own package remains the default, preserving existing installations.  A user can
 * override it with another installed app that advertises it can open that service's web link
 * (for example, an alternative YouTube Music client).  The override is deliberately per service:
 * selecting an alternative YouTube Music client never changes how Spotify links are opened.
 */
object StreamingShortcutRoutes {
    private const val PREF_KEY_PREFIX = "streaming_target_package_"

    data class App(val packageName: String, val label: String)

    fun preferenceKey(service: StreamingService): String =
            PREF_KEY_PREFIX + service.name.lowercase()

    fun selectedPackage(preferences: SharedPreferences, service: StreamingService): String? =
            preferences.getString(preferenceKey(service), null)?.takeIf(String::isNotBlank)

    /** Pure routing rule, kept separate from Android package lookup for regression tests. */
    fun resolvePackage(
            service: StreamingService,
            openMode: String,
            selectedPackage: String?,
            isInstalled: (String) -> Boolean
    ): String? {
        if (openMode != StreamingShortcutLinks.OPEN_MODE_APP) return null
        val selected = selectedPackage?.takeIf(isInstalled)
        if (selected != null) return selected
        return service.packageName?.takeIf(isInstalled)
    }

    fun targetPackage(
            context: Context,
            preferences: SharedPreferences,
            service: StreamingService,
            openMode: String,
            rawLink: String? = null
    ): String? = resolvePackage(
            service,
            openMode,
            selectedPackage(preferences, service),
            { packageName ->
                context.isPackageInstalled(packageName) &&
                        (rawLink?.let { listOf(it) } ?: representativeLinks(service)).any { link ->
                            canOpen(context.packageManager, packageName,
                                    linkForTarget(link, service, packageName))
                        }
            }
    )

    /**
     * The official Spotify app accepts its compact app URI; alternatives should receive the
     * provider's normal web URL, which is the interoperable form they register for.
     */
    fun linkForTarget(rawLink: String, service: StreamingService, targetPackage: String?): String =
            if (targetPackage != null && targetPackage == service.packageName) {
                StreamingShortcutLinks.forInstalledApp(rawLink)
            } else {
                StreamingShortcutLinks.forBrowser(rawLink)
            }

    /**
     * Test real content paths with an explicitly targeted intent. Domain verification can exclude
     * an alternative from an implicit query even though it accepts a package-targeted link.
     * A media-button receiver alone proves nothing about web-link support.
     */
    fun availableApps(context: Context, service: StreamingService): List<App> {
        val links = representativeLinks(service)
        if (links.isEmpty()) return emptyList()
        val packageManager = context.packageManager
        return candidatePackages(context, links).asSequence()
                .filter { packageName -> links.any { link ->
                    canOpen(packageManager, packageName, linkForTarget(link, service, packageName))
                } }
                .mapNotNull { packageName ->
                    try {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        App(packageName, packageManager.getApplicationLabel(appInfo).toString())
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    } catch (_: SecurityException) {
                        null
                    }
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .toList()
    }

    /**
     * The packages worth testing against [links]: everything with a launcher entry, everything the
     * system itself resolves one of the links to, and every media-button receiver - a streaming
     * client is one of those, and an alternative the system's own resolution leaves out (an
     * unverified domain, since Android 12) is still found by the first or the last.
     *
     * Three queries that return every app on the phone, so they are asked once per call and not
     * once per service: asked per service they were repeated twelve times for the same answer.
     */
    private fun candidatePackages(context: Context, links: List<String>): List<String> {
        val packageManager = context.packageManager
        return try {
            (packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName } + links.flatMap { link ->
                packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(link)),
                        PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
            } + packageManager.queryBroadcastReceivers(
                    Intent(Intent.ACTION_MEDIA_BUTTON), 0
            ).map { it.activityInfo.packageName })
                    .filter { it != context.packageName }
                    .distinct()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Installed apps that can be used for at least one supported streaming service.
     *
     * This is intentionally the same eligibility check as the per-service app picker.  It keeps
     * the icon picker useful for patched/alternative clients as well as the official clients,
     * without guessing from a package name or exposing arbitrary installed apps.
     *
     * **This is slow and must never run on the main thread.** Every candidate is tested against
     * every service's links with a package-targeted query, which is one binder call each: some
     * thousand of them on a phone with a hundred launcher apps. It used to be called from the icon
     * picker's `show()`, which froze the dialog for seconds before it appeared. Callers go through
     * `BuiltInIconPicker`'s background loader, which also keeps the answer for a while.
     *
     * One pass rather than one per service, since the answer is a union: a package that opens any
     * service's link is in, whichever service that is, so it is not tested against the rest, and the
     * official clients are listed on being installed without testing anything.
     */
    fun availableStreamingApps(context: Context): List<App> {
        val packageManager = context.packageManager
        val services = StreamingService.entries.filter { it != StreamingService.GENERIC }
        val byPackage = linkedMapOf<String, App>()
        services.forEach { service ->
            service.packageName?.let { packageName ->
                app(context, packageName)?.let { byPackage.putIfAbsent(packageName, it) }
            }
        }

        val probes = services.flatMap { service ->
            representativeLinks(service).map { link -> service to link }
        }
        candidatePackages(context, probes.map { it.second }.distinct())
                .filter { it !in byPackage }
                .forEach { packageName ->
                    val opensAService = probes.any { (service, link) ->
                        canOpen(packageManager, packageName,
                                linkForTarget(link, service, packageName))
                    }
                    if (opensAService) app(context, packageName)?.let { byPackage[packageName] = it }
                }
        return byPackage.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun app(context: Context, packageName: String): App? = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        if (!info.enabled || packageName == context.packageName) null else App(
                packageName, context.packageManager.getApplicationLabel(info).toString())
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun Context.isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun canOpen(pm: PackageManager, packageName: String, link: String): Boolean = try {
        pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(link))
                .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(packageName),
                PackageManager.MATCH_DEFAULT_ONLY).any {
            it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled
        }
    } catch (_: RuntimeException) {
        false
    }

    private fun representativeLinks(service: StreamingService): List<String> = when (service) {
        StreamingService.YOUTUBE_MUSIC -> listOf("https://music.youtube.com/watch?list=LM",
                "https://music.youtube.com/watch?v=example", "https://music.youtube.com/playlist?list=example")
        StreamingService.SPOTIFY -> listOf("https://open.spotify.com/playlist/example",
                "https://open.spotify.com/track/example", "https://open.spotify.com/collection/tracks")
        StreamingService.DEEZER -> listOf("https://www.deezer.com/playlist/1")
        StreamingService.TIDAL -> listOf("https://tidal.com/browse/album/1")
        StreamingService.APPLE_MUSIC -> listOf("https://music.apple.com/us/album/example/1")
        StreamingService.AMAZON_MUSIC -> listOf("https://music.amazon.com/albums/example")
        StreamingService.SOUNDCLOUD -> listOf("https://soundcloud.com/you/likes")
        StreamingService.QOBUZ -> listOf("https://www.qobuz.com/album/example/1")
        StreamingService.BANDCAMP -> listOf("https://example.bandcamp.com/album/example")
        StreamingService.AUDIOMACK -> listOf("https://audiomack.com/example/song/example")
        StreamingService.MIXCLOUD -> listOf("https://www.mixcloud.com/example/example/")
        StreamingService.PANDORA -> listOf("https://www.pandora.com/artist/example/1")
        StreamingService.GENERIC -> emptyList()
    }
}
