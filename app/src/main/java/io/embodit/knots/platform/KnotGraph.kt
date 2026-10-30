package io.embodit.knots.platform

import android.content.Context
import io.embodit.knots.data.KnotRepository
import io.embodit.knots.data.PrefsKnotRepository
import io.embodit.knots.platform.input.ControllerInput
import io.embodit.knots.platform.tracking.EyeTracker
import io.embodit.knots.platform.tracking.HeadTracker

/**
 * Process-wide wiring of the platform services. The scene starts and stops the tracking services (every start has a
 * stop: `KnotScene.attach` / `detach`); the ViewModel only sees the repository.
 */
object KnotGraph {
    val head = HeadTracker()
    val eyes = EyeTracker()
    val controller = ControllerInput()

    @Volatile var appContext: Context? = null

    @Volatile private var repository: KnotRepository? = null

    fun repository(context: Context): KnotRepository =
        repository ?: synchronized(this) { repository ?: PrefsKnotRepository(context).also { repository = it } }
}
