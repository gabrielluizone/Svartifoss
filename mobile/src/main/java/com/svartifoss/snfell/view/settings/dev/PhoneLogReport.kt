package com.svartifoss.snfell.view.settings.dev

import android.content.Context
import com.matejdro.wearutils.logging.FileLogger

/** Kept well under a dialog TextView's comfortable render size; older lines are still on disk and
 *  reachable through the existing "Send logs" email flow if the full history is ever needed. */
private const val MAX_REPORT_CHARS = 12_000

/**
 * Reads what Timber has already been writing to disk via the [FileLogger] tree planted at process
 * start (`WearMusicCenter.onCreate`), so this is the log an ordinary run already produced - not a
 * new capture mechanism. Files rotate by index (`log_0.log` .. `log_3.log`, ~30 KB each), so they
 * are ordered here by last-modified time rather than by index to reconstruct chronological order.
 * A `log_watch_*.log` file only exists after "Send logs" has fetched the watch's own log at least
 * once in this install; when present it is called out separately rather than interleaved, since
 * its timestamps come from the watch's clock, not the phone's.
 */
internal fun buildPhoneLogReport(context: Context): String {
    val logsFolder = FileLogger.getInstance(context).logsFolder
    val files = logsFolder?.listFiles()?.filter { it.name.endsWith(".log") } ?: emptyList()
    if (files.isEmpty()) {
        return "No log files found yet at ${logsFolder?.absolutePath}."
    }

    val phoneFiles = files.filter { !it.name.startsWith("log_watch_") }.sortedBy { it.lastModified() }
    val watchFiles = files.filter { it.name.startsWith("log_watch_") }.sortedBy { it.name }

    val phoneText = phoneFiles.joinToString("") { it.readText() }
    val phoneTail = phoneText.takeLast(MAX_REPORT_CHARS)

    return buildString {
        val truncationNote = if (phoneTail.length < phoneText.length) {
            ", showing the last ${phoneTail.length}"
        } else {
            ""
        }
        appendLine("Phone log (${phoneFiles.size} file(s), ${phoneText.length} chars total" +
                "$truncationNote):")
        appendLine("-".repeat(40))
        append(phoneTail.ifBlank { "(empty)" })
        appendLine()

        if (watchFiles.isNotEmpty()) {
            appendLine()
            appendLine("-".repeat(40))
            appendLine("Watch log from last \"Send logs\" (${watchFiles.size} file(s)):")
            appendLine("-".repeat(40))
            val watchText = watchFiles.joinToString("") { it.readText() }
            append(watchText.takeLast(MAX_REPORT_CHARS).ifBlank { "(empty)" })
        }
    }
}
