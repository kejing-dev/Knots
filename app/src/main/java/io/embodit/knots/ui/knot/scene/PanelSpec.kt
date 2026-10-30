package io.embodit.knots.ui.knot.scene

/** Ids and dp sizes of every attachment panel (design `panels`); the Compose side declares exactly these, once. */
object PanelSpec {
    const val GUIDE = "guidePanel"
    const val SETTINGS = "settingsPanel"
    const val MENU = "menuPanel"

    /** Width / height in dp. */
    val GUIDE_SIZE = 440f to 380f
    val SETTINGS_SIZE = 380f to 420f
    val MENU_SIZE = 300f to 360f

    /** 1 dp = 0.75 mm: the guide panel is 0.33 x 0.255 m at 0.9 m (design `physicalM`). */
    const val SCALE = 0.9375f

    val pool: List<String> = listOf(GUIDE, SETTINGS, MENU)

    fun size(id: String): Pair<Float, Float> =
        when (id) {
            GUIDE -> GUIDE_SIZE
            SETTINGS -> SETTINGS_SIZE
            else -> MENU_SIZE
        }

    /** Half width / height of a panel in metres at [scale]. */
    fun halfM(id: String, scale: Float = SCALE): Pair<Float, Float> {
        val (w, h) = size(id)
        return (w * PanelRig.DP_M * scale / 2f) to (h * PanelRig.DP_M * scale / 2f)
    }
}
