package com.svartifoss.snfell.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.StatusBarNotification
import android.support.v4.media.session.MediaSessionCompat
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.actions.playback.likeLabelIndicatesAlreadyLiked
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/** A notification action safe to serialize through the Wear Data Layer. */
data class MediaNotificationAction(
        val id: String,
        val label: String,
        val semantic: String,
        val iconPng: ByteArray?
)

/** MediaStyle identifies the buttons the app considers suitable for its compact surface. Mirror
 * that exact set on the watch: filling empty watch slots from the expanded notification made a
 * two-action Spotify panel grow an unrelated third bubble. If compact metadata is absent or
 * malformed, fall back to every notification action for legacy players. */
internal fun orderedNotificationActionIndices(
        actionCount: Int,
        compactIndices: IntArray?
): List<Int> {
    if (actionCount <= 0) return emptyList()
    val compact = (compactIndices ?: IntArray(0)).asSequence()
            .filter { it in 0 until actionCount }
            .distinct()
            .toList()
    return compact.ifEmpty { (0 until actionCount).toList() }
}

/** What one attempt at rendering an icon actually put on the canvas: the area of the bounding box
 * its visible pixels occupy, and how many of them there were. */
internal data class GlyphCoverage(val boundsArea: Int, val inkPixels: Int)

/**
 * Which of several renders of the *same* icon resource to keep, or -1 when none of them drew
 * anything.
 *
 * A notification icon is the publishing app's own asset, and how much of it draws depends on the
 * Context it is resolved against: a `?attr/` colour, a themed group transform or a state-dependent
 * layer resolves one way under that app's application theme and another under ours, and a path
 * that resolves to nothing is simply not drawn. Forcing a white tint cannot bring those back - it
 * recolours what *was* drawn. YouTube Music's shuffle arrived as two bare strokes and the "on"
 * dot, both arrowheads missing, and every check in the pipeline passed it: it was neither empty
 * nor malformed, just incomplete.
 *
 * There is no way to ask a Drawable whether it drew all of itself. There is a reliable comparison,
 * though, because every candidate here is the same asset rendered the same size: the one covering
 * the largest area is the one that lost the fewest paths. Ties keep the earliest candidate, which
 * is the order callers list them in - the publisher's own context first, so an icon that renders
 * identically every way keeps exactly the behaviour it had.
 */
internal fun bestGlyphRender(coverages: List<GlyphCoverage?>): Int {
    var best = -1
    for ((index, coverage) in coverages.withIndex()) {
        if (coverage == null || coverage.inkPixels <= 0 || coverage.boundsArea <= 0) continue
        val incumbent = coverages.getOrNull(best)
        val wins = incumbent == null ||
                coverage.boundsArea > incumbent.boundsArea ||
                (coverage.boundsArea == incumbent.boundsArea &&
                        coverage.inkPixels > incumbent.inkPixels)
        if (wins) best = index
    }
    return best
}

/** Dislike is deliberately not a watch shortcut. It is easy to hit accidentally on a tiny round
 * display and YouTube Music's fourth dislike action used to distort the three-button layout. */
internal fun <T> discardDislikeActions(actions: List<T>, semanticOf: (T) -> String): List<T> =
        actions.filterNot { semanticOf(it) == MediaActionSemantics.DISLIKE }

/**
 * Process-local bridge between [android.service.notification.NotificationListenerService] and
 * MusicService. Notification actions cannot be sent directly to the watch because PendingIntent
 * and remote-package Icon objects are phone-only. This registry keeps the PendingIntent here,
 * sends a stable id plus rasterized icon to the watch, then resolves the id again when tapped.
 */
object MediaNotificationActions {
    private const val ACTION_PREFIX = "notification:"

