package io.github.gccammelli.pandy

import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrinterAmsReportsTest {
    private fun entities(model: String, report: String) = lanEntities(
        LanConfig("example.invalid", "DEMO", "", model), json.parseToJsonElement(report).jsonObject, true
    )

    @Test fun legacyReportsCoverModelsAndZeroToFourUnits() {
        for (model in listOf("X1C", "X1", "X1E", "P1P", "P1S", "P2S", "A1", "A1 mini", "H2D", "H2S")) {
            for (count in 0..4) {
                val units = (0 until count).joinToString(",") { """{"id":"$it","tray":[{"id":"0","tray_type":"PLA"}]}""" }
                val rows = entities(model, """{"print":{"nozzle_temper":215,"ams":{"tray_now":"4","ams":[$units]}}}""")
                assertEquals(count * 4, rows.count { it.first.key == "tray" }, "$model / $count")
                assertEquals("215.0", rows.first { it.first.key == "nozzle_temp" }.second.state)
                assertEquals(if (count > 1 && !model.startsWith("H2")) 1 else 0, rows.count { it.first.key == "tray" && it.second.attrs.s("active") == "true" })
            }
        }
    }

    @Test fun unknownExtruderDataDoesNotChangeLegacyPrintersOrGuessH2Units() {
        val print = """{"print":{"ams":{"tray_now":"1","ams":[{"id":0},{"id":1}]},"device":{"extruder":{"info":[{"id":0,"snow":65535}]}}}}"""
        for (model in listOf("X1C", "P2S", "A1")) {
            val legacy = entities(model, print).filter { it.first.key == "tray" && it.second.attrs.s("active") == "true" }
            assertEquals(listOf("ams0"), legacy.map { it.first.device }, model)
        }
        val h2Unknown = entities("H2D", print).filter { it.first.key == "tray" && it.second.attrs.s("active") == "true" }
        assertTrue(h2Unknown.isEmpty())
        val h2Missing = print.replace(",\"device\":{\"extruder\":{\"info\":[{\"id\":0,\"snow\":65535}]}}", "")
        val missing = entities("H2S", h2Missing).filter { it.first.key == "tray" && it.second.attrs.s("active") == "true" }
        assertTrue(missing.isEmpty())
        val single = """{"print":{"ams":{"tray_now":"1","ams":[{"id":0}]}}}"""
        val h2Single = entities("H2D", single).filter { it.first.key == "tray" && it.second.attrs.s("active") == "true" }
        assertEquals(listOf("ams0"), h2Single.map { it.first.device })
    }

    @Test fun invalidH2SnowDoesNotFallBackToLegacyTrayOnSingleAms() {
        for (snow in listOf("70000", "-1", "\"abc\"")) {
            val report = """{"print":{"ams":{"tray_now":"1","ams":[{"id":0}]},"device":{"extruder":{"info":[{"id":0,"snow":$snow}]}}}}"""
            val active = entities("H2D", report).filter { it.first.key == "tray" && it.second.attrs.s("active") == "true" }
            assertTrue(active.isEmpty(), "snow=$snow")
        }
    }

    @Test fun h2ExtrudersUseUnitAndSlotInsteadOfLegacyTray() {
        val rows = entities("H2D", """{"print":{"ams":{"tray_now":"1","ams":[{"id":0},{"id":1},{"id":128}]},"device":{"extruder":{"info":[{"id":0,"temp":14418140,"snow":257},{"id":1,"temp":13107390,"snow":32768}]}}}}""")
        assertEquals("220", rows.first { it.first.key == "right_nozzle_temp" }.second.state)
        assertEquals("220", rows.first { it.first.key == "right_target_nozzle_temp" }.second.state)
        assertEquals("190", rows.first { it.first.key == "left_nozzle_temp" }.second.state)
        assertEquals("200", rows.first { it.first.key == "left_target_nozzle_temp" }.second.state)
        val active = rows.filter { it.first.key == "tray" && it.second.attrs.s("active") == "true" }
        assertEquals(setOf("ams1" to "AMS_2_tray_2", "ams128" to "AMS_3_tray_1"), active.map { it.first.device to it.first.uid }.toSet())
        assertFalse(rows.first { it.first.uid == "AMS_1_tray_2" }.second.attrs.s("active") == "true")
    }

    @Test fun h2dCanRepresentFourMultiSlotUnitsAndEightHtUnits() {
        val units = (listOf(0, 1, 2, 3) + (128..135)).joinToString(",") { """{"id":$it}""" }
        val report = """{"print":{"ams":{"ams":[$units]},"device":{"extruder":{"info":[{"id":0,"snow":769},{"id":1,"snow":34560}]}}}}"""
        val rows = entities("H2D", report).filter { it.first.key == "tray" }
        assertEquals(24, rows.size)
        assertEquals(setOf("ams3", "ams135"), rows.filter { it.second.attrs.s("active") == "true" }.map { it.first.device }.toSet())
    }

    @Test fun versionModulesIdentifyAmsModels() {
        val rows = entities("H2D", """{"info":{"module":[{"name":"ams/0"},{"name":"ams_f1/1"},{"name":"n3f/2"},{"name":"n3s/128"}]},"print":{"ams":{"ams":[{"id":0},{"id":1},{"id":2},{"id":128}]}}}""")
        assertEquals(listOf("AMS", "AMS Lite", "AMS 2 Pro", "AMS HT"), rows.filter { it.first.key == "tray" }.groupBy { it.first.device }.values.map { it.first().first.deviceModel })
        assertEquals(1, rows.count { it.first.device == "ams128" && it.first.key == "tray" })
        assertTrue(rows.none { it.first.key == "left_nozzle_temp" })
    }
}

