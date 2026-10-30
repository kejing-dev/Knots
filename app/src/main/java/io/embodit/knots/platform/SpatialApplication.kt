package io.embodit.knots.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import io.embodit.knots.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KnotGraph.appContext = applicationContext
        launch(::mainApp)
    }
}
