package io.embodit.knots.ui.knot.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.CircularProgressDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.SliderDefaults
import com.pico.spatial.ui.design.Text
import io.embodit.knots.BuildConfig
import io.embodit.knots.R
import io.embodit.knots.domain.knot.Category
import io.embodit.knots.domain.knot.KnotLibrary
import io.embodit.knots.domain.model.Hand
import io.embodit.knots.ui.knot.KnotEvent
import io.embodit.knots.ui.knot.KnotUiState
import io.embodit.knots.ui.knot.Mode
import io.embodit.knots.ui.knot.Phase
import io.embodit.knots.ui.knot.debug.probe
import io.embodit.knots.ui.knot.scene.PanelSpec
import io.embodit.knots.ui.theme.KnotColors

/**
 * The guide panel (440 x 380 dp at 0.75 mm / dp, design `panels[0]`): one panel whose content follows the mode. Never
 * more than one primary + one secondary + one icon button + the progress row + two lines of text (title-bar keys aside).
 */
@Composable
internal fun GuidePanel(state: KnotUiState, filled: Boolean, onEvent: (KnotEvent) -> Unit) {
    val (w, h) = PanelSpec.GUIDE_SIZE
    Glass(w.dp, h.dp) {
        if (!filled) return@Glass
        if (state.inKnot && state.placing) {
            PlaceMarker(state, onEvent)
            return@Glass
        }
        if (state.inKnot && state.outOfSight != null) {
            OutOfSight(state, onEvent)
            return@Glass
        }
        when (state.mode) {
            Mode.LOADING -> Loading()
            Mode.WELCOME -> Welcome(state, onEvent)
            Mode.LIBRARY -> Library(state, onEvent)
            Mode.PREVIEW -> Preview(state, onEvent)
            Mode.STEP -> StepContent(state, onEvent)
            Mode.YOUR_TURN -> YourTurn(state, onEvent)
            Mode.DONE -> Done(state, onEvent)
            Mode.RESUME -> Resume(state, onEvent)
            Mode.DATA_ERROR -> DataError(onEvent)
        }
    }
}

@Composable
private fun ColumnScope.Loading() {
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = PicoTheme.colorScheme.labelPrimary, progressSize = CircularProgressDefaults.circleProgressSize(28.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = rs(R.string.loading), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.body, maxLines = 1)
    }
    Spacer(modifier = Modifier.weight(1f))
}

