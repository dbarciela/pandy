package io.github.gccammelli.pandy

import io.github.gccammelli.pandy.ui.extrudeAllowed
import io.github.gccammelli.pandy.ui.nozzleSwitching
import io.github.gccammelli.pandy.ui.shouldSwitchNozzle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Nozzle sheet's gating rules for a dual-nozzle printer, one case per operand. */
class NozzleGatingTest {
    @Test fun switchingIsOnlyReportedWhileIdleAndEitherPendingOrMismatched() {
        assertFalse(nozzleSwitching(printing = true, pending = "left", dual = true, selected = "left", active = "right"))
        assertTrue(nozzleSwitching(printing = false, pending = "left", dual = false, selected = null, active = null))
        assertFalse(nozzleSwitching(printing = false, pending = null, dual = false, selected = "left", active = "right"))
        assertTrue(nozzleSwitching(printing = false, pending = null, dual = true, selected = "left", active = "right"))
        assertFalse(nozzleSwitching(printing = false, pending = null, dual = true, selected = null, active = "right"))
        assertFalse(nozzleSwitching(printing = false, pending = null, dual = true, selected = "left", active = null))
        assertFalse(nozzleSwitching(printing = false, pending = null, dual = true, selected = "left", active = "left"))
    }

    @Test fun extrusionNeedsIdleNoSwitchHeatAndAConfirmedNozzleWhenDual() {
        assertFalse(extrudeAllowed(printing = true, switching = false, dual = false, selected = null, active = null, temp = 220.0))
        assertFalse(extrudeAllowed(printing = false, switching = true, dual = false, selected = null, active = null, temp = 220.0))
        assertTrue(extrudeAllowed(printing = false, switching = false, dual = false, selected = null, active = null, temp = 170.0))
        assertFalse(extrudeAllowed(printing = false, switching = false, dual = false, selected = null, active = null, temp = 169.9))
        assertFalse(extrudeAllowed(printing = false, switching = false, dual = false, selected = null, active = null, temp = null))
        assertTrue(extrudeAllowed(printing = false, switching = false, dual = true, selected = "left", active = "left", temp = 200.0))
        assertFalse(extrudeAllowed(printing = false, switching = false, dual = true, selected = "left", active = "right", temp = 200.0))
        assertFalse(extrudeAllowed(printing = false, switching = false, dual = true, selected = null, active = null, temp = 200.0))
        assertFalse(extrudeAllowed(printing = false, switching = false, dual = true, selected = "left", active = null, temp = 200.0))
    }

    @Test fun pickingANozzleSwitchesOnlyWhenIdleInactiveAndNotAlreadyPending() {
        assertFalse(shouldSwitchNozzle(printing = true, active = "right", pending = null, side = "left"))
        assertFalse(shouldSwitchNozzle(printing = false, active = "left", pending = null, side = "left"))
        assertFalse(shouldSwitchNozzle(printing = false, active = "right", pending = "left", side = "left"))
        assertTrue(shouldSwitchNozzle(printing = false, active = "right", pending = null, side = "left"))
        assertTrue(shouldSwitchNozzle(printing = false, active = null, pending = "right", side = "left"))
    }
}
