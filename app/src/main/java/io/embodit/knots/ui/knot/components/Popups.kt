package io.embodit.knots.ui.knot.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import io.embodit.knots.BuildConfig
import io.embodit.knots.R
import io.embodit.knots.domain.model.Hand
import io.embodit.knots.domain.model.LanguagePref
import io.embodit.knots.domain.model.RopeHeight
import io.embodit.knots.ui.knot.KnotEvent
import io.embodit.knots.ui.knot.KnotUiState
import io.embodit.knots.ui.knot.debug.probe
import io.embodit.knots.ui.knot.scene.PanelSpec
import io.embodit.knots.ui.theme.KnotColors

/**
 * Settings (380 x 420 dp, S11; design `panels[1]`), a pooled attachment panel in front of the guide panel (a Stage
 * popup; no floating dialog): language, hand, rope or not, thickness, rope height, look-to-advance, clear, about, quit.
 * The content scrolls past the panel's height. "Clear" (S12) and "quit app" (S18) swap the content for a confirm strip.
 */
@Composable
internal fun SettingsPanel(state: KnotUiState, filled: Boolean, onEvent: (KnotEvent) -> Unit) {
    val (w, h) = PanelSpec.SETTINGS_SIZE
    Glass(w.dp, h.dp, corner = 24.dp, padding = 16.dp, spacing = 6.dp) {
        if (!filled) return@Glass
        if (state.confirmClear) {
            Text(text = rs(R.string.settings_title), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.title, maxLines = 1)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = rs(R.string.confirm_clear), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.big, maxLines = 2)
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WordButton("confirm_cancel", rs(R.string.action_cancel), { onEvent(KnotEvent.ClearCancelled) }, minWidth = 120.dp)
                WordButton("confirm_clear", rs(R.string.action_clear), { onEvent(KnotEvent.ClearConfirmed) }, danger = true, minWidth = 120.dp)
            }
            return@Glass
        }
        if (state.confirmExit) {
            // S18 (D26): the second ask sits in the same panel, like "clear"; no second panel
            Text(text = rs(R.string.settings_title), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.title, maxLines = 1)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = rs(R.string.exit_title), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.big, maxLines = 2)
            Text(text = rs(R.string.exit_body), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.small, maxLines = 2)
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WordButton("exit_cancel", rs(R.string.action_cancel), { onEvent(KnotEvent.ExitCancelled) }, minWidth = 120.dp)
                WordButton("exit_confirm", rs(R.string.exit_yes), { onEvent(KnotEvent.ExitConfirmed) }, danger = true, minWidth = 120.dp)
            }
            return@Glass
        }
        Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(text = rs(R.string.settings_title), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.title, maxLines = 1, modifier = Modifier.weight(1f))
            WordButton("settings_close", rs(R.string.action_close), { onEvent(KnotEvent.SettingsDismissed) }, minWidth = 88.dp)
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingRow(rs(R.string.settings_language)) {
                Segments(
                    items = listOf("lang_system" to rs(R.string.lang_system), "lang_zh" to rs(R.string.lang_zh), "lang_en" to rs(R.string.lang_en)),
                    selected = state.settings.language.ordinal,
                    onSelect = { onEvent(KnotEvent.LanguageSelected(LanguagePref.entries[it])) },
                    itemWidth = 72.dp,
                )
            }
            SettingRow(rs(R.string.settings_hand)) {
                Segments(
                    items = listOf("hand_right" to rs(R.string.hand_right), "hand_left" to rs(R.string.hand_left)),
                    selected = state.settings.hand.ordinal,
                    onSelect = { onEvent(KnotEvent.HandSelected(Hand.entries[it])) },
                    itemWidth = 84.dp,
                )
            }
            SettingRow(rs(R.string.settings_has_rope)) {
                Segments(
                    items = listOf("rope_yes" to rs(R.string.rope_yes), "rope_no" to rs(R.string.rope_no)),
                    selected = if (state.settings.hasRope) 0 else 1,
                    onSelect = { onEvent(KnotEvent.HasRopePicked(it == 0)) },
                    itemWidth = 90.dp,
                )
            }
            if (state.settings.hasRope) SettingRow(rs(R.string.settings_size)) { SizeSegments(state, onEvent, itemWidth = 84.dp) }
            SettingRow(rs(R.string.settings_height)) {
                Segments(
                    items = listOf("height_high" to rs(R.string.height_high), "height_normal" to rs(R.string.height_normal), "height_low" to rs(R.string.height_low)),
                    selected = state.settings.height.ordinal,
                    onSelect = { onEvent(KnotEvent.HeightSelected(RopeHeight.entries[it])) },
                    itemWidth = 72.dp,
                )
            }
            Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically) {
                Text(text = rs(R.string.settings_dwell), color = PicoTheme.colorScheme.labelPrimary, style = KnotType.small, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Switch(state.settings.dwell, { on -> onEvent(KnotEvent.DwellSelected(on)) }, Modifier.probe("dwell_switch") { onEvent(KnotEvent.DwellSelected(!state.settings.dwell)) })
            }
            Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically) {
                WordButton("clear", rs(R.string.settings_clear), { onEvent(KnotEvent.ClearClicked) }, enabled = state.progress.records.isNotEmpty() || state.progress.current != null, minWidth = 140.dp)
            }
            Text(text = rs(R.string.settings_about, BuildConfig.VERSION_NAME), color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
            // D26: "quit app" is the last row of settings
            Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically) {
                WordButton("exit", rs(R.string.settings_exit), { onEvent(KnotEvent.ExitClicked) }, danger = true, minWidth = 140.dp)
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, color = PicoTheme.colorScheme.labelPrimary, style = KnotType.small, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        content()
    }
}