/** S01: do you have a rope (and how thick)? */
@Composable
private fun ColumnScope.Welcome(state: KnotUiState, onEvent: (KnotEvent) -> Unit) {
    Text(text = rs(R.string.welcome_title), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.big, maxLines = 2, overflow = TextOverflow.Ellipsis)
    LabeledRow(rs(R.string.welcome_has_rope)) {
        Segments(
            items = listOf("rope_yes" to rs(R.string.rope_yes), "rope_no" to rs(R.string.rope_no)),
            selected = if (state.settings.hasRope) 0 else 1,
            onSelect = { onEvent(KnotEvent.HasRopePicked(it == 0)) },
            itemWidth = 104.dp,
        )
    }
    if (state.settings.hasRope) {
        LabeledRow(rs(R.string.welcome_size)) { SizeSegments(state, onEvent) }
        Text(text = rs(R.string.welcome_size_hint), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    } else {
        Text(text = rs(R.string.welcome_no_rope_hint), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(modifier = Modifier.weight(1f))
    Text(text = rs(R.string.safety), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Row(modifier = Modifier.fillMaxWidth()) { WordButton("welcome_go", rs(R.string.action_start), { onEvent(KnotEvent.WelcomeDone) }, primary = true, minWidth = 160.dp) }
}

@Composable
internal fun SizeSegments(state: KnotUiState, onEvent: (KnotEvent) -> Unit, itemWidth: androidx.compose.ui.unit.Dp = 76.dp) {
    val sizes = listOf(4, 8, 11)
    Segments(
        items = listOf("size_4" to rs(R.string.size_thin), "size_8" to rs(R.string.size_mid), "size_11" to rs(R.string.size_thick)),
        selected = sizes.indexOf(state.settings.ropeMm).coerceAtLeast(0),
        onSelect = { onEvent(KnotEvent.RopeSizePicked(sizes[it])) },
        itemWidth = itemWidth,
    )
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, color = PicoTheme.colorScheme.labelPrimary, style = KnotType.small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        content()
    }
}

/** S02: which knot? Category chips and the knots, easiest first. */
@Composable
private fun ColumnScope.Library(state: KnotUiState, onEvent: (KnotEvent) -> Unit) {
    TitleRow(title = rs(R.string.library_title), trailing = { Key("gear", R.drawable.ic_gear, rs(R.string.action_settings)) { onEvent(KnotEvent.SettingsClicked) } })
    val cats = KnotLibrary.categories
    Segments(
        items = cats.map { "cat_${it.id}" to categoryName(it) },
        selected = cats.indexOf(state.category),
        onSelect = { onEvent(KnotEvent.CategoryPicked(cats[it])) },
        itemWidth = (400 / cats.size).dp,
        itemHeight = 40.dp,
        small = true,
    )
    // v0.1.2: ten knots; the list scrolls inside the panel
    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in KnotLibrary.ordered(state.category)) {
            val learned = state.progress.learned(k.id)
            val dots = "●".repeat(k.difficulty) + "○".repeat(3 - k.difficulty)
            RowButton(
                name = "knot_${k.id}",
                text = knotName(k, state.language),
                sub = rs(R.string.library_meta, k.steps.size, dots),
                trailing = if (learned) rs(R.string.library_learned) else "",
                trailingColor = KnotColors.LearnedText,
                onClick = { onEvent(KnotEvent.KnotPicked(k.id)) },
                height = 48.dp,
                leading = { KnotThumb(k, size = 36.dp) },
            )
        }
    }
    // the list scrolls: say so (the next row also peeks in at the bottom)
    val count = KnotLibrary.ordered(state.category).size
    if (count > 4) Text(text = rs(R.string.library_scroll, count), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 1)
}

@Composable
private fun categoryName(c: Category): String =
    rs(
        when (c) {
            Category.ALL -> R.string.cat_all
            Category.BASIC -> R.string.cat_basic
            Category.CAMPING -> R.string.cat_camping
            Category.BOATING -> R.string.cat_boating
            Category.CLIMBING -> R.string.cat_climbing
        }
    )

/** Title bar of an open knot: back, name, "turn 90°" (worded), and, when turned on S03 / S07, "my view"; more. */
@Composable
private fun KnotTitle(state: KnotUiState, onEvent: (KnotEvent) -> Unit, myViewInTitle: Boolean) {
    TitleRow(
        title = knotName(state.knot, state.language),
        leading = { Key("back", R.drawable.ic_back, rs(R.string.action_back)) { onEvent(KnotEvent.BackClicked) } },
        trailing = {
            if (myViewInTitle && state.turned) Key("myview", R.drawable.ic_my_view, rs(R.string.action_my_view)) { onEvent(KnotEvent.MyViewClicked) }
            WordButton("turn90", rs(R.string.action_turn90), { onEvent(KnotEvent.Turn90Clicked) }, minWidth = 92.dp, iconRes = R.drawable.ic_turn)
            Key("more", R.drawable.ic_more, rs(R.string.action_more)) { onEvent(KnotEvent.MoreClicked) }
        },
    )
}

/** S03: look at the whole knot once. */
@Composable
private fun ColumnScope.Preview(state: KnotUiState, onEvent: (KnotEvent) -> Unit) {
    val k = state.knot
    KnotTitle(state, onEvent, myViewInTitle = true)
    Text(text = knotAlias(k, state.language), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(text = knotUse(k, state.language), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(text = knotCaution(k, state.language), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.small, maxLines = 3, overflow = TextOverflow.Ellipsis)
    if (k.post) Text(text = rs(R.string.post_hint), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(modifier = Modifier.weight(1f))
    LinearProgressIndicator({ DemoClock.share.floatValue }, Modifier.fillMaxWidth())
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WordButton("follow", rs(R.string.action_follow), { onEvent(KnotEvent.FollowClicked) }, primary = true, minWidth = 140.dp)
        WordButton("preview_again", rs(R.string.action_watch_again), { onEvent(KnotEvent.PreviewAgainClicked) }, minWidth = 120.dp, iconRes = R.drawable.ic_replay)
    }
}

/** Count, dots, the controller and gaze hints shared by S04 - S06 and S16. */
@Composable
private fun StepHeader(state: KnotUiState) {
    val n = state.knot.steps.size
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = rs(R.string.step_count, state.step + 1, n), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 1)
        Spacer(modifier = Modifier.width(10.dp))
        StepDots(count = n, index = state.step)
        Spacer(modifier = Modifier.weight(1f))
        if (state.mode == Mode.STEP && state.settings.dwell && state.gazeFromHead && !state.controllerActive) {
            Text(text = rs(R.string.gaze_head_hint), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (state.controllerActive) HintStrip(rs(R.string.controller_hint), PicoTheme.colorScheme.alert)
}

/** S04 / S05 / S06: the step. */
@Composable
private fun ColumnScope.StepContent(state: KnotUiState, onEvent: (KnotEvent) -> Unit) {
    val step = state.currentStep
    KnotTitle(state, onEvent, myViewInTitle = false)
    StepHeader(state)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Text(text = stepText(step, state.language), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.big, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val mn = stepMnemonic(step, state.language)
        when {
            mn.isNotEmpty() -> Text(text = "「$mn」", color = PicoTheme.colorScheme.labelPrimary, style = KnotType.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
            state.knot.post && state.step == 0 -> Text(text = rs(R.string.post_hint), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    ProgressRow(state, onEvent)
    BottomRow(state, onEvent, dwell = true)
}

/** Play / pause, the whole-knot scrubber with step ticks, the speed chip. */
@Composable
private fun ProgressRow(state: KnotUiState, onEvent: (KnotEvent) -> Unit) {
    val n = state.knot.steps.size
    Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val scrubbing = state.phase == Phase.SCRUB
        Key(if (scrubbing) "play" else "pause", if (scrubbing) R.drawable.ic_play else R.drawable.ic_pause, rs(if (scrubbing) R.string.action_play else R.string.action_pause)) { onEvent(KnotEvent.PlayPauseClicked) }
        Box(modifier = Modifier.weight(1f).height(KeyHeight), contentAlignment = Alignment.Center) {
            StepTicks(n, state.step, Modifier.fillMaxWidth().height(10.dp).align(Alignment.BottomCenter))
            val value = if (scrubbing) state.scrub else DemoClock.share.floatValue
            Slider(
                value,
                { v: Float -> onEvent(KnotEvent.Scrubbed(v)) },
                Modifier.fillMaxWidth().probe("scrubber"),
                0f..1f,
                {},
                colors = SliderDefaults.sliderColors(trackColor = PicoTheme.colorScheme.fillPrimary, progressColor = PicoTheme.colorScheme.interaction, progressHighColor = PicoTheme.colorScheme.interaction),
            )
        }
        WordButton("speed", speedLabel(state.settings.speed), { onEvent(KnotEvent.SpeedClicked) }, minWidth = 64.dp)
    }
}

private fun speedLabel(v: Float): String = if (v == 0.5f) "0.5×" else if (v == 1.5f) "1.5×" else "1×"

/**
 * Next (primary, with the look-to-advance ring; "Done" on the last step, no ring), previous, and replay / "my view".
 * "Next" sits in the corner farthest from the rope: bottom right for right hands, bottom left for left hands.
 */
@Composable
private fun BottomRow(state: KnotUiState, onEvent: (KnotEvent) -> Unit, dwell: Boolean) {
    val last = state.lastStep
    val next: @Composable () -> Unit = {
        val text = rs(if (last) R.string.action_done else R.string.action_next)
        if (dwell && !last) DwellRing(enabledSetting = state.settings.dwell) { WordButton("next", text, { onEvent(KnotEvent.NextClicked) }, primary = true, minWidth = 124.dp) }
        else WordButton("next", text, { onEvent(KnotEvent.NextClicked) }, primary = true, minWidth = 124.dp)
    }
    val prev: @Composable () -> Unit = { WordButton("prev", rs(R.string.action_prev), { onEvent(KnotEvent.PrevClicked) }, enabled = state.step > 0, minWidth = 96.dp) }
    val replay: @Composable () -> Unit = {
        if (state.turned) WordButton("myview", rs(R.string.action_my_view), { onEvent(KnotEvent.MyViewClicked) }, minWidth = 96.dp, iconRes = R.drawable.ic_my_view)
        else WordButton("replay", rs(R.string.action_replay), { onEvent(KnotEvent.ReplayClicked) }, minWidth = 96.dp, iconRes = R.drawable.ic_replay)
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.hand == Hand.LEFT) {
            next()
            Spacer(modifier = Modifier.weight(1f))
            prev()
            replay()
        } else {
            replay()
            prev()
            Spacer(modifier = Modifier.weight(1f))
            next()
        }
    }
}

/** S16: your turn (no rope): drag the orange tip along the dashes; "Next" skips it. */
@Composable
private fun ColumnScope.YourTurn(state: KnotUiState, onEvent: (KnotEvent) -> Unit) {
    KnotTitle(state, onEvent, myViewInTitle = false)
    StepHeader(state)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
        Text(text = stepText(state.currentStep, state.language), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.big, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val how =
            when {
                state.tightenStep -> R.string.yourturn_pull
                state.controllerActive -> R.string.yourturn_controller
                else -> R.string.yourturn_pinch
            }
        Text(text = rs(how), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Row(modifier = Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = rs(R.string.yourturn_progress), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 1)
        LinearProgressIndicator({ DemoClock.yourTurn.floatValue }, Modifier.weight(1f))
    }
    BottomRow(state, onEvent, dwell = false)
}

/** S07: done. */
@Composable
private fun ColumnScope.Done(state: KnotUiState, onEvent: (KnotEvent) -> Unit) {
    KnotTitle(state, onEvent, myViewInTitle = true)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DoneMark(40.dp)
        Text(text = rs(R.string.done_title), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.big, maxLines = 1)
    }
    Text(text = rs(R.string.done_check), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 1)
    Text(text = knotCheck(state.knot, state.language), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WordButton("again", rs(R.string.action_again), { onEvent(KnotEvent.AgainClicked) }, primary = true, minWidth = 140.dp)
        WordButton("library", rs(R.string.action_other_knot), { onEvent(KnotEvent.BackClicked) }, minWidth = 140.dp)
    }
}

/** S13: carry on where you left off? */
@Composable
private fun ColumnScope.Resume(state: KnotUiState, onEvent: (KnotEvent) -> Unit) {
    val cur = state.progress.current
    val k = cur?.let { KnotLibrary.knot(it.knotId) } ?: state.knot
    Spacer(modifier = Modifier.weight(1f))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KnotThumb(k, 56.dp)
        Text(text = rs(R.string.resume_title, knotName(k, state.language), (cur?.step ?: 0) + 1), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.big, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WordButton("resume", rs(R.string.action_resume), { onEvent(KnotEvent.ResumeClicked) }, primary = true, minWidth = 140.dp)
        WordButton("library", rs(R.string.action_library), { onEvent(KnotEvent.BackClicked) }, minWidth = 140.dp)
    }
}

/** S15: the knot data could not be read. */
@Composable
private fun ColumnScope.DataError(onEvent: (KnotEvent) -> Unit) {
    Spacer(modifier = Modifier.weight(1f))
    Text(text = rs(R.string.error_title), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.big, maxLines = 2)
    Text(text = rs(R.string.version, BuildConfig.VERSION_NAME), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 1)
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth()) { WordButton("retry", rs(R.string.action_retry), { onEvent(KnotEvent.RetryClicked) }, primary = true, minWidth = 140.dp) }
}

/** S14: the rope is behind / left / right; the panel has come along. */
@Composable
private fun ColumnScope.OutOfSight(state: KnotUiState, onEvent: (KnotEvent) -> Unit) {
    TitleRow(title = knotName(state.knot, state.language))
    Spacer(modifier = Modifier.weight(1f))
    val where =
        when (state.outOfSight) {
            "left" -> R.string.away_left
            "right" -> R.string.away_right
            else -> R.string.away_behind
        }
    Text(text = rs(where), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.big, maxLines = 2, textAlign = TextAlign.Start)
    Spacer(modifier = Modifier.weight(1f))
    Text(text = rs(R.string.away_body), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth()) { WordButton("bring_here", rs(R.string.action_new_marker), { onEvent(KnotEvent.BringHereClicked) }, primary = true, minWidth = 160.dp) }
}

/**
 * S17: put a marker on the floor (controller ray + trigger, or gaze + pinch), then "move to marker" (factory rule D1).
 * The rope then stays at that anchor for the whole session.
 */
@Composable
private fun ColumnScope.PlaceMarker(state: KnotUiState, onEvent: (KnotEvent) -> Unit) {
    TitleRow(title = knotName(state.knot, state.language), leading = { Key("back", R.drawable.ic_back, rs(R.string.action_back)) { onEvent(KnotEvent.PlacingCancelled) } })
    Text(text = rs(R.string.marker_title), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.big, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(text = rs(if (state.controllerActive) R.string.marker_how_controller else R.string.marker_how_pinch), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(text = rs(if (state.marker != null) R.string.marker_set else R.string.marker_hint), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WordButton("to_marker", rs(R.string.action_to_marker), { onEvent(KnotEvent.MoveToMarkerClicked) }, primary = true, enabled = state.marker != null, minWidth = 160.dp)
        if (state.rope != null) WordButton("marker_cancel", rs(R.string.action_cancel), { onEvent(KnotEvent.PlacingCancelled) }, minWidth = 104.dp)
    }
}

@Suppress("unused") private fun RowScope.keep() = Unit
