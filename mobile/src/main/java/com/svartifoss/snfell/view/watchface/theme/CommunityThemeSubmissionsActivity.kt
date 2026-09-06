package com.svartifoss.snfell.view.watchface.theme

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.MusicLoadingBarsView
import com.svartifoss.snfell.view.applyLyraDialogStyling
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * What this account has sent to the community gallery, and where each submission stands.
 *
 * Submitting used to be write-only: a theme left the phone and the only way to learn its outcome
 * was to notice it appear in the gallery, which says nothing at all about one that was rejected.
 * The screen exists because a queue nobody can see is indistinguishable from a queue that lost
 * your theme.
 *
 * It shows status and never a reason or a reviewer. The reviewer's identity is deliberately not
 * readable here (see [CommunityThemeSubmissionsRepository]), and a free-text rejection reason is
 * not something the moderation flow records yet -- inventing one on this side would be guessing at
 * a verdict rather than reporting it.
 */
class CommunityThemeSubmissionsActivity : AppCompatActivity() {

    private lateinit var submissions: CommunityThemeSubmissionsRepository
    private lateinit var onlineThemes: OnlineThemesRepository
    private lateinit var container: LinearLayout
    private lateinit var message: TextView
    private lateinit var progress: MusicLoadingBarsView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_theme_submissions)

        submissions = CommunityThemeSubmissionsRepository()
        onlineThemes = OnlineThemesRepository(this)
        container = findViewById(R.id.submissions_container)
        message = findViewById(R.id.submissions_message)
        progress = findViewById(R.id.submissions_progress)
        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
        progress.setBarsColor(LyraAccent.contrastSafe(
                LyraAccent.resolve(this),
                getColor(R.color.lyra_background),
                minimumContrast = 3.0))
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        container.removeAllViews()
        message.visibility = View.GONE
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val state = submissions.mySubmissions()
            // Counts come from the public catalogue, so a submission still in review simply has
            // none rather than a zero that would read as "nobody liked it".
            val likes = onlineThemes.publishedLikeCounts()
            val installs = onlineThemes.publishedInstallCounts()
            progress.visibility = View.GONE
            when (state) {
                is CommunityThemeSubmissionsState.Loaded ->
                    render(CommunityThemeSubmissionOrder.withPublicCounts(
                            state.records, likes, installs))
                CommunityThemeSubmissionsState.SignedOut ->
                    showMessage(R.string.community_theme_submissions_signed_out)
                is CommunityThemeSubmissionsState.Failed -> {
                    Timber.w(state.error, "Could not load this account's community submissions")
                    showMessage(R.string.community_theme_submissions_error)
                }
            }
        }
    }

    private fun render(records: List<CommunityThemeSubmissionRecord>) {
        if (records.isEmpty()) {
            showMessage(R.string.community_theme_submissions_empty)
            return
        }
        val inflater = LayoutInflater.from(this)
        records.forEach { record ->
            val row = inflater.inflate(R.layout.item_community_theme_submission, container, false)
            val status = row.findViewById<TextView>(R.id.submission_status)
            status.setText(statusLabel(record.status))
            status.setTextColor(statusColor(record.status))
            row.findViewById<TextView>(R.id.submission_name).text = record.name
            row.findViewById<TextView>(R.id.submission_meta).text = metaLine(record)
            row.findViewById<TextView>(R.id.submission_explanation)
                    .setText(statusExplanation(record.status))
            val likes = row.findViewById<TextView>(R.id.submission_likes)
            if (record.likes != null) {
                // One line rather than two controls: an author reading their own list wants both
                // figures at a glance, and a published theme always has both or neither.
                likes.text = buildString {
                    append(resources.getQuantityString(
                            R.plurals.community_theme_submissions_likes,
                            record.likes,
                            record.likes))
                    record.installs?.let { installs ->
                        append(" \u00b7 ")
                        append(resources.getQuantityString(
                                R.plurals.community_theme_submissions_downloads,
                                installs,
                                installs))
                    }
                }
                likes.visibility = View.VISIBLE
            } else {
                likes.visibility = View.GONE
            }
            val remove = row.findViewById<MaterialButton>(R.id.submission_remove)
            // Nothing left to take down once it is already on its way out.
            if (record.status == CommunityThemeSubmissionStatus.WITHDRAWN) {
                remove.visibility = View.GONE
            } else {
                remove.visibility = View.VISIBLE
                remove.setOnClickListener { confirmRemoval(record) }
            }
            container.addView(row)
        }
    }

    private fun metaLine(record: CommunityThemeSubmissionRecord): String {
        val layout = WatchThemeRepository.displayNameForFace(this, record.baseFace)
        val submitted = record.createdAtMillis?.let { millis ->
            DateUtils.getRelativeTimeSpanString(
                    millis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS).toString()
        }
        return listOfNotNull(
                getString(R.string.community_theme_submissions_layout, layout),
                submitted).joinToString(" · ")
    }

    private fun statusLabel(status: CommunityThemeSubmissionStatus): Int = when (status) {
        CommunityThemeSubmissionStatus.PENDING -> R.string.community_theme_submissions_status_pending
        CommunityThemeSubmissionStatus.APPROVED -> R.string.community_theme_submissions_status_approved
        CommunityThemeSubmissionStatus.PUBLISHED -> R.string.community_theme_submissions_status_published
        CommunityThemeSubmissionStatus.REJECTED -> R.string.community_theme_submissions_status_rejected
        CommunityThemeSubmissionStatus.WITHDRAWN -> R.string.community_theme_submissions_status_withdrawn
        CommunityThemeSubmissionStatus.UNKNOWN -> R.string.community_theme_submissions_status_unknown
    }

    private fun statusExplanation(status: CommunityThemeSubmissionStatus): Int = when (status) {
        CommunityThemeSubmissionStatus.PENDING -> R.string.community_theme_submissions_pending_detail
        CommunityThemeSubmissionStatus.APPROVED -> R.string.community_theme_submissions_approved_detail
        CommunityThemeSubmissionStatus.PUBLISHED -> R.string.community_theme_submissions_published_detail
        CommunityThemeSubmissionStatus.REJECTED -> R.string.community_theme_submissions_rejected_detail
        CommunityThemeSubmissionStatus.WITHDRAWN -> R.string.community_theme_submissions_withdrawn_detail
        CommunityThemeSubmissionStatus.UNKNOWN -> R.string.community_theme_submissions_unknown_detail
    }

    private fun statusColor(status: CommunityThemeSubmissionStatus): Int = when (status) {
        CommunityThemeSubmissionStatus.PUBLISHED, CommunityThemeSubmissionStatus.APPROVED ->
            LyraAccent.contrastSafe(
                    LyraAccent.resolve(this),
                    getColor(R.color.lyra_background),
                    minimumContrast = 4.5)
        CommunityThemeSubmissionStatus.REJECTED, CommunityThemeSubmissionStatus.WITHDRAWN ->
            getColor(R.color.lyra_text_secondary)
        else -> getColor(R.color.lyra_on_surface)
    }

    /**
     * Removing is a request, not an instant delete, and the dialog says so.
     *
     * The catalogue is a set of files in Git that only the trusted publisher can change, so the
     * app records the intent and the next run withdraws it. Anyone who already installed the theme
     * keeps their own local copy, which no amount of removal on this side can reach.
     */
    private fun confirmRemoval(record: CommunityThemeSubmissionRecord) {
        val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.community_theme_submissions_remove_title)
                .setMessage(getString(
                        R.string.community_theme_submissions_remove_message,
                        record.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.community_theme_submissions_remove_confirm) { _, _ ->
                    removeSubmission(record)
                }
                .create()
        dialog.setOnShowListener {
            dialog.applyLyraDialogStyling(accent = LyraAccent.contrastSafe(
                    LyraAccent.resolve(this),
                    getColor(R.color.lyra_surface),
                    minimumContrast = 4.5))
        }
        dialog.show()
    }

    private fun removeSubmission(record: CommunityThemeSubmissionRecord) {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val result = submissions.withdraw(record.id)) {
                CommunityThemeWithdrawalResult.Requested -> {
                    Toast.makeText(
                            this@CommunityThemeSubmissionsActivity,
                            R.string.community_theme_submissions_remove_requested,
                            Toast.LENGTH_LONG).show()
                    load()
                }
                CommunityThemeWithdrawalResult.SignedOut -> {
                    progress.visibility = View.GONE
                    showMessage(R.string.community_theme_submissions_signed_out)
                }
                is CommunityThemeWithdrawalResult.Failed -> {
                    Timber.w(result.error, "Could not withdraw a community theme submission")
                    progress.visibility = View.GONE
                    showMessage(R.string.community_theme_submissions_remove_error)
                }
            }
        }
    }

    private fun showMessage(stringRes: Int) {
        message.setText(stringRes)
        message.visibility = View.VISIBLE
    }
}