    /**
     * Canvas for an action glyph.
     *
     * It was 48px, which is the size the *bitmap* was then drawn at on the wrist rather than a
     * lower bound on its sharpness: the watch fits these with CENTER_INSIDE, which never scales a
     * bitmap up, so a 48px raster inside a 52dp button rendered at 24dp on a 2.0-density watch and
     * 16dp on a 3.0-density one - a quarter smaller than the same action drawn by the system's own
     * media controls beside it, and the app's icons alone, since this app's own fallback glyphs
     * are vectors that fill their button. Sized to the largest button the panel can show instead,
     * so the fit has room to work on any density.
     */
    private const val ICON_SIZE_PX = 128

    /**
     * Canvas for the playing-app's *source* icon, which is a different job from an action glyph.
     *
     * The source icon is drawn as large as the Split face's seam mark - up to 52dp, which is
     * ~130px on a high-density round watch. Kept as its own constant rather than sharing
     * [ICON_SIZE_PX]: that one is paid up to three times per state change, this one once.
     */
    private const val SOURCE_ICON_SIZE_PX = 144
    private const val MAX_ACTIONS = 3

    /** Alpha above which a pixel counts as part of the glyph rather than as antialiasing spill.
     * Shared by the phone's render measurement and, by value, the watch's defensive check. */
    private const val VISIBLE_ALPHA_FLOOR = 12

    private data class StoredAction(
            val notificationActionIndex: Int,
            val identity: MediaActionIdentity,
            val publicAction: MediaNotificationAction,
            val pendingIntent: PendingIntent
    )

    /** The app's "like/save" notification action, found across ALL notification actions (not
     *  only the compact set mirrored to the watch): Spotify's heart lives in the expanded
     *  actions, so the dedicated Like button on the watch needs a way to reach it. [intent] is
     *  phone-only; [liked] is a best-effort guess from the action's own label (see
     *  [likeLabelIndicatesAlreadyLiked]) for apps - SoundCloud among them - whose "like" never
     *  becomes a MediaSession custom action, so [com.svartifoss.snfell.actions.playback.LikeAction.isCurrentlyLiked]
     *  never sees it. */
    private data class LikeNotificationAction(
            val intent: PendingIntent,
            val liked: Boolean
    )

    private data class StoredNotification(
            val packageName: String,
            val notificationKey: String,
            val sessionToken: MediaSession.Token?,
            val postedAt: Long,
            val actions: List<StoredAction>,
            val likeAction: LikeNotificationAction?,
            // The notification's own small icon (the branded glyph the status bar shows), used
            // as the watch's source-icon element. This is what users recognise as "the app icon
            // in the notification"; the launcher icon is different, chunkier artwork.
            val smallIconPng: ByteArray?
    )

