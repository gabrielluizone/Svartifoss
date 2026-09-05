package com.svartifoss.snfell.view.watchface.theme

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * The per-account ledger behind the gallery's download figure.
 *
 * Nothing on the serving side can produce that number. The catalogue is JSON committed to this
 * repository and handed out by GitHub Pages, so a request there counts how often a phone refreshed
 * its cached copy of the *whole list*, not how often somebody took a theme — and the profile
 * fetched on the way to a detail screen is a preview, not an install. What is countable is the
 * moment an install actually succeeds on a phone, which is what this writes: one immutable
 * document at `communityThemeInstalls/<theme id>/installers/<Firebase UID>`, aggregated into the
 * static catalogue by the same trusted publisher run that counts likes.
 *
 * Three properties follow from that and each is deliberate.
 *
 * It needs **no account of its own**. Like [CommunityThemeLikeRepository] it provisions an
 * anonymous Firebase UID silently, so installing a theme never presents a sign-in. It inherits the
 * same accepted imprecision: clearing app data yields a new UID, so the same person can be counted
 * twice. This is a popularity signal, not a metered distribution count, and the privacy policy
 * says so rather than implying a precision the design cannot deliver.
 *
 * It is **create-only, and removal never decrements it**. Taking a theme back out of My themes
 * does not un-download it. Offering the opposite would turn the published figure into a live
 * installed-count that walks backwards, inside a file that is only rewritten about weekly — so the
 * number on screen would disagree with itself far more often than it would be informative.
 *
 * And it **never fails the install**. Every entry point returns rather than throws, because the
 * user's theme is already applied by the time this runs: a Firestore outage, a revoked anonymous
 * provider or a theme the publication registry has not caught up with must all cost the count,
 * never the thing the user actually asked for.
 */
class CommunityThemeInstallRepository(
        private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
        private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * [recordInstall] on a scope that outlives the caller.
     *
     * The screen that installs a theme closes in the same gesture, so a `lifecycleScope` launch
     * would be cancelled milliseconds later and the count would be lost precisely on the successful
     * path — the same trap the watch-shutdown send documents in `MusicService`. Nothing waits on
     * the result: the theme is already applied and the ledger is a background detail.
     */
    fun recordInstallDetached(themeId: String) {
        RECORD_SCOPE.launch { recordInstall(themeId) }
    }

    /**
     * Records that this account installed [themeId], if it has not already.
     *
     * The existence check is what makes re-installing the same theme silent instead of an error:
     * the rules accept only a true create, so writing unconditionally would be denied on the
     * second install of a theme somebody had removed and taken again. The transaction also makes
     * two devices racing on the same account idempotent.
     */
    suspend fun recordInstall(themeId: String) {
        if (!isCanonicalThemeId(themeId)) return
        try {
            val reference = installDocument(themeId, requireAccountUid())
            firestore.runTransaction { transaction ->
                if (!transaction.get(reference).exists()) {
                    transaction.set(reference, hashMapOf(
                            "schemaVersion" to INSTALL_SCHEMA_VERSION,
                            "createdAt" to FieldValue.serverTimestamp()))
                }
            }.await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Deliberately swallowed at debug level. The install has already succeeded and there
            // is nothing here the user could act on, so a toast would only report a failure of
            // something they never asked for.
            Timber.d(error, "Could not record a community-theme install")
        }
    }

    private suspend fun requireAccountUid(): String =
            auth.currentUser?.uid
                    ?: auth.signInAnonymously().await().user?.uid
                    ?: throw IllegalStateException("Firebase returned no anonymous install account")

    private fun installDocument(themeId: String, uid: String) = firestore
            .collection(INSTALLS_COLLECTION)
            .document(themeId)
            .collection(INSTALLERS_COLLECTION)
            .document(uid)

    /** Mirrors the UUIDv4-only Firestore document-id contract. */
    private fun isCanonicalThemeId(raw: String): Boolean = UUID_V4.matches(raw)

    private companion object {
        /** Process-scoped: every caller finishes its own screen in the same gesture. */
        val RECORD_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val INSTALLS_COLLECTION = "communityThemeInstalls"
        const val INSTALLERS_COLLECTION = "installers"
        const val INSTALL_SCHEMA_VERSION = 1
        val UUID_V4 = Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
