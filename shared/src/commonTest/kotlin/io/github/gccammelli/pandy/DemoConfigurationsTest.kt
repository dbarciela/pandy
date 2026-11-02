package io.github.gccammelli.pandy

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DemoConfigurationsTest {
    @Test fun demoRegistriesCoverEveryModelCountAndType() = runTest {
        for (model in listOf("X1C", "X1", "X1E", "P1P", "P1S", "P2S", "A1", "A1 mini", "H2D", "H2S")) {
            for (count in 0..4) for (type in listOf("AMS", "AMS 2 Pro", "AMS Lite", "AMS HT")) {
                val info = FakeHomeAssistant(DemoConfig(model, count, type)).printers().single()
                assertEquals(model, info.model)
                assertEquals(count, info.ams.size)
                assertEquals(List(count) { type }, info.ams.map { it.model })
                assertEquals(List(count) { if (type == "AMS HT") 1 else 4 }, info.ams.map { it.trays.size })
            }
        }
    }

    @Test fun dualNozzleDemoShowsTwoLoadedUnits() = runTest {
        val demo = FakeHomeAssistant(DemoConfig("H2D", 2, "AMS 2 Pro"))
        val info = demo.printers().single()
        suspend fun active(unit: Int, slot: Int): String? {
            val entity = info.ams[unit].trays.getValue(slot)
            return json.parseToJsonElement(demo.get("/api/states/$entity")).jsonObject.o("attributes")?.s("active")
        }
        assertEquals("true", active(0, 2))
        assertEquals("true", active(1, 1))
        assertEquals("false", active(0, 1))
    }

    @Test fun dualNozzleDemoUpdatesTheRightNozzleWithTheActiveControl() = runTest {
        val demo = FakeHomeAssistant(DemoConfig("H2D"))
        demo.post("/api/services/number/set_value", buildJsonObject {
            put("entity_id", "number.x1c_demo_target_nozzle_temperature"); put("value", 250)
        }.toString())
        fun read(key: String) = "sensor.x1c_demo_$key"
        suspend fun value(key: String) = json.parseToJsonElement(demo.get("/api/states/${read(key)}")).jsonObject.s("state")
        assertEquals("250", value("right_target_nozzle_temp"))
        demo.tick()
        assertEquals(value("nozzle_temp"), value("right_nozzle_temp"))
        assertEquals("190", value("left_nozzle_temp"))
        demo.post("/api/services/button/press", buildJsonObject { put("entity_id", "button.x1c_demo_stop") }.toString())
        assertEquals("0", value("right_target_nozzle_temp"))
        assertEquals("0", value("left_target_nozzle_temp"))
    }

    @Test fun mixedH2dDemoCanShowFourAmsAndEightHt() = runTest {
        val info = FakeHomeAssistant(DemoConfig("H2D", 4, "AMS 2 Pro", 8)).printers().single()
        assertEquals(12, info.ams.size)
        assertEquals(List(4) { "AMS 2 Pro" } + List(8) { "AMS HT" }, info.ams.map { it.model })
        assertEquals((0..3).toList() + (128..135).toList(), info.ams.map { it.amsId() })
        assertEquals(List(4) { 4 } + List(8) { 1 }, info.ams.map { it.trays.size })
    }

    @Test fun rejectsUnsupportedDemoInputs() {
        assertFailsWith<IllegalArgumentException> { DemoConfig(amsCount = 5) }
        assertFailsWith<IllegalArgumentException> { DemoConfig(amsCount = 4, amsType = "AMS HT", htCount = 5) }
        assertFailsWith<IllegalArgumentException> { DemoConfig(model = "other") }
        assertFailsWith<IllegalArgumentException> { DemoConfig(amsType = "other") }
    }
}
