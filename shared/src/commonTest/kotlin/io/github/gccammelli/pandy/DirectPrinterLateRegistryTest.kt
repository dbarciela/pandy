package io.github.gccammelli.pandy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DirectPrinterLateRegistryTest {
    private suspend fun changedRegistry(initial: String, update: String, check: suspend (DirectPrinter) -> Unit) {
        val messages = Channel<String>(Channel.UNLIMITED)
        val ready = CompletableDeferred<Unit>()
        val link = object : LanLink {
            override suspend fun run(onConnected: () -> Unit, onMessage: (String) -> Unit) {
                onConnected(); onMessage(initial)
                for (message in messages) onMessage(message)
            }
            override fun publish(payload: String) = Unit
            override fun close() = Unit
        }
        val printer = DirectPrinter(LanConfig("example.invalid", "DEMO", "", "H2D"), true, { link })
        try {
            printer.ws(listOf(buildJsonObject { put("type", "config/entity_registry/list") }))
            kotlinx.coroutines.coroutineScope {
                val stream = async { runCatching { printer.stream(buildJsonObject {}) { ready.complete(Unit) } } }
                withTimeout(3000) { ready.await() }
                messages.send(update)
                assertIs<ApiException>(withTimeout(3000) { stream.await() }.exceptionOrNull())
            }
            check(printer)
        } finally { printer.stop(); messages.close() }
    }

    @Test fun lateVersionInfoRefreshesTheAmsModel() = runBlocking {
        changedRegistry("""{"print":{"command":"push_status","ams":{"ams":[{"id":0}]}}}""",
            """{"info":{"module":[{"name":"n3f/0"}]}}""") { printer ->
            assertEquals("AMS 2 Pro", printer.printers().single().ams.single().model)
        }
    }

    @Test fun lateNozzleDataRefreshesTheEntityRegistry() = runBlocking {
        changedRegistry("""{"print":{"command":"push_status"}}""",
            """{"print":{"device":{"extruder":{"info":[{"id":0,"temp":14418140}]}}}}""") { printer ->
            assertEquals(true, printer.printers().single().ids.containsKey("sensor.right_nozzle_temp"))
        }
    }
}