/** A SegmentControl whose items are [itemWidth] x [itemHeight]; the selected item is the orange accent with a dark label. */
@Composable
internal fun Segments(
    items: List<Pair<String, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    itemWidth: Dp,
    itemHeight: Dp = KeyHeight,
    small: Boolean = false,
) {
    // gate:allow tabs-segment the shared Segments helper: same-page options (language, hand, rope, size, height) and the library's category filter, never page switching
    SegmentControl(modifier = Modifier.width(itemWidth * items.size).height(itemHeight), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
        items.forEachIndexed { i, (name, text) ->
            val onClick = { onSelect(i) }
            SegmentItem(
                selected = selected == i,
                onClick = onClick,
                modifier = Modifier.width(itemWidth).height(itemHeight).probe(name, onClick),
                textStyle = if (small) PicoTheme.typography.labelMedium else PicoTheme.typography.labelLarge,
                colors =
                    SegmentControlDefaults.colors(
                        itemBackgroundColor = Color.Transparent,
                        itemContentColor = PicoTheme.colorScheme.labelPrimary,
                        selectedItemBackgroundColor = PicoTheme.colorScheme.interaction,
                        selectedItemContentColor = KnotColors.OnAccent,
                    ),
                title = {
                    // design-style: inherited-content-color SegmentItem
                    Text(text = text, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
                },
            )
        }
    }
}

/** The step panel's more menu (S10): new marker / move to marker / hide or show the rope / start over / settings / library. */
@Composable
internal fun MenuPanel(state: KnotUiState, filled: Boolean, onEvent: (KnotEvent) -> Unit) {
    val (w, h) = PanelSpec.MENU_SIZE
    Glass(w.dp, h.dp, corner = 24.dp, padding = 10.dp, spacing = 6.dp) {
        if (!filled) return@Glass
        RowButton("menu_bring", rs(R.string.menu_bring_here), "", "", { onEvent(KnotEvent.MenuBringHere) }, height = 48.dp)
        RowButton("menu_hide", rs(if (state.ropeHidden) R.string.menu_show_rope else R.string.menu_hide_rope), "", "", { onEvent(KnotEvent.MenuHideRope) }, height = 48.dp)
        RowButton("menu_restart", rs(R.string.menu_restart), "", "", { onEvent(KnotEvent.MenuRestart) }, height = 48.dp)
        RowButton("menu_settings", rs(R.string.menu_settings), "", "", { onEvent(KnotEvent.MenuSettings) }, height = 48.dp)
        RowButton("menu_home", rs(R.string.menu_home), "", "", { onEvent(KnotEvent.MenuHome) }, height = 48.dp)
    }
}
