package io.embodit.knots.domain.model

/** Why spatial sensing cannot be used (from the HMD provider's support state). */
enum class BlockReason { NOT_IN_FULL_SPACE, WITHOUT_PERMISSION, DEVICE_NOT_SUPPORTED }

/** Language chosen in Settings: follow the system, Chinese, English. */
enum class LanguagePref { SYSTEM, ZH, EN }

/** Main hand (design `spatialLayout.rope.mirror`): left = the demo rope mirrored in x, the panel on the left. */
enum class Hand { RIGHT, LEFT }

/**
 * Demo rope height above the floor marker (design T22, v0.1.1: the rope hangs over a marker the user put on the floor,
 * factory rule D1 "move to marker"): the rope frame's origin is 1.25 / 1.10 / 0.95 m above the floor.
 */
enum class RopeHeight(val id: String, val aboveM: Float) {
    HIGH("high", 1.25f),
    NORMAL("normal", 1.10f),
    LOW("low", 0.95f);

    companion object {
        fun byId(id: String): RopeHeight? = entries.firstOrNull { it.id == id }
    }
}

/** Persisted settings (design `dataModel.settings`). */
data class Settings(
    val language: LanguagePref = LanguagePref.SYSTEM,
    val hand: Hand = Hand.RIGHT,
    /** "I have a rope" (follow along) or not (S16 "your turn" after each demo, design `ropeMode`). */
    val hasRope: Boolean = true,
    /** Rope diameter in mm: 4 / 8 / 11 (without a rope the demo uses 8). */
    val ropeMm: Int = 8,
    val height: RopeHeight = RopeHeight.NORMAL,
    val speed: Float = 1f,
    /** Look-to-advance (1.2 s on "Next"). Default on; debug builds start with it off (design `input.dwell`). */
    val dwell: Boolean = true,
    val welcomeSeen: Boolean = false,
) {
    /** The diameter the demo rope is drawn with. */
    val drawMm: Int
        get() = if (hasRope) ropeMm else 8
}

/** Where the user left off (design `dataModel.progress.current`). */
data class CurrentKnot(val knotId: String, val step: Int)

/** One knot's record. */
data class KnotRecord(val learned: Boolean = false, val learnedAt: Long = 0L, val times: Int = 0)

/** The rope frame's pose (the anchor) and its floor marker, kept so the rope comes back to the same place. */
data class SavedPose(val x: Float, val y: Float, val z: Float, val yaw: Float)

/** Persisted progress. */
data class Progress(
    val records: Map<String, KnotRecord> = emptyMap(),
    val current: CurrentKnot? = null,
    val ropePose: SavedPose? = null,
) {
    fun learned(id: String): Boolean = records[id]?.learned == true
}