    private val notifications = LinkedHashMap<String, StoredNotification>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val actionIdGeneration = UUID.randomUUID().toString()
    private val actionIdSequence = AtomicLong()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun update(context: Context, sbn: StatusBarNotification) {
        val notification = sbn.notification
        val sessionToken = notificationMediaSessionToken(notification)
        val isMedia = notification.category == Notification.CATEGORY_TRANSPORT ||
                sessionToken != null ||
                notification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true

        // Re-use an id only while the action really is the same. In particular, many players
        // replace the PendingIntent and icon in the same notification slot when play becomes
        // pause. A versioned id makes a delayed tap from the watch harmless: once the slot has
        // changed, the old id is no longer present in this registry.
        val previous = synchronized(this) { notifications[sbn.key] }
        val canReusePreviousIds = previous?.packageName == sbn.packageName &&
                previous.sessionToken == sessionToken
        val reusablePreviousActions = if (canReusePreviousIds) previous?.actions.orEmpty() else emptyList()
        val usedPreviousActionIndices = HashSet<Int>()
        val notificationActions = notification.actions.orEmpty()
        val compactIndices = try {
            notification.extras?.getIntArray(Notification.EXTRA_COMPACT_ACTIONS)
        } catch (_: RuntimeException) {
            null
        }

        // Learned before the actions are walked, and deliberately outside the
        // `takeIf { it.isNotEmpty() }` below: a media notification with no *actions* still carries
        // the app's glyph, and that glyph is wanted for a row whose whole purpose is launching an
        // app that is not playing. See AppGlyphStore for why it has to outlive this registry.
        // Lazily, because update() runs for every notification on the phone and only a media one
        // has icons to resolve - building another package's Contexts for a chat message would be
        // pure cost. Local to this call, so the cheapest lazy mode is the correct one.
        val publisherContexts by lazy(LazyThreadSafetyMode.NONE) {
            publisherContexts(context, sbn.packageName)
        }
        val smallIconPng = if (isMedia) loadSmallIconPng(publisherContexts, sbn) else null
        if (isMedia) AppGlyphStore.remember(context, sbn.packageName, smallIconPng)

        val stored = if (isMedia) {
            orderedNotificationActionIndices(notificationActions.size, compactIndices)
                    .asSequence()
                    .mapNotNull { index ->
                        val action = notificationActions[index]
                        val intent = action.actionIntent ?: return@mapNotNull null
                        val label = action.title?.toString().orEmpty()
                        val semantic = notificationActionSemantic(action, label)
                        val iconPng = rasterizePng(
                                actionDrawableCandidates(publisherContexts, action))
                        val identity = mediaActionIdentity(
                                sourceId = null,
                                label = label,
                                semantic = semantic,
                                iconPng = iconPng
                        )
                        // Notification apps are free to insert or reorder actions between
                        // updates. Match by the immutable execution target and complete visible
                        // identity, never by array position alone.
                        val previousMatch = findStableActionMatch(
                                previous = reusablePreviousActions,
                                currentIdentity = identity,
                                alreadyUsed = usedPreviousActionIndices,
                                identityOf = StoredAction::identity,
                                hasSameExecutionTarget = { it.pendingIntent == intent }
                        )
                        previousMatch?.let { usedPreviousActionIndices += it.index }
                        val id = previousMatch?.value?.publicAction?.id ?: run {
                            "$ACTION_PREFIX${sbn.key}:$actionIdGeneration:" +
                                    "${actionIdSequence.incrementAndGet()}:$index"
                        }
                        StoredAction(
                                notificationActionIndex = index,
                                identity = identity,
                                publicAction = MediaNotificationAction(
                                        id = id,
                                        label = label,
                                        semantic = semantic,
                                        iconPng = iconPng
                                ),
                                pendingIntent = intent
                        )
                    }
                    .toList()
                    .let { discardDislikeActions(it) { stored -> stored.publicAction.semantic } }
                    .take(MAX_ACTIONS)
                    .takeIf { it.isNotEmpty() }
                    ?.let { actions ->
                        StoredNotification(
                                packageName = sbn.packageName,
                                notificationKey = sbn.key,
                                sessionToken = sessionToken,
                                postedAt = sbn.postTime,
                                actions = actions,
                                likeAction = findLikeAction(notificationActions),
                                smallIconPng = smallIconPng
                        )
                    }
        } else {
            null
        }

        val changed = synchronized(this) {
            if (stored == null) {
                notifications.remove(sbn.key) != null
            } else {
                val old = notifications.put(sbn.key, stored)
                old == null || !old.hasSamePublicContent(stored)
            }
        }
        if (changed) notifyChanged()
    }

    fun remove(notificationKey: String) {
        val changed = synchronized(this) { notifications.remove(notificationKey) != null }
        if (changed) notifyChanged()
    }

    fun clear() {
        val changed = synchronized(this) {
            val hadEntries = notifications.isNotEmpty()
            notifications.clear()
            hadEntries
        }
        if (changed) notifyChanged()
    }

