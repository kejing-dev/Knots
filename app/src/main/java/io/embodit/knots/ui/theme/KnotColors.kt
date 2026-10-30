package io.embodit.knots.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette (design `palette`): SpatialUI vibrant roles on the Material.Regular glass, one accent (life-ring orange)
 * that is at once the rope's working end, the primary button and the look-to-advance ring. The only file with colour
 * literals. Brand tokens colour the 3D rope.
 */
object KnotColors {
    // ---- ColorScheme roles that the design fixes (the rest follow the system Vibrant scheme)
    val Interaction = Color(0xFFF26B1D) // design-style: fixed-figma-color interaction: primary buttons, dwell ring (dark label 6.4:1)
    val Passable = Color(0xFF5FD3A4) // design-style: fixed-figma-color passable: learned check
    val Alert = Color(0xFFFFB648) // design-style: fixed-figma-color alert: controller hint strip (dark label 10:1)
    val Error = Color(0xFFFF6B5E) // design-style: fixed-figma-color error: "clear" confirm fill (dark label 6.9:1)

    val InteractionText = Color(0xFFB0470C) // design-style: fixed-figma-color interactionText: orange words on the light glass (mnemonic, your-turn hint), 5:1

    val LearnedText = Color(0xFF1E7A52) // design-style: fixed-figma-color learnedText: the library's learned mark on the glass row (was passable green, 1.4:1)

    /** Dark label on the bright fills above (white on orange is only 3:1). */
    val OnAccent = Color(0xFF1B1209) // design-style: fixed-figma-color onAccent: labels on orange / amber / coral fills

    // ---- brand tokens (3D content and pictograms)
    val Hemp = Color(0xFFEDE3CF) // design-style: fixed-figma-color ropeHemp: standing part
    val WorkingOrange = Color(0xFFF26B1D) // design-style: fixed-figma-color workingOrange: the working end
    val SecondBlue = Color(0xFF2F6FB3) // design-style: fixed-figma-color secondEndBlue: the square knot's other end
    val SecondRed = Color(0xFFD7322E) // design-style: fixed-figma-color secondRopeRed: a bend's second rope (v0.1.3)
    val Whipping = Color(0xFF3A3430) // design-style: fixed-figma-color tipWhipping: dark rope ends
    val HarbourTeal = Color(0xFF13788C) // design-style: fixed-figma-color harbourTeal: icon background
    val White = Color(0xFFFFFFFF) // design-style: fixed-figma-color white: post, handle
    val Black = Color(0xFF000000) // design-style: fixed-figma-color black: shadow

    // ---- the rope palette texture (ARGB ints, 10 columns: colour + darker stripe; design ropeShadowStripe = black 18 %)
    const val RopeHemp = 0xFFEDE3CF.toInt()
    const val RopeHempStripe = 0xFFC2BAA9.toInt()
    const val RopeOrange = 0xFFF26B1D.toInt()
    const val RopeOrangeStripe = 0xFFC65818.toInt()
    const val RopeBlue = 0xFF2F6FB3.toInt()
    const val RopeBlueStripe = 0xFF275B93.toInt()
    const val RopeWhip = 0xFF3A3430.toInt()

    // v0.1.3: the second rope of a bend (blue first rope, red second)
    const val RopeRed = 0xFFD7322E.toInt()
    const val RopeRedStripe = 0xFFAE2826.toInt()
}
