package io.github.gccammelli.pandy

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** How a choice setting is edited: a segmented control for a few short options, otherwise a picker sheet. */
internal enum class ChoiceStyle { Segmented, Picker }

internal fun SlicerSetting.choiceStyle(): ChoiceStyle =
    if (options.size in 2..4 && options.all { it.label.length <= 11 } && options.sumOf { it.label.length } <= 30) ChoiceStyle.Segmented else ChoiceStyle.Picker

/** Every word of [query] must appear in the label, group, key or an option label; blank matches everything. */
internal fun SlicerSetting.matches(query: String): Boolean {
    val words = query.lowercase().split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val text = (listOf(label, group, key.replace('_', ' ')) + options.map { it.label }).joinToString(" ").lowercase()
    return words.all { it in text }
}

internal fun SlicerSetting.stepSize(): Double = when {
    type == "integer" -> 1.0
    unit == "mm/s" || unit == "%" || unit == "°" -> 5.0
    unit == "mm" && (max ?: 0.0) <= 2.0 -> 0.05
    else -> 1.0
}

internal fun formatSettingNumber(n: Double): String {
    val rounded = round(n * 1000) / 1000
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

/** The value one step up (+1) or down (-1) from [current], kept inside the slicer's range. */
internal fun SlicerSetting.stepped(current: String, direction: Int): String {
    val from = current.toDoubleOrNull() ?: min ?: 0.0
    return withinRange((from + direction * stepSize()).coerceIn(min ?: from, max ?: from))
}

/** [n] to the three decimals the slicer accepts, rounded towards the inside of the range so a bound is never overshot. */
internal fun SlicerSetting.withinRange(n: Double): String {
    val nearest = round(n * 1000) / 1000
    val inside = when {
        max != null && nearest > max -> floor(n * 1000) / 1000
        min != null && nearest < min -> ceil(n * 1000) / 1000
        else -> nearest
    }
    return formatSettingNumber(inside)
}

private val DENSITY_PRESETS = listOf("Light" to "10", "Medium" to "20", "Strong" to "35", "Solid" to "100")

/** The density presets this slicer accepts; a narrower range hides the ones it would refuse. */
internal fun SlicerSetting.densityPresets(): List<Pair<String, String>> = DENSITY_PRESETS.filter { valid(it.second) }

/** Which small drawing represents a choice, or null when it only has text. */
internal fun settingArt(key: String, value: String): String? = when (key) {
    "sparse_infill_pattern" -> "infill:$value"
    "brim_type" -> "brim:$value"
    "support_type" -> "support:" + if (value.startsWith("tree")) "tree" else "normal"
    else -> null
}

/** Groups that start open: those with a change, or the first one when nothing is changed. */
internal fun initiallyOpenGroups(groups: List<String>, changed: Collection<String>): Set<String> =
    (groups.filter { it in changed }.ifEmpty { groups.take(1) }).toSet()
