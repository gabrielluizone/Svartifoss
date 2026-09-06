package com.svartifoss.snfell.view.watchface.theme

import android.content.Context
import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import org.json.JSONArray
import org.json.JSONObject

/**
 * The semantic half of a public community-theme profile.
 *
 * The renderer's preference readers intentionally tolerate old values so an existing local
 * backup can survive an app upgrade. Public profiles are different: their values participate in
 * duplicate and originality fingerprints, so accepting a renderer alias here would let two
 * equivalent appearances appear distinct. This loader therefore fails closed unless the shipped
 * data-only contract covers the exact current scoped-preference schema.
 */
internal class CommunityThemeConstraints private constructor(
        private val rules: Map<String, Rule>,
        private val legacyReadOnlyValues: Map<String, Set<String>>,
        private val defaultOriginalityFaces: Set<String>,
        private val originalityFaceOverrides: Map<String, Set<String>>,
        private val originalityRequirements: Map<String, List<OriginalityRequirement>>
) {

    /**
     * Checks both the typed public-profile value and its canonical vocabulary/range.
     *
     * [allowLegacyReadOnly] is deliberately not a general compatibility switch. The sole caller
     * that enables it first proves that the profile is one of the immutable Phase-1 compatibility
     * seeds. New submissions, direct public parsing and the digest/originality path always use the
     * canonical branch.
     */
    fun accepts(
            key: String,
            value: WatchThemeValue,
            allowLegacyReadOnly: Boolean = false
    ): Boolean {
        val rule = rules[key] ?: return false
        return rule.acceptsCanonical(value) ||
                (allowLegacyReadOnly && value is WatchThemeValue.Text &&
                        value.value in legacyReadOnlyValues[key].orEmpty())
    }

    /** True only when this changed key can materially affect the complete face snapshot. */
    fun isOriginalityApplicable(
            key: String,
            completeSettings: Map<String, WatchThemeValue>,
            baseFace: String
    ): Boolean {
        if (key !in rules) return false
        val applicableFaces = originalityFaceOverrides[key] ?: defaultOriginalityFaces
        if (baseFace !in applicableFaces) return false
        return originalityRequirements[key].orEmpty().all { requirement ->
            completeSettings[requirement.setting] in requirement.equalsAny
        }
    }

    private data class OriginalityRequirement(
            val setting: String,
            val equalsAny: Set<WatchThemeValue>
    )

    private sealed interface Rule {
        fun acceptsCanonical(value: WatchThemeValue): Boolean
    }

    private object BooleanRule : Rule {
        override fun acceptsCanonical(value: WatchThemeValue): Boolean = value is WatchThemeValue.Flag
    }

    private data class IntRule(val minimum: Int, val maximum: Int) : Rule {
        override fun acceptsCanonical(value: WatchThemeValue): Boolean =
                value is WatchThemeValue.Number && value.value in minimum..maximum
    }

    private data class EnumRule(val values: Set<String>) : Rule {
        override fun acceptsCanonical(value: WatchThemeValue): Boolean =
                value is WatchThemeValue.Text && value.value in values
    }

    private data class ColorRule(val expression: Regex) : Rule {
        override fun acceptsCanonical(value: WatchThemeValue): Boolean =
                value is WatchThemeValue.Text && expression.matches(value.value)
    }

    /**
     * A structured setting: the ordered background stack, whose grammar the asset declares.
     *
     * The other three rule kinds each describe one value. This one describes a *sequence* of
     * enumerated values, which is a wider public input boundary than anything before it - so the
     * contract states the whole grammar (version marker, depth, encoded length, the opacity range
     * and the vocabulary each kind may draw from) and the parse below admits nothing that is not
     * in it. A stack is still enumerated data: there is no expression to evaluate, no colour that
     * is not six hex digits and no style that is not one of the names the asset lists.
     *
     * Deliberately not a regex. The grammar is positional and each field's vocabulary depends on
     * the field before it, which a pattern can only express by inlining every combination - and a
     * pattern nobody can read is a poor thing to make a trust boundary out of.
     */
    private data class LayerStackRule(
            val version: String,
            val maxLayers: Int,
            val maxLength: Int,
            val minOpacity: Int,
            val maxOpacity: Int,
            val colors: Set<String>,
            val kinds: Map<String, Set<String>>
    ) : Rule {
        override fun acceptsCanonical(value: WatchThemeValue): Boolean {
            val text = (value as? WatchThemeValue.Text)?.value ?: return false
            // Empty is the shipped default: nobody has composed a stack, and the three legacy
            // settings in this same profile are what the recipient renders.
            if (text.isEmpty()) return true
            if (text.length > maxLength) return false
            val parts = text.split('|')
            if (parts.first() != version || parts.size - 1 > maxLayers) return false
            return parts.drop(1).all(::acceptsLayer)
        }

        private fun acceptsLayer(encoded: String): Boolean {
            val fields = encoded.split('.')
            if (fields.size !in 2..5) return false
            val styles = kinds[fields[0]] ?: return false
            if (fields[1] !in styles) return false

            val opacity = fields.getOrNull(2)
            if (opacity != null) {
                val parsed = opacity.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
                        ?.toIntOrNull() ?: return false
                // Compared back against its own decimal so "060" cannot become a second spelling
                // of one composition - the settings digest is taken over this exact string.
                if (parsed.toString() != opacity || parsed !in minOpacity..maxOpacity) return false
            }

            val color = fields.getOrNull(3)
            if (color != null && color !in colors) return false
            val custom = fields.getOrNull(4)
            return if (color == "custom") {
                custom != null && HEX_RGB_EXPRESSION.matches(custom)
            } else {
                custom == null
            }
        }
    }

    companion object {
        private const val ASSET_NAME = "community-theme-constraints.json"
        private const val SCHEMA_VERSION = 2
        private const val HEX_RGB_RULE_NAME = "empty-or-uppercase-hex-rgb"
        private const val HEX_RGB_PATTERN = "^$|^#[0-9A-F]{6}$"
        private val VALUE_SET_NAME = Regex("^[a-z][A-Za-z0-9]*$")
        private const val LAYER_RULE_NAME = "background-layer-stack-v1"
        private val HEX_RGB_EXPRESSION = Regex("^#[0-9A-F]{6}$")

        fun load(context: Context): CommunityThemeConstraints? = try {
            context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
                fromJson(JSONObject(reader.readText()))
            }
        } catch (_: Exception) {
            null
        }

        /** Visible to JVM tests so the checked-in asset is validated without an Android Context. */
        internal fun fromJson(root: JSONObject): CommunityThemeConstraints? {
            return try {
                if (!root.hasExactlyKeys(ROOT_KEYS) || root.requiredInt("schemaVersion") != SCHEMA_VERSION) {
                    return null
                }
                val valueSetsJson = root.optJSONObject("valueSets") ?: return null
                val colorRulesJson = root.optJSONObject("colorRules") ?: return null
                val layerRulesJson = root.optJSONObject("layerRules") ?: return null
                val applicabilityJson = root.optJSONObject("originalityApplicableFaces") ?: return null
                val requirementsJson = root.optJSONObject("originalityRequires") ?: return null
                val legacyValuesJson = root.optJSONObject("legacyReadOnlyValues") ?: return null
                val settingsJson = root.optJSONObject("settings") ?: return null

                val valueSets = parseValueSets(valueSetsJson) ?: return null
                val colorRules = parseColorRules(colorRulesJson) ?: return null
                val layerRules = parseLayerRules(layerRulesJson, valueSets) ?: return null
                val rules = parseSettings(settingsJson, valueSets, colorRules, layerRules)
                        ?: return null
                val applicability = parseOriginalityApplicability(applicabilityJson, rules) ?: return null
                val requirements = parseOriginalityRequirements(requirementsJson, rules) ?: return null
                val legacyValues = parseLegacyValues(legacyValuesJson, rules) ?: return null
                CommunityThemeConstraints(
                        rules = rules,
                        legacyReadOnlyValues = legacyValues,
                        defaultOriginalityFaces = applicability.defaultFaces,
                        originalityFaceOverrides = applicability.overrides,
                        originalityRequirements = requirements)
            } catch (_: Exception) {
                null
            }
        }

        private fun parseValueSets(json: JSONObject): Map<String, Set<String>>? {
            val values = linkedMapOf<String, Set<String>>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                if (!VALUE_SET_NAME.matches(name)) return null
                val list = json.optJSONArray(name) ?: return null
                val parsed = parseUniqueTextArray(list) ?: return null
                values[name] = parsed
            }
            return values.takeIf { it.isNotEmpty() }
        }

        private fun parseColorRules(json: JSONObject): Map<String, Regex>? {
            val rules = linkedMapOf<String, Regex>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val value = json.optJSONObject(name) ?: return null
                if (!value.hasExactlyKeys(setOf("pattern"))) return null
                val pattern = value.requiredString("pattern") ?: return null
                // Keep color grammar deliberately narrow. A future grammar gets a schema bump and
                // explicit Android support instead of silently widening the public input boundary.
                if (name != HEX_RGB_RULE_NAME || pattern != HEX_RGB_PATTERN) return null
                rules[name] = Regex(pattern)
            }
            return rules.takeIf { it.keys == setOf(HEX_RGB_RULE_NAME) }
        }

        /**
         * The layer grammar, pinned to the one name and shape this build implements.
         *
         * Same posture as [parseColorRules] and for the same reason: widening a public input
         * boundary is a decision, so a future grammar arrives as a schema bump with explicit
         * support here, never by an asset quietly describing something new.
         */
        private fun parseLayerRules(
                json: JSONObject,
                valueSets: Map<String, Set<String>>
        ): Map<String, LayerStackRule>? {
            if (!json.hasExactlyKeys(setOf(LAYER_RULE_NAME))) return null
            val rule = json.optJSONObject(LAYER_RULE_NAME) ?: return null
            if (!rule.hasExactlyKeys(
                            setOf("version", "maxLayers", "maxLength", "opacity", "colors", "kinds"))) {
                return null
            }
            val version = rule.requiredString("version") ?: return null
            val maxLayers = rule.requiredInt("maxLayers")?.takeIf { it in 1..32 } ?: return null
            val maxLength = rule.requiredInt("maxLength")?.takeIf { it in 1..1024 } ?: return null
            val opacity = rule.optJSONObject("opacity") ?: return null
            if (!opacity.hasExactlyKeys(setOf("min", "max"))) return null
            val minOpacity = opacity.requiredInt("min") ?: return null
            val maxOpacity = opacity.requiredInt("max") ?: return null
            if (minOpacity > maxOpacity) return null
            val colors = parseUniqueTextArray(rule.optJSONArray("colors") ?: return null)
                    ?: return null
            val kindsJson = rule.optJSONObject("kinds") ?: return null
            val kinds = linkedMapOf<String, Set<String>>()
            val kindKeys = kindsJson.keys()
            while (kindKeys.hasNext()) {
                val token = kindKeys.next()
                // One character, so a layer's kind can never be mistaken for part of its style.
                if (token.length != 1) return null
                val setName = kindsJson.requiredString(token) ?: return null
                kinds[token] = valueSets[setName] ?: return null
            }
            if (kinds.isEmpty()) return null
            return mapOf(LAYER_RULE_NAME to LayerStackRule(
                    version = version,
                    maxLayers = maxLayers,
                    maxLength = maxLength,
                    minOpacity = minOpacity,
                    maxOpacity = maxOpacity,
                    colors = colors,
                    kinds = kinds))
        }

        private fun parseSettings(
                json: JSONObject,
                valueSets: Map<String, Set<String>>,
                colorRules: Map<String, Regex>,
                layerRules: Map<String, LayerStackRule>
        ): Map<String, Rule>? {
            val expected = FaceScopedPreferences.SCOPED_DEFINITIONS.associate { definition ->
                definition.key to typeFor(definition.defaultValue)
            }
            if (!json.hasExactlyKeys(expected.keys)) return null

            val rules = linkedMapOf<String, Rule>()
            expected.forEach { (key, type) ->
                val setting = json.optJSONObject(key) ?: return null
                val declaredType = setting.requiredString("type") ?: return null
                if (declaredType != type) return null
                val rule = when (type) {
                    "boolean" -> {
                        if (!setting.hasExactlyKeys(setOf("type"))) return null
                        BooleanRule
                    }
                    "int" -> {
                        if (!setting.hasExactlyKeys(setOf("type", "min", "max"))) return null
                        val minimum = setting.requiredInt("min") ?: return null
                        val maximum = setting.requiredInt("max") ?: return null
                        if (minimum > maximum) return null
                        IntRule(minimum, maximum)
                    }
                    "string" -> {
                        val valueSet = setting.requiredString("valueSet")
                        val colorRule = setting.requiredString("colorRule")
                        val layerRule = setting.requiredString("layerRule")
                        val declared = listOfNotNull(valueSet, colorRule, layerRule)
                        if (declared.size != 1) return null
                        when {
                            valueSet != null &&
                                    setting.hasExactlyKeys(setOf("type", "valueSet")) ->
                                EnumRule(valueSets[valueSet] ?: return null)
                            colorRule != null &&
                                    setting.hasExactlyKeys(setOf("type", "colorRule")) ->
                                ColorRule(colorRules[colorRule] ?: return null)
                            layerRule != null &&
                                    setting.hasExactlyKeys(setOf("type", "layerRule")) ->
                                layerRules[layerRule] ?: return null
                            else -> return null
                        }
                    }
                    else -> return null
                }
                rules[key] = rule
            }
            return rules
        }

        private fun parseOriginalityApplicability(
                json: JSONObject,
                rules: Map<String, Rule>
        ): OriginalityApplicability? {
            if (!json.hasExactlyKeys(setOf("default", "overrides"))) return null
            val defaultFaces = parseFaceSet(json.optJSONArray("default") ?: return null)
                    ?: return null
            val overridesJson = json.optJSONObject("overrides") ?: return null
            val overrides = linkedMapOf<String, Set<String>>()
            val keys = overridesJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key !in rules) return null
                val faces = parseFaceSet(
                        overridesJson.optJSONArray(key) ?: return null,
                        allowEmpty = true) ?: return null
                overrides[key] = faces
            }
            return OriginalityApplicability(defaultFaces, overrides)
        }

        private fun parseOriginalityRequirements(
                json: JSONObject,
                rules: Map<String, Rule>
        ): Map<String, List<OriginalityRequirement>>? {
            val requirements = linkedMapOf<String, List<OriginalityRequirement>>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val changedKey = keys.next()
                if (changedKey !in rules) return null
                val rawConditions = json.optJSONArray(changedKey) ?: return null
                if (rawConditions.length() == 0) return null
                val parsed = ArrayList<OriginalityRequirement>(rawConditions.length())
                for (index in 0 until rawConditions.length()) {
                    val condition = rawConditions.optJSONObject(index) ?: return null
                    if (!condition.hasExactlyKeys(setOf("setting", "equalsAny"))) return null
                    val setting = condition.requiredString("setting") ?: return null
                    val dependentRule = rules[setting] ?: return null
                    val rawValues = condition.optJSONArray("equalsAny") ?: return null
                    if (rawValues.length() == 0) return null
                    val values = linkedSetOf<WatchThemeValue>()
                    for (valueIndex in 0 until rawValues.length()) {
                        val value = typedCanonicalValue(dependentRule, rawValues.opt(valueIndex))
                                ?: return null
                        if (!values.add(value)) return null
                    }
                    parsed += OriginalityRequirement(setting, values)
                }
                requirements[changedKey] = parsed
            }
            return requirements
        }

        private fun parseFaceSet(array: JSONArray, allowEmpty: Boolean = false): Set<String>? {
            if (!allowEmpty && array.length() == 0) return null
            val faces = linkedSetOf<String>()
            for (index in 0 until array.length()) {
                val face = array.opt(index) as? String ?: return null
                if (face !in PUBLIC_BASE_FACES || !faces.add(face)) return null
            }
            return faces
        }

        private fun typedCanonicalValue(rule: Rule, rawValue: Any?): WatchThemeValue? {
            val value = when (rule) {
                BooleanRule -> (rawValue as? Boolean)?.let(WatchThemeValue::Flag)
                is IntRule -> (rawValue as? Int)?.let(WatchThemeValue::Number)
                is EnumRule,
                is ColorRule,
                is LayerStackRule -> (rawValue as? String)?.let(WatchThemeValue::Text)
            } ?: return null
            return value.takeIf(rule::acceptsCanonical)
        }

        private fun parseLegacyValues(
                json: JSONObject,
                rules: Map<String, Rule>
        ): Map<String, Set<String>>? {
            val values = linkedMapOf<String, Set<String>>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val rule = rules[key] as? EnumRule ?: return null
                val parsed = parseUniqueTextArray(json.optJSONArray(key) ?: return null) ?: return null
                if (parsed.any { it in rule.values }) return null
                values[key] = parsed
            }
            return values
        }

        private fun parseUniqueTextArray(array: JSONArray): Set<String>? {
            if (array.length() == 0) return null
            val values = linkedSetOf<String>()
            for (index in 0 until array.length()) {
                val value = array.opt(index) as? String ?: return null
                if (value.isEmpty() || value.length > MAX_PUBLIC_SETTING_TEXT_LENGTH || !values.add(value)) {
                    return null
                }
            }
            return values
        }

        private fun typeFor(defaultValue: Any): String = when (defaultValue) {
            is String -> "string"
            is Boolean -> "boolean"
            is Int -> "int"
            else -> error("Unsupported public-theme preference type")
        }

        private fun JSONObject.requiredString(key: String): String? = opt(key) as? String

        /** JSONObject's optInt coerces strings/doubles; the public contract must not. */
        private fun JSONObject.requiredInt(key: String): Int? = opt(key) as? Int

        private fun JSONObject.hasExactlyKeys(expected: Set<String>): Boolean {
            if (length() != expected.size) return false
            val keys = keys()
            while (keys.hasNext()) {
                if (keys.next() !in expected) return false
            }
            return true
        }

        private val ROOT_KEYS = setOf(
                "schemaVersion",
                "colorRules",
                "layerRules",
                "valueSets",
                "originalityApplicableFaces",
                "originalityRequires",
                "legacyReadOnlyValues",
                "settings")

        private val PUBLIC_BASE_FACES: Set<String> =
                ThemeAppearance.ALLOWED_BASE_FACES - ArchivedFaces.COMMUNITY_GALLERY_EXCLUDED

        private data class OriginalityApplicability(
                val defaultFaces: Set<String>,
                val overrides: Map<String, Set<String>>
        )
    }
}
