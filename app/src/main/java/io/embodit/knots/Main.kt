package io.embodit.knots

import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import io.embodit.knots.ui.knot.KnotScreen
import io.embodit.knots.ui.theme.KnotsTheme

/**
 * App entry. One Mixed Stage (AndroidManifest: `pico.spatial.stage.style=1`, immersion 0/0/0) holds
 * the whole product: the demo sheet on the desk and the guide panel (design package `container`).
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            KnotsTheme {
                KnotScreen()
            }
        }
    }