    /**
     * Returns actions belonging to the controller that is actually active. Package matching is
     * still the compatibility fallback because some legacy/OEM notifications omit a readable
     * media-session token, but an exact token match always wins when one is available.
     */
    fun actionsForSession(
            packageName: String,
            sessionToken: MediaSession.Token?
    ): List<MediaNotificationAction> = synchronized(this) {
        val packageNotifications = notifications.values
                .asSequence()
                .filter { it.packageName == packageName }
                .toList()

        val exactSession = sessionToken?.let { activeToken ->
            packageNotifications.asSequence()
                    .filter { it.sessionToken == activeToken }
                    .maxByOrNull { it.postedAt }
        }
        val packageFallback = packageNotifications.asSequence()
                // A missing notification token is the compatibility case. Do not borrow a
                // notification that positively identifies a different session of the same app.
                .filter { sessionToken == null || it.sessionToken == null }
                .maxByOrNull { it.postedAt }

        (exactSession ?: packageFallback)
                ?.actions
                ?.map { it.publicAction }
                .orEmpty()
    }

    /** Resolves an id only inside the currently active package/session. */
    fun execute(
            actionId: String,
            packageName: String,
            sessionToken: MediaSession.Token?
    ): Boolean {
        val action = synchronized(this) {
            notifications.values.asSequence()
                    .filter { notification ->
                        notification.packageName == packageName &&
                                (sessionToken == null || notification.sessionToken == null ||
                                        notification.sessionToken == sessionToken)
                    }
                    .flatMap { it.actions.asSequence() }
                    .firstOrNull { it.publicAction.id == actionId }
        } ?: return false

        return try {
            action.pendingIntent.send()
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        }
    }

    fun isNotificationAction(actionId: String): Boolean = actionId.startsWith(ACTION_PREFIX)

    /** Fires the app's like/save notification action (e.g. Spotify's heart) for the active
     *  package/session, if one was found. Lets the watch's dedicated Like button work for apps
     *  that expose "like" only as a notification action, not a MediaSession custom action. */
    fun executeLike(packageName: String, sessionToken: MediaSession.Token?): Boolean {
        val intent = synchronized(this) {
            notifications.values.asSequence()
                    .filter { notification ->
                        notification.packageName == packageName &&
                                (sessionToken == null || notification.sessionToken == null ||
                                        notification.sessionToken == sessionToken)
                    }
                    .mapNotNull { it.likeAction?.intent }
                    .firstOrNull()
        } ?: return false
        return try {
            intent.send()
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        }
    }

    /** Best-effort guess at whether the active package/session's track is already liked, read
     *  from its like/save notification action's own label. This is the only signal available for
     *  apps - SoundCloud among them - that expose "like" solely as a `Notification.Action` rather
     *  than a MediaSession custom action, so [MusicService][com.svartifoss.snfell.music.MusicService]
     *  falls back to it only when no custom action was found. False (not "unknown") when no like
     *  action was found at all, matching
     *  [LikeAction.isCurrentlyLiked][com.svartifoss.snfell.actions.playback.LikeAction.isCurrentlyLiked]'s
     *  own default. */
    fun likedStateForSession(packageName: String, sessionToken: MediaSession.Token?): Boolean {
        val action = synchronized(this) {
            notifications.values.asSequence()
                    .filter { notification ->
                        notification.packageName == packageName &&
                                (sessionToken == null || notification.sessionToken == null ||
                                        notification.sessionToken == sessionToken)
                    }
                    .mapNotNull { it.likeAction }
                    .firstOrNull()
        }
        return action?.liked ?: false
    }

    /** First notification action (across the full list, not just the compact set) classified as a
     *  like/save, so the Like button can reach a heart that lives in the expanded actions. */
    private fun findLikeAction(
            notificationActions: Array<out Notification.Action>
    ): LikeNotificationAction? {
        for (action in notificationActions) {
            val intent = action.actionIntent ?: continue
            val label = action.title?.toString().orEmpty()
            val semantic = notificationActionSemantic(action, label)
            if (semantic == MediaActionSemantics.LIKE) {
                return LikeNotificationAction(intent, likeLabelIndicatesAlreadyLiked(label))
            }
        }
        return null
    }

