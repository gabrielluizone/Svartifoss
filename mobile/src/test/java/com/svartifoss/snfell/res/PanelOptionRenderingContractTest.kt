package com.svartifoss.snfell.res

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guards the two independent renderers behind the additive Panel catalog.
 *
 * The phone preview cannot reuse Wear's Android views, so accepting a stable preference token on
 * only one side fails silently: the picker works, but either the wrist or its preview falls back to
 * an unrelated appearance. These source-level checks are deliberately scoped to the owning
 * renderer so shared tokens such as `double`, `dial`, `soft`, and `chrome` cannot satisfy the
 * contract merely by appearing in another panel.
 */
class PanelOptionRenderingContractTest {

    private data class RendererContract(
            val valuesArray: String,
            val previewHas: (String) -> Boolean,
            val wearHas: (String) -> Boolean)

    @Test
    fun `every additive values array has a renderer contract`() {
        val declared = Regex("""<string-array[^>]*name="([^"]+_extra_values|wear_progress_layout_values)"""")
                .findAll(panelOptionsSource)
                .map { it.groupValues[1] }
                .toSet()

        assertEquals(
                "Add a scoped Wear/preview contract when adding another Panel option axis",
                contracts().map { it.valuesArray }.toSet(),
                declared)
    }

    @Test
    fun `every additive option reaches Wear and WatchPreview renderers`() {
        val failures = mutableListOf<String>()

        contracts().forEach { contract ->
            val values = arrayItems(contract.valuesArray)
            if (values.isEmpty()) failures += "${contract.valuesArray}: the values array is empty"
            values.forEach { value ->
                if (!contract.wearHas(value)) {
                    failures += "${contract.valuesArray}/$value: missing from the scoped Wear renderer"
                }
                if (!contract.previewHas(value)) {
                    failures += "${contract.valuesArray}/$value: missing from WatchPreviewView"
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                    "Additive Panel options without matching render paths:\n  " +
                            failures.joinToString("\n  "))
        }
    }

    private fun contracts(): List<RendererContract> {
        val preview = source(
                "mobile/src/main/java/com/svartifoss/snfell/view/watchface/WatchPreviewView.kt")
        val wearMain = source(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/MainActivity.kt")
        val volume = source(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/CircularVolumeBar.kt")
        val progress = source(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/CircularProgressSeekBar.kt")
        val queue = source(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/queue/QueueScreen.kt")
        val backdrop = source(
                "common/src/main/java/com/svartifoss/snfell/common/OverlayBackdrop.kt")

        val previewBackdrop = functionBody(preview, "drawConfiguredOverlayBackdrop")
        val wearBackdrop = functionBody(wearMain, "applyOverlayBackdrop")
        val previewVolume = functionBody(preview, "drawVolumeArc")
        val previewRing = functionBody(preview, "drawEdgeSeekRing")
        val previewReadout = functionBody(preview, "drawOverlayReadout")
        val previewSeekLayout = functionBody(preview, "drawSeekSurface")
        val previewQuickStyle = functionBody(preview, "quickSkin")
        val previewQuickLayout = functionBody(preview, "previewQuickSlotRect")
        val previewQueue = functionBody(preview, "queueSkin")
        val wearReadout = functionBody(wearMain, "applyPillReadoutStyle")
        val wearSeekLayout = functionBody(wearMain, "applySeekPanelLayout")
        val wearQuickStyle = functionBody(wearMain, "inactiveQuickButtonBackground")
        val wearQuickLayout = functionBody(wearMain, "applyQuickPanelLayout")

        fun enumContract(
                valuesArray: String,
                enumType: String,
                wearSource: String,
                previewScope: String,
                constantFor: (String) -> String = ::enumConstant
        ) = RendererContract(
                valuesArray = valuesArray,
                previewHas = { value -> hasLiteral(previewScope, value) },
                wearHas = { value ->
                    val constant = constantFor(value)
                    mapsToEnum(wearSource, value, constant) &&
                            wearSource.contains("$enumType.$constant")
                })

        fun literalContract(valuesArray: String, previewScope: String, wearScope: String) =
                RendererContract(
                        valuesArray = valuesArray,
                        previewHas = { value -> hasLiteral(previewScope, value) },
                        wearHas = { value -> hasLiteral(wearScope, value) })

        return listOf(
                RendererContract(
                        valuesArray = "wear_overlay_backdrop_extra_values",
                        previewHas = { value ->
                            previewBackdrop.contains(
                                    "OverlayBackdrop.${backdropConstant(value)}")
                        },
                        wearHas = { value ->
                            val constant = backdropConstant(value)
                            mapsToEnum(backdrop, value, constant) &&
                                    wearBackdrop.contains("OverlayBackdrop.$constant")
                        }),
                enumContract(
                        "wear_volume_style_extra_values",
                        "VolumeStyle",
                        volume,
                        previewVolume),
                enumContract(
                        "wear_volume_layout_extra_values",
                        "VolumeLayout",
                        volume,
                        previewVolume),
                enumContract(
                        "wear_progress_style_extra_values",
                        "RingStyle",
                        progress,
                        previewRing),
                RendererContract(
                        valuesArray = "wear_progress_layout_values",
                        previewHas = { value ->
                            (hasLiteral(previewRing, value) ||
                                    value == "edge" &&
                                    defaultProgressLayoutIsRendered(previewRing)) &&
                                    // `double` is also a RingStyle. Tie it explicitly to the layout
                                    // so that the style branch cannot hide a missing layout branch.
                                    (value != "double" || Regex(
                                            """progressLayout\s*==\s*"double"""")
                                            .containsMatchIn(previewRing))
                        },
                        wearHas = { value ->
                            val constant = enumConstant(value)
                            (mapsToEnum(progress, value, constant) ||
                                    value == "edge" && Regex("""else\s*->\s*EDGE\b""")
                                            .containsMatchIn(progress)) &&
                                    Regex("""\b${Regex.escape(constant)}\s*\(""")
                                            .containsMatchIn(progress) &&
                                    progressRingGeometryIsConsumed(progress)
                        }),
                literalContract(
                        "wear_seek_style_extra_values",
                        previewReadout,
                        wearReadout),
                literalContract(
                        "wear_seek_layout_extra_values",
                        previewSeekLayout,
                        wearSeekLayout),
                literalContract(
                        "wear_quick_panel_style_extra_values",
                        previewQuickStyle,
                        wearQuickStyle),
                literalContract(
                        "wear_quick_panel_layout_extra_values",
                        previewQuickLayout,
                        wearQuickLayout),
                enumContract(
                        "wear_queue_style_extra_values",
                        "QueueStyle",
                        queue,
                        previewQueue)
        )
    }

    private fun progressRingGeometryIsConsumed(source: String): Boolean =
            listOf(
                    "ringLayout.startAngle",
                    "ringLayout.sweepAngle",
                    "ringLayout.radiusInsetDp",
                    "ringLayout.strokeScale",
                    "ringLayout.drawsSecondRing",
                    "ringLayout.secondRingInsetDp")
                    .all(source::contains)

    /** `edge` is the backwards-compatible fallback, so it intentionally has no quoted branch. */
    private fun defaultProgressLayoutIsRendered(source: String): Boolean =
            source.contains("when (progressLayout)") &&
                    Regex("""else\s*->\s*-90f""").containsMatchIn(source) &&
                    Regex("""else\s*->\s*360f""").containsMatchIn(source)

    private fun backdropConstant(value: String): String = when (value) {
        "secondary" -> "SOLID_SECONDARY"
        "tertiary" -> "SOLID_TERTIARY"
        else -> enumConstant(value)
    }

    private fun enumConstant(value: String): String = value.uppercase(Locale.ROOT)

    private fun mapsToEnum(source: String, value: String, constant: String): Boolean =
            Regex(
                    """"${Regex.escape(value)}"\s*->\s*${Regex.escape(constant)}\b""")
                    .containsMatchIn(source)

    private fun hasLiteral(source: String, value: String): Boolean =
            source.contains("\"$value\"")

    private val panelOptionsSource: String by lazy {
        source("mobile/src/main/res/values/panel_options.xml")
    }

    private fun arrayItems(name: String): List<String> {
        val body = Regex(
                """<string-array[^>]*name="${Regex.escape(name)}"[^>]*>(.*?)</string-array>""",
                RegexOption.DOT_MATCHES_ALL)
                .find(panelOptionsSource)
                ?.groupValues?.get(1)
                ?: throw AssertionError("$name missing from panel_options.xml")
        return Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(body)
                .map { it.groupValues[1].trim() }
                .toList()
    }

    private fun source(relative: String): String = repoFile(relative).readText()

    /** Gradle can execute JVM tests from the module or repository directory. */
    private fun repoFile(relative: String): File =
            listOf(File("../$relative"), File(relative))
                    .firstOrNull { it.exists() }
                    ?: throw AssertionError(
                            "Could not locate $relative from ${File(".").absolutePath}")

    private fun functionBody(source: String, function: String): String {
        val signature = Regex("""fun\s+$function\s*\(""").find(source)
                ?: throw AssertionError("$function not found")
        val open = source.indexOf('{', signature.range.last)
        if (open < 0) throw AssertionError("$function has no block body")
        return balancedBlock(source, open)
    }

    private fun balancedBlock(source: String, open: Int): String {
        var depth = 0
        var index = open
        while (index < source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, index + 1)
                }
            }
            index++
        }
        throw AssertionError("Unbalanced braces from offset $open")
    }
}
