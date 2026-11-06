package io.embodit.knots.ui.knot

import org.junit.Assert.assertEquals
import org.junit.Test

/** v0.1.4: settings' quit-app confirm (D26) is its own designed state, S18, apart from "clear" (S12). */
class KnotUiStateTest {
    @Test
    fun quitConfirmIsS18() {
        assertEquals("S11", KnotUiState(mode = Mode.LIBRARY, settingsOpen = true).stateId)
        assertEquals("S18", KnotUiState(mode = Mode.LIBRARY, settingsOpen = true, confirmExit = true).stateId)
        assertEquals("S12", KnotUiState(mode = Mode.LIBRARY, settingsOpen = true, confirmClear = true).stateId)
    }

    @Test
    fun quitConfirmNeedsSettingsOpen() {
        assertEquals("S02", KnotUiState(mode = Mode.LIBRARY, confirmExit = true).stateId)
    }
}