    /** Rasterizes a framework MediaSession.CustomAction resource in the publishing app's own
     * package context. Resource ids are package-local and therefore cannot cross to Wear as-is. */
    fun loadRemoteActionIcon(
            context: Context,
            packageName: String,
            resourceId: Int
    ): ByteArray? {
        if (resourceId == 0) return null
        return rasterizePng(
                remoteResourceCandidates(publisherContexts(context, packageName), resourceId))
    }

    /**
     * PNG of the small icon published by the media notification of [packageName] - the same glyph
     * the status bar shows. Token matching wins when available, mirroring [actionsForSession];
     * null when that app currently has no stored media notification, in which case the caller is
     * expected to fall back to the launcher icon.
     */
    fun smallIconForSession(
            packageName: String,
            sessionToken: MediaSession.Token?
    ): ByteArray? = synchronized(this) {
        val candidates = notifications.values.filter { it.packageName == packageName }
        val match = sessionToken?.let { token -> candidates.lastOrNull { it.sessionToken == token } }
                ?: candidates.maxByOrNull { it.postedAt }
        match?.smallIconPng
    }

    /** Rasterizes the notification's small icon. Shares [rasterizePng] with the action icons, so
     * the glyph arrives on the same optical canvas and flat-white template tint the watch already
     * knows how to tint. */
    @Suppress("DEPRECATION")
    private fun loadSmallIconPng(
            contexts: PublisherContexts,
            sbn: StatusBarNotification
    ): ByteArray? = try {
        val notification = sbn.notification
        val candidates = notification.smallIcon
                ?.let { icon -> iconDrawableCandidates(contexts, icon) }
                .orEmpty()
                .ifEmpty { remoteResourceCandidates(contexts, notification.icon) }
        rasterizePng(candidates, SOURCE_ICON_SIZE_PX)
    } catch (_: Exception) {
        null
    }

    private fun notifyChanged() {
        listeners.forEach { it.invoke() }
    }

    /** Data classes compare ByteArray by reference; notification icons need content equality. */
    private fun StoredNotification.hasSamePublicContent(other: StoredNotification): Boolean =
            packageName == other.packageName &&
                    sessionToken == other.sessionToken &&
                    smallIconPng.contentEqualsNullable(other.smallIconPng) &&
                    actions.size == other.actions.size &&
                    actions.zip(other.actions).all { (left, right) ->
                        left.publicAction.id == right.publicAction.id &&
                                left.publicAction.label == right.publicAction.label &&
                                left.publicAction.semantic == right.publicAction.semantic &&
                                left.publicAction.iconPng.contentEqualsNullable(
                                        right.publicAction.iconPng
                                )
                    }

