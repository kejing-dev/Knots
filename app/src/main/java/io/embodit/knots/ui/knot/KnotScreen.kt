package io.embodit.knots.ui.knot

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.SpatialNavigator
import io.embodit.knots.platform.EyePermissionActivity
import io.embodit.knots.platform.KnotLog
import io.embodit.knots.ui.knot.components.KnotStage
import io.embodit.knots.ui.knot.components.LocalKnotResources
import io.embodit.knots.ui.knot.components.localizedResources
import io.embodit.knots.ui.knot.debug.KnotProbe
import io.embodit.knots.ui.knot.scene.KnotFrameSystem
import io.embodit.knots.ui.knot.scene.KnotScene
import io.embodit.knots.ui.knot.scene.RopeWindow
import kotlin.system.exitProcess

/**
 * The one screen of the app, living in a Mixed Stage. It wires the three parts together: the
 * ViewModel holds the state, the [KnotScene] turns state into entities and reports the demo's progress, drags and
 * the gaze, and [KnotStage] declares the panels.
 */
@Composable
fun KnotScreen() {
    val context = LocalContext.current
    val viewModel: KnotViewModel = viewModel(factory = KnotViewModel.Factory(context))
    val state by viewModel.state.collectAsStateWithLifecycle()

    remember(context) {
        KnotProbe.enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        true
    }
    val scene = remember(viewModel) { KnotScene(viewModel::onEvent) }

    DisposableEffect(scene) {
        registerSystem<KnotFrameSystem>()
        KnotProbe.host =
            object : KnotProbe.Host {
                override fun dispatch(event: KnotEvent) = viewModel.onEvent(event)

                override fun uiState(): KnotUiState = viewModel.state.value

                override fun dumpJson(): String = viewModel.dumpJson()

                override fun scene(): KnotProbe.SceneProbe = scene

                override fun pressControl(name: String): Boolean = KnotProbe.click(name)

                override fun sceneHandle(): Any = scene
            }
        KnotLog.i("STAGE enter")
        onDispose {
            KnotProbe.host = null
            unregisterSystem<KnotFrameSystem>()
            scene.detach()
            KnotLog.i("STAGE disposed")
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    when (event) {
                        Lifecycle.Event.ON_STOP -> viewModel.onEvent(KnotEvent.WentBackground)
                        Lifecycle.Event.ON_START -> viewModel.onEvent(KnotEvent.CameForeground)
                        else -> Unit
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(scene, state) { scene.render(state) }

    val navigator = LocalSpatialNavigator.current
    DisposableEffect(navigator, viewModel) {
        RopeWindow.navigator = navigator
        RopeWindow.onClosedByUser = { viewModel.onEvent(KnotEvent.RopeWindowClosed) }
        onDispose {
            if (RopeWindow.navigator === navigator) RopeWindow.navigator = null
            RopeWindow.onClosedByUser = null
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect: KnotUiEffect ->
            when (effect) {
                is KnotUiEffect.MoveToMarker -> scene.moveToMarker(effect.marker)
                KnotUiEffect.RecallRope -> scene.recallRope("user")
                is KnotUiEffect.Turn -> scene.turnBy(effect.degrees)
                KnotUiEffect.ResetTurn -> scene.resetTurn()
                KnotUiEffect.AskEyePermission -> if (!EyePermissionActivity.granted(context)) EyePermissionActivity.open(context)
                KnotUiEffect.Quit -> quitApp(context, scene, navigator)
            }
        }
    }

    val resources = remember(state.language, context) { localizedResources(context, state.language) }
    CompositionLocalProvider(LocalKnotResources provides resources) {
        KnotStage(state = state, scene = scene, onEvent = viewModel::onEvent)
    }
}

/** D26 quit, the factory order: release the scene, close the Stage, remove the app's tasks, end the process 400 ms later. */
private suspend fun quitApp(context: Context, scene: KnotScene, navigator: SpatialNavigator) {
    KnotLog.i("EXIT confirmed: release + closeStage + finishAndRemoveTask + exit")
    RopeWindow.closeForQuit()
    runCatching { scene.detach() }.onFailure { KnotLog.i("EXIT detach: $it") }
    runCatching { navigator.closeStage() }.onFailure { KnotLog.i("EXIT closeStage: $it") }
    runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.appTasks?.forEach { runCatching { it.finishAndRemoveTask() } }
        context.findActivity()?.finishAndRemoveTask()
    }.onFailure { KnotLog.i("EXIT finishAndRemoveTask: $it") }
    Handler(Looper.getMainLooper()).postDelayed({ exitProcess(0) }, 400)
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
