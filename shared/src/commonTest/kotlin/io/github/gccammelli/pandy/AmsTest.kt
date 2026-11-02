package io.github.gccammelli.pandy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class AmsTest {
    private fun mappingPrinter(): Printer {
        val ams=AmsInfo("ams",1,"AMS",emptyMap(),mapOf(1 to "sensor.white",2 to "sensor.green"))
        val info=PrinterInfo("printer","serial","Printer","X1C",null,emptyMap(),listOf(ams),emptyList())
        return Printer(info,listOf("white" to "#FFFFFF","green" to "#00FF00").associate { (name,color) ->
            "sensor.$name" to Entity("sensor.$name","PLA",json.parseToJsonElement("""{"type":"PLA","color":"$color"}""").jsonObject)
        })
    }

    @Test fun slicerMappingPreservesUnusedSlotsAndRefusesUnmatchedFilaments() {
        val p=mappingPrinter()
        assertEquals(listOf(0,-1,1),amsMappingFor(p,listOf(mapOf("id" to "1","type" to "PLA","color" to "#FFFFFF"),
            mapOf("id" to "3","type" to "pla","color" to "#00FF00"))))
        assertEquals(listOf(0),amsMappingFor(p,listOf(mapOf("id" to "bad","type" to "PLA","color" to "#FFFFFF"))))
        assertEquals(listOf(0),amsMappingFor(p,listOf(mapOf("type" to "PLA","color" to "#FFFFFF"))))
        assertNull(amsMappingFor(p,emptyList()))
        assertNull(amsMappingFor(p,listOf(mapOf("type" to "PETG"))))
        val bare=Printer(p.info.copy(ams=emptyList()),emptyMap())
        assertNull(amsMappingFor(bare,listOf(mapOf("type" to "PLA"))))
    }

    @Test
    fun mapsStandardAndAmsHtTrayIds() {
        val standard = AmsInfo("standard", 2, "AMS", emptyMap(), emptyMap())
        assertEquals(4, standard.trayId(0))
        assertEquals(7, standard.trayId(3))
        assertEquals(0, standard.slotForTrayId(4))
        assertNull(standard.slotForTrayId(8))

        val ht = AmsInfo("ht", 1, "AMS HT", emptyMap(), emptyMap())
        assertEquals(128, ht.trayId(0))
        assertEquals(0, ht.slotForTrayId(128))
        assertNull(ht.slotForTrayId(129))
        assertEquals(128, AmsInfo("ht2", 2, "AMS HT", emptyMap(), emptyMap()).trayId(0))
        assertEquals(129, AmsInfo("ht2", 2, "AMS HT", emptyMap(), emptyMap(), protocolId = 129).trayId(0))
    }

    @Test
    fun homeAssistantHtIdDoesNotCountStandardAms() {
        val serial = "TESTSERIAL"
        val devices = JsonArray(listOf(
            json.parseToJsonElement("""{"id":"printer","model":"X1C","name":"X1C","via_device_id":null,"identifiers":[["bambu_lab","$serial"]]}""").jsonObject,
            json.parseToJsonElement("""{"id":"standard","model":"AMS","name":"AMS","via_device_id":"printer","identifiers":[["bambu_lab","${serial}_ams0"]]}""").jsonObject,
            json.parseToJsonElement("""{"id":"ht","model":"AMS HT","name":"AMS HT","via_device_id":"printer","identifiers":[["bambu_lab","${serial}_ams128"]]}""").jsonObject,
        ))
        val entities = JsonArray(listOf(
            json.parseToJsonElement("""{"entity_id":"sensor.standard_tray_1","device_id":"standard","platform":"bambu_lab","translation_key":"tray","unique_id":"${serial}_tray_1"}""").jsonObject,
            json.parseToJsonElement("""{"entity_id":"sensor.ht_tray_1","device_id":"ht","platform":"bambu_lab","translation_key":"tray","unique_id":"${serial}_tray_1"}""").jsonObject,
        ))
        val ams = printersFrom(devices, entities).single().ams
        assertEquals(listOf(0, 128), ams.map { it.amsId() })
    }

    @Test
    fun ams2ProModelIdentificationAndBadges() {
        val ams2ProExplicit = AmsInfo("ams_pro", 1, "AMS 2 Pro", emptyMap(), emptyMap())
        assertEquals(true, ams2ProExplicit.isAms2Pro())
        assertEquals(true, ams2ProExplicit.supportsDrying())
        assertEquals("AMS A", ams2ProExplicit.displayName())
        assertEquals("AMS 2 Pro", ams2ProExplicit.modelBadge())

        val ams2ProHw = AmsInfo("ams_hw", 2, "N3F05", emptyMap(), emptyMap())
        assertEquals(true, ams2ProHw.isAms2Pro())
        assertEquals(true, ams2ProHw.supportsDrying())
        assertEquals("AMS B", ams2ProHw.displayName())
        assertEquals("AMS 2 Pro", ams2ProHw.modelBadge())

        val standard = AmsInfo("ams_std", 1, "AMS", emptyMap(), emptyMap())
        assertEquals(false, standard.isAms2Pro())
        assertEquals(false, standard.supportsDrying())
        assertEquals("AMS A", standard.displayName())
        assertNull(standard.modelBadge())

        val ht = AmsInfo("ams_ht", 1, "AMS HT", emptyMap(), emptyMap())
        assertEquals(false, ht.isAms2Pro())
        assertEquals(true, ht.supportsDrying())
        assertEquals("AMS HT", ht.displayName())
        assertEquals("AMS HT", ht.modelBadge())
    }

    @Test
    fun amsDryingTelemetryStates() {
        val amsInfo = AmsInfo(
            "ams1", 1, "AMS 2 Pro",
            ids = mapOf(
                "binary_sensor.drying" to "binary_sensor.ams1_drying",
                "sensor.remaining_drying_time" to "sensor.ams1_drying_remain",
                "sensor.drying_target_temp" to "sensor.ams1_drying_temp"
            ),
            trays = emptyMap()
        )
        val printerInfo = PrinterInfo("p", "serial", "Printer", "X2D", null, emptyMap(), listOf(amsInfo), emptyList())
        val printer = Printer(
            printerInfo,
            mapOf(
                "binary_sensor.ams1_drying" to Entity("binary_sensor.ams1_drying", "on", JsonObject(emptyMap())),
                "sensor.ams1_drying_remain" to Entity("sensor.ams1_drying_remain", "3.5", JsonObject(emptyMap())),
                "sensor.ams1_drying_temp" to Entity("sensor.ams1_drying_temp", "55.0", JsonObject(emptyMap()))
            )
        )

        assertEquals(true, printer.amsIsDrying(amsInfo))
        assertEquals(3.5, printer.amsDryingRemaining(amsInfo))
        assertEquals(55.0, printer.amsDryingTargetTemp(amsInfo))
    }
}
