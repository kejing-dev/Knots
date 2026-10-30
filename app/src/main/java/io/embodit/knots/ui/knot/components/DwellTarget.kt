package io.embodit.knots.ui.knot.components

import androidx.compose.runtime.mutableFloatStateOf

/**
 * Where the "Next" button lies on the guide panel, in the panel's root pixels, and how far the look-to-advance count
 * has come. The button registers its bounds; the scene's frame system tests the gaze ray (eyes, else head) against
 * them every frame and writes [progress] (0..1, quantised) for the ring (the system never tells the app where its
 * pointer hovers).
 */
object DwellTarget {
    data class Bounds(val cx: Float, val cy: Float, val w: Float, val h: Float, val rootW: Int, val rootH: Int)

    @Volatile var bounds: Bounds? = null

    /** 0..1 in steps of 0.05 (written by the scene while the count runs). */
    val progress = mutableFloatStateOf(0f)
}

/**
 * The demo's position for the panel's progress bar, written by the scene (quantised to 0.01, so the panel recomposes
 * at most a few times a second): [share] = whole-knot progress 0..1, [yourTurn] = how much of "your turn" is threaded.
 */
object DemoClock {
    val share = mutableFloatStateOf(0f)
    val yourTurn = mutableFloatStateOf(0f)
}
