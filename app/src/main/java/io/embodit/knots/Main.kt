package io.embodit.knots

import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.VolumeAlignment
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.pico.spatial.ui.foundation.dsl.WorldScale
import com.pico.spatial.ui.platform.LengthUnit
import io.embodit.knots.ui.knot.KnotScreen
import io.embodit.knots.ui.knot.scene.ROPE_VOLUME_ID
import io.embodit.knots.ui.knot.scene.RopeVolume
import io.embodit.knots.ui.theme.KnotsTheme

/**
 * App entry. One Mixed Stage (AndroidManifest: `pico.spatial.stage.style=1`, immersion 0/0/0) holds
 * the guide panel and the app's state (design package `container`). v0.1.4 (user 2026-11-05, factory D21): the demo
 * rope stands in a system Volumetric window opened from the Stage, life size (fixed world scale), upright; the system
 * draws its window bar (move / close) under it instead of the app's own 3D bar.
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            KnotsTheme {
                KnotScreen()
            }
        }
        WindowContainer(
            ROPE_VOLUME_ID,
            form = Form.Volumetric,
            defaultSize = WindowContainerSize(ROPE_VOLUME_W_M, ROPE_VOLUME_H_M, ROPE_VOLUME_D_M, LengthUnit.Meters),
            enableMaterialBackground = false,
            volumeAlignment = VolumeAlignment.Gravity,
            worldScale = WorldScale.Fixed,
        ) { KnotsTheme { RopeVolume() } }
    }

/**
 * The rope volume (metres): a life-size knot (≤ ~0.3 m) with its standing part and the height setting's ±7.5 cm; as
 * deep as it is wide, so the rope still fits when turned 90° (the system bar sits right under it).
 */
private const val ROPE_VOLUME_W_M = 0.45f
private const val ROPE_VOLUME_H_M = 0.5f
private const val ROPE_VOLUME_D_M = 0.45f
