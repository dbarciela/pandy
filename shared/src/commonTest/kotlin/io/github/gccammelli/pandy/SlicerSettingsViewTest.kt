package io.github.gccammelli.pandy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlicerSettingsViewTest {
    private fun number(key: String = "layer_height", unit: String = "mm", min: Double = 0.05, max: Double = 0.6, integer: Boolean = false) =
        SlicerSetting("Quality", key, "Layer height", if (integer) "integer" else "number", unit, min, max)

    private fun choice(vararg labels: String, key: String = "seam_position") =
        SlicerSetting("Quality", key, "Seam position", "choice", options = labels.map { SlicerChoice(it.lowercase(), it) })

    @Test fun fewShortOptionsAreSegmentedAndOthersUseAPicker() {
        assertEquals(ChoiceStyle.Segmented, choice("On", "Off").choiceStyle())
        assertEquals(ChoiceStyle.Segmented, choice("Nearest", "Aligned", "Back", "Random").choiceStyle())
        assertEquals(ChoiceStyle.Picker, choice("Only one").choiceStyle())
        assertEquals(ChoiceStyle.Picker, choice("A", "B", "C", "D", "E").choiceStyle())
        assertEquals(ChoiceStyle.Picker, choice("Normal (automatic)", "Tree").choiceStyle())
        assertEquals(ChoiceStyle.Picker, choice("Ten chars!!", "Ten chars!!", "Ten chars!!", "Ten chars!!").choiceStyle())
        assertEquals(ChoiceStyle.Picker, choice("Eleven chars", "Twelve chars", "Another long", "More length").choiceStyle())
    }

    @Test fun searchMatchesEveryWordInLabelGroupKeyAndOptions() {
        val infill = SlicerSetting("Strength", "sparse_infill_pattern", "Infill pattern", "choice", options = listOf(SlicerChoice("gyroid", "Gyroid")))
        assertTrue(infill.matches(""))
        assertTrue(infill.matches("   "))
        assertTrue(infill.matches("INFILL"))
        assertTrue(infill.matches("strength gyroid"))
        assertTrue(infill.matches("sparse"))
        assertFalse(infill.matches("infill brim"))
        assertFalse(number().matches("wall"))
    }

    @Test fun stepSizeFollowsTheUnit() {
        assertEquals(1.0, number("wall_loops", "", 1.0, 20.0, integer = true).stepSize())
        assertEquals(5.0, number("speed", "mm/s", 1.0, 500.0).stepSize())
        assertEquals(5.0, number("density", "%", 0.0, 100.0).stepSize())
        assertEquals(5.0, number("angle", "°", 0.0, 90.0).stepSize())
        assertEquals(0.05, number().stepSize())
        assertEquals(1.0, number("brim_width", "mm", 0.0, 30.0).stepSize())
        assertEquals(1.0, number("plain", "", 0.0, 3.0).stepSize())
        assertEquals(0.05, SlicerSetting("G", "k", "K", "number", "mm").stepSize()) // no range sent: the fine step
        assertEquals(0f..100f, SlicerSetting("G", "k", "K", "number").sliderRange())
        assertEquals(0.05f..0.6f, number().sliderRange())
    }

    @Test fun steppingStaysInRangeAndFormatsCleanly() {
        val layer = number()
        assertEquals("0.17", layer.stepped("0.12", 1))
        assertEquals("0.07", layer.stepped("0.12", -1))
        assertEquals("0.05", layer.stepped("0.05", -1))
        assertEquals("0.6", layer.stepped("0.6", 1))
        assertEquals("0.15", layer.stepped("0.1", 1))
        assertEquals("125", number("speed", "mm/s", 1.0, 500.0).stepped("120", 1))
        assertEquals("1", number("speed", "mm/s", 1.0, 500.0).stepped("1", -1))
        assertEquals("5", number("wall_loops", "", 1.0, 20.0, integer = true).stepped("4", 1))
        assertEquals("0.1", layer.stepped("not a number", 1)) // restarts from the minimum
        assertEquals("0", SlicerSetting("G", "k", "K", "integer").stepped("x", 1)) // no range: nothing to step into
        assertEquals("0.001", formatSettingNumber(0.0004999 + 0.0005))
        assertEquals("2", formatSettingNumber(2.0))
    }

    @Test fun steppingNeverOvershootsABoundWithMoreThanThreeDecimals() {
        val odd = number("odd", "mm", 0.05, 0.6006)
        assertEquals("0.6", odd.stepped("0.6", 1)) // 0.6006 is not a value the slicer accepts, so stay inside
        assertEquals("0.051", number("odd", "mm", 0.0504, 0.6).stepped("0.05", -1))
        assertEquals("0.6", odd.withinRange(0.6004))
    }

    @Test fun densityPresetsHideWhatTheSlicerWouldRefuse() {
        val full = number("sparse_infill_density", "%", 0.0, 100.0)
        assertEquals(listOf("10", "20", "35", "100"), full.densityPresets().map { it.second })
        assertEquals(listOf("10", "20", "35"), number("sparse_infill_density", "%", 0.0, 40.0).densityPresets().map { it.second })
    }

    @Test fun artIsOnlyDefinedForTheVisualChoices() {
        assertEquals("infill:gyroid", settingArt("sparse_infill_pattern", "gyroid"))
        assertEquals("brim:outer_only", settingArt("brim_type", "outer_only"))
        assertEquals("support:tree", settingArt("support_type", "tree(auto)"))
        assertEquals("support:normal", settingArt("support_type", "normal(manual)"))
        assertNull(settingArt("seam_position", "back"))
    }

    @Test fun changedGroupsStartOpenElseTheFirst() {
        val groups = listOf("Quality", "Strength", "Support")
        assertEquals(setOf("Quality"), initiallyOpenGroups(groups, emptyList()))
        assertEquals(setOf("Strength", "Support"), initiallyOpenGroups(groups, listOf("Support", "Strength", "Strength")))
        assertEquals(emptySet(), initiallyOpenGroups(emptyList(), emptyList()))
    }
}
