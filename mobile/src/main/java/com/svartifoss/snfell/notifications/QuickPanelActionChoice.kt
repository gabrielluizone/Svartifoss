package com.svartifoss.snfell.notifications

/** Which of the playing app's two lists of actions feeds the quick panel's session buttons. */
internal enum class QuickActionSource { NOTIFICATION, CUSTOM_ACTIONS, NONE }

/** Play, pause, skip and stop: the controls every face already draws. The same set the watch
 *  ranks last (`MainActivity.quickActionDisplayRank`), so both ends agree on what is redundant. */
private val TRANSPORT_SEMANTICS = setOf(
        MediaActionSemantics.PLAY,
        MediaActionSemantics.PAUSE,
        MediaActionSemantics.PREVIOUS,
        MediaActionSemantics.NEXT,
        MediaActionSemantics.STOP
)

internal fun isTransportSemantic(semantic: String): Boolean = semantic in TRANSPORT_SEMANTICS

/**
 * Where the quick panel's buttons come from when its source is the playing app.
 *
 * The media notification is preferred because its icons draw exactly, but only while it offers
 * something the watch does not already have. YouTube Music's compact notification is Previous,
 * Play and Next - its like, shuffle and repeat live in the session's custom actions - so taking
 * the notification whenever it matched turned the panel into a second row of the transport the
 * face already draws. That is what happened on the Pixel 3 (Android 10) once its notification
 * started matching the session: until then the token mismatch had sent every state down the
 * custom-action path, which is the panel its user had always seen. Hence the order:
 *
 * 1. the notification, when it has at least one action that is not transport;
 * 2. the custom actions, when *they* have one;
 * 3. whichever list has anything at all, notification first, so a player that exposes only
 *    transport still fills the panel rather than reporting it unavailable.
 *
 * Both lists are expected without dislike, which neither source ever puts on the watch.
 */
internal fun chooseQuickActionSource(
        notificationSemantics: List<String>,
        customSemantics: List<String>
): QuickActionSource = when {
    notificationSemantics.any { !isTransportSemantic(it) } -> QuickActionSource.NOTIFICATION
    customSemantics.any { !isTransportSemantic(it) } -> QuickActionSource.CUSTOM_ACTIONS
    notificationSemantics.isNotEmpty() -> QuickActionSource.NOTIFICATION
    customSemantics.isNotEmpty() -> QuickActionSource.CUSTOM_ACTIONS
    else -> QuickActionSource.NONE
}

/**
 * The first [max] of [actions], transport moved behind everything else and the app's own order
 * kept within each group. The cap used to be applied first, so an app listing its like fourth
 * lost it to three transport buttons before the watch's own ranking ever saw it.
 */
internal fun <T> rankQuickActions(actions: List<T>, max: Int, semanticOf: (T) -> String): List<T> =
        actions.sortedBy { if (isTransportSemantic(semanticOf(it))) 1 else 0 }.take(max)