    private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
        this === other -> true
        this == null || other == null -> false
        else -> contentEquals(other)
    }

    @Suppress("DEPRECATION")
    private fun notificationMediaSessionToken(notification: Notification): MediaSession.Token? {
        return try {
            when (val rawToken = notification.extras?.get(Notification.EXTRA_MEDIA_SESSION)) {
                is MediaSession.Token -> rawToken
                is MediaSessionCompat.Token -> rawToken.token as? MediaSession.Token
                else -> null
            }
        } catch (_: RuntimeException) {
            // Malformed/OEM Parcelable extras must not take down the notification listener.
            null
        }
    }

    /**
     * Every way this action's icon can legitimately be loaded, most conservative first.
     *
     * The system draws a notification action through *its own* Context, and that render - the one
     * in the phone's notification shade - is the one the user considers correct. This app draws it
     * through a Context created from the publishing package instead, because a resource Icon that
     * carries no package name would otherwise resolve against our own resources and pick up an
     * unrelated drawable of ours that happens to share the integer id.
     *
     * Both are defensible and they do not always agree, which is exactly the problem: a theme
     * attribute inside the app's own vector resolves differently under each, and whatever fails to
     * resolve is not drawn at all. So load it every way available and let [bestGlyphRender] keep
     * the render that drew the most of the glyph, rather than betting the icon on one of them.
     */
    @Suppress("DEPRECATION")
    private fun actionDrawableCandidates(
            contexts: PublisherContexts,
            action: Notification.Action
    ): List<Drawable> {
        val candidates = ArrayList<Drawable>(4)
        val icon = try {
            action.getIcon()
        } catch (_: Exception) {
            // Some OEM/media apps publish a resource Icon whose package context Icon.loadDrawable
            // cannot resolve. The explicit resource-id path below still can.
            null
        }
        icon?.let { candidates += iconDrawableCandidates(contexts, it) }
        candidates += remoteResourceCandidates(contexts, action.icon)
        return candidates
    }

    /**
     * The contexts one notification's icons are resolved against, built once per update.
     *
     * Context.createPackageContext loads another package's resources, which is not free, and this
     * runs on the notification listener's callback thread for every media notification a player
     * posts - which for some players is every second of playback. Building these per candidate
     * would have meant a dozen of them per update to gain nothing: they are the same two contexts
     * every time.
     */
    private class PublisherContexts(
            val app: Context,
            val themed: Context?,
            val plain: Context?
    )

    private fun publisherContexts(context: Context, packageName: String) = PublisherContexts(
            app = context,
            themed = createThemedPackageContext(context, packageName),
            plain = createPackageContext(context, packageName)
    )

    /** [actionDrawableCandidates]' rule applied to any [Icon]: publisher-themed, publisher
     * un-themed, then - only when the icon names the resources it comes from, so this cannot reach
     * one of our own drawables by id collision - the system's own way of loading it. */
    private fun iconDrawableCandidates(
            contexts: PublisherContexts,
            icon: Icon
    ): List<Drawable> {
        val candidates = ArrayList<Drawable>(3)
        candidates.addDrawable { contexts.themed?.let(icon::loadDrawable) }
        candidates.addDrawable { contexts.plain?.let(icon::loadDrawable) }
        if (iconCarriesItsOwnResources(contexts.app, icon)) {
            candidates.addDrawable { icon.loadDrawable(contexts.app) }
        }
        return candidates
    }

    /** The same pair of contexts for an icon known only by its resource id - MediaSession custom
     * actions and the legacy `Notification.Action.icon` field. */
    private fun remoteResourceCandidates(
            contexts: PublisherContexts,
            resourceId: Int
    ): List<Drawable> {
        if (resourceId == 0) return emptyList()
        val candidates = ArrayList<Drawable>(2)
        candidates.addDrawable {
            contexts.themed?.let { AppCompatResources.getDrawable(it, resourceId) }
        }
        candidates.addDrawable {
            contexts.plain?.let { AppCompatResources.getDrawable(it, resourceId) }
        }
        return candidates
    }

    private fun MutableList<Drawable>.addDrawable(load: () -> Drawable?) {
        try {
            load()?.let { add(it) }
        } catch (_: Exception) {
            // A candidate that cannot be loaded is not a failure - the others still stand, and an
            // icon with no candidate at all becomes the watch's own semantic glyph.
        }
    }

    /**
     * Whether loading [icon] through *our* Context still reaches the publisher's asset, i.e. the
     * icon names the resources it comes from. Only then is the system's own render safe to add as
     * a candidate; a package-less resource Icon would resolve its integer id against this app's
     * resources and produce a completely unrelated drawable of ours.
     *
     * Icon.getType()/getResPackage() are public from API 28 (they existed hidden before that), and
     * compileSdk resolves them happily against a minSdk 23 device that would then throw
     * NoSuchMethodError - so on older releases the answer is "cannot tell", and the
     * publisher-context-only rule stands there exactly as before.
     */
    private fun iconCarriesItsOwnResources(context: Context, icon: Icon): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            icon.type != Icon.TYPE_RESOURCE ||
                    icon.resPackage.orEmpty().let {
                        it.isNotEmpty() && it != context.packageName
                    }
        } catch (_: Exception) {
            false
        }
    }

    private fun createPackageContext(context: Context, packageName: String): Context? = try {
        context.createPackageContext(packageName, 0)
    } catch (_: Exception) {
        null
    }

    /** A package Context created via [Context.createPackageContext] carries no theme, so any
     * `?attr/...` reference inside a remote app's icon (common in Material-styled vector
     * drawables) resolves against undefined/default values instead of that app's own theme. Apply
     * the publisher's declared theme when the framework can resolve one. */
    private fun createThemedPackageContext(context: Context, packageName: String): Context? {
        val remoteContext = createPackageContext(context, packageName) ?: return null
        try {
            val themeResId = context.packageManager.getApplicationInfo(packageName, 0).theme
            if (themeResId != 0) {
                remoteContext.setTheme(themeResId)
            }
        } catch (_: Exception) {
            // Theming is a best-effort legibility improvement; an untheme'd context still works
            // for icons that don't reference theme attributes.
        }
        return remoteContext
    }

    private fun notificationActionSemantic(
            action: Notification.Action,
            label: String
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            when (action.semanticAction) {
                Notification.Action.SEMANTIC_ACTION_THUMBS_UP -> return MediaActionSemantics.LIKE
                Notification.Action.SEMANTIC_ACTION_THUMBS_DOWN ->
                    return MediaActionSemantics.DISLIKE
            }
        }
        return inferMediaActionSemantic(label)
    }

    /** A launcher-style AdaptiveIconDrawable pads its visible glyph inside a much larger safe
     * zone (its intrinsic size describes the full canvas, not just the glyph), so fitting by
     * intrinsic size alone would render it far smaller than every other action icon. Draw only
     * its foreground layer instead when one is present. */
    private fun unwrapAdaptiveIcon(drawable: Drawable): Drawable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                drawable is android.graphics.drawable.AdaptiveIconDrawable) {
            drawable.foreground?.let { return it }
        }
        return drawable
    }

    /**
     * PNG of the best render available for one icon, or null when nothing drew.
     *
     * [candidates] are the same icon loaded through different Contexts; each is rendered and
     * [bestGlyphRender] decides which to keep. Choosing has to happen *before*
     * [normalizeTemplateBitmap], which scales whatever survived up to fill the canvas: run in the
     * other order, a fragment and the whole glyph both come out filling the frame and there is
     * nothing left to compare. That normalization is also why a partial render was never merely
     * small on the wrist - it was magnified until it looked deliberate.
     */
    private fun rasterizePng(
            candidates: List<Drawable>,
            sizePx: Int = ICON_SIZE_PX
    ): ByteArray? {
        if (candidates.isEmpty()) return null
        val renders = candidates.map { renderTemplate(it, sizePx) }
        val extents = renders.map { render -> render?.let(::measureGlyph) }
        val chosen = bestGlyphRender(extents.map { it?.coverage })
        // A few apps publish an action drawable that resolves to a fully transparent vector in
        // another package's theme. Do not serialize that as a "valid" PNG: an absent image lets
        // the watch use the semantic fallback glyph instead of showing an empty pill.
        if (chosen < 0) return null
        if (chosen != 0) {
            Timber.v("Notification icon: candidate %d of %d drew the most of the glyph (%s)",
                    chosen, renders.size, extents[chosen]?.coverage)
        }
        val source = renders[chosen] ?: return null
        val extent = extents[chosen] ?: return null
        return try {
            val normalized = normalizeTemplateBitmap(source, extent.bounds, sizePx)
            ByteArrayOutputStream().use { output ->
                normalized.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Draws one candidate onto the shared template canvas: settled, flat white, fitted whole. */
    private fun renderTemplate(sourceDrawable: Drawable, sizePx: Int): Bitmap? = try {
        val drawable = unwrapAdaptiveIcon(settledDrawable(sourceDrawable)).mutate()
        // Media notification action icons are monochrome templates the system tints itself. Force
        // a flat white tint so a glyph whose own colour resolved to something invisible in this
        // context still reads; the watch then tints it to the panel's chrome colour. The button's
        // active/inactive state stays readable because the icon's own shape (e.g. filled vs
        // outlined thumb) still carries it. Note the limit of this, which was misread once: a path
        // that was not drawn at all has no pixels for a tint to recolour, and no amount of tinting
        // recovers it - that is what rendering several candidates is for.
        drawable.setTint(Color.WHITE)
        drawable.setTintMode(android.graphics.PorterDuff.Mode.SRC_IN)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = sizePx / 10
        val available = sizePx - inset * 2
        val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: available
        val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: available
        val scale = minOf(
                available.toFloat() / intrinsicWidth,
                available.toFloat() / intrinsicHeight
        )
        val width = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
        val height = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
        val left = (sizePx - width) / 2
        val top = (sizePx - height) / 2
        drawable.setBounds(left, top, left + width, top + height)
        drawable.draw(canvas)
        bitmap
    } catch (_: Exception) {
        null
    }

    /**
     * The frame a drawable settles on, rather than whichever one it happens to be holding.
     *
     * Players ship their toggles as state lists and transition drawables - one slot is both
     * "shuffle off" and "shuffle on" - and one just loaded has been given no state and has not run
     * its transition. Ending that first is what makes the raster a picture of a button rather than
     * of an animation that never started. Two levels is enough for the real shape of these assets
     * (a state list whose selected child is itself a transition) and bounds the walk.
     */
    private fun settledDrawable(source: Drawable): Drawable {
        var drawable = source
        repeat(2) {
            try {
                drawable.jumpToCurrentState()
            } catch (_: Exception) {
                return drawable
            }
            val child = (drawable as? DrawableContainer)?.current
            if (child == null || child === drawable) return drawable
            drawable = child
        }
        return drawable
    }

    private data class GlyphExtent(val bounds: Rect, val coverage: GlyphCoverage)

    /** Visible bounds and ink of one render, in a single getPixels pass - this runs for every
     * candidate of every action of every notification update, so the per-pixel getPixel scan it
     * replaced is not what should be paying for the extra renders. */
    private fun measureGlyph(bitmap: Bitmap): GlyphExtent? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        var ink = 0
        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                if (Color.alpha(pixels[rowStart + x]) > VISIBLE_ALPHA_FLOOR) {
                    ink++
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return null
        return GlyphExtent(
                bounds = Rect(left, top, right + 1, bottom + 1),
                coverage = GlyphCoverage(
                        boundsArea = (right - left + 1) * (bottom - top + 1),
                        inkPixels = ink
                )
        )
    }

    /** Crops transparent/intrinsic padding and places the visible glyph on a common optical
     * canvas. Players ship wildly different vector viewBoxes: without this pass Spotify looked
     * tiny, while YouTube Music's asymmetric padding pushed glyphs off-centre. */
    private fun normalizeTemplateBitmap(source: Bitmap, visible: Rect, sizePx: Int): Bitmap {
        val visibleWidth = visible.width()
        val visibleHeight = visible.height()
        val target = sizePx * .77f
        val scale = minOf(target / visibleWidth, target / visibleHeight)
        val width = visibleWidth * scale
        val height = visibleHeight * scale
        val destination = RectF(
                (sizePx - width) / 2f,
                (sizePx - height) / 2f,
                (sizePx + width) / 2f,
                (sizePx + height) / 2f)
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(
                    source,
                    visible,
                    destination,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

}
