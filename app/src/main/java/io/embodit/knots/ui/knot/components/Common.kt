package io.embodit.knots.ui.knot.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.knots.domain.knot.Knot
import io.embodit.knots.domain.knot.KnotStep
import io.embodit.knots.domain.model.LanguagePref
import io.embodit.knots.ui.knot.debug.probe
import io.embodit.knots.ui.theme.KnotColors

/** Panel padding (design: Material.Regular glass root, 32 dp corners, roomy layout B19). */
internal val PanelPadding = 18.dp
internal val KeyWidth = 48.dp
internal val KeyHeight = 48.dp

/** Glass root of a panel: clipped shape, Material.Regular (design `panels[].material`), padding. */
@Composable
internal fun Glass(width: Dp, height: Dp, corner: Dp = 32.dp, material: Material = Material.Regular, padding: Dp = PanelPadding, spacing: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.size(width, height).clip(RoundedCornerShape(corner)).backgroundMaterial(enable = true, style = material).padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
    }
}

/** Type roles of the design (`typography.roles`, panel at 0.9 m). */
internal object KnotType {
    /** titleLarge 24/30 SemiBold: the step sentence. */
    val big: TextStyle
        @Composable get() = PicoTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)

    /** titleMedium 16/20 SemiBold: knot names, panel titles, button labels. */
    val title: TextStyle
        @Composable get() = PicoTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

    /** bodyLarge 18/24 Medium: use, check. */
    val body: TextStyle
        @Composable get() = PicoTheme.typography.bodyLargeMultiline.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)

    /** bodyMedium 16/22 Medium: mnemonic, caution, safety note. */
    val small: TextStyle
        @Composable get() = PicoTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)

    /** labelMedium 14/18 SemiBold: 2 / 5, speed, categories. */
    val label: TextStyle
        @Composable get() = PicoTheme.typography.labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum, lnum")
}

/** True when the UI is in Chinese (the chosen language, or the system's when following it). */
@Composable
@ReadOnlyComposable
internal fun isChinese(language: LanguagePref): Boolean =
    when (language) {
        LanguagePref.ZH -> true
        LanguagePref.EN -> false
        LanguagePref.SYSTEM -> LocalKnotResources.current.configuration.locales[0].language == "zh"
    }

@Composable
@ReadOnlyComposable
internal fun knotName(k: Knot, language: LanguagePref): String = if (isChinese(language)) k.zh else k.en

@Composable
@ReadOnlyComposable
internal fun knotAlias(k: Knot, language: LanguagePref): String = if (isChinese(language)) k.aliasZh else k.aliasEn

@Composable
@ReadOnlyComposable
internal fun knotUse(k: Knot, language: LanguagePref): String = if (isChinese(language)) k.useZh else k.useEn

@Composable
@ReadOnlyComposable
internal fun knotCaution(k: Knot, language: LanguagePref): String = if (isChinese(language)) k.cautionZh else k.cautionEn

@Composable
@ReadOnlyComposable
internal fun knotCheck(k: Knot, language: LanguagePref): String = if (isChinese(language)) k.checkZh else k.checkEn

@Composable
@ReadOnlyComposable
internal fun stepText(step: KnotStep, language: LanguagePref): String = if (isChinese(language)) step.zh else step.en

/** The mnemonic, Chinese only (design i18n: no English mnemonic). */
@Composable
@ReadOnlyComposable
internal fun stepMnemonic(step: KnotStep, language: LanguagePref): String = if (isChinese(language)) step.mnemonicZh else ""

/** A 48 x 48 icon key for self-explaining icons (back, more, settings, play / pause). */
@Composable
internal fun Key(name: String, iconRes: Int, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val content = if (enabled) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelQuaternary
    IconButton(
        onClick = onClick,
        modifier = Modifier.probe(name, onClick),
        colors = IconButtonDefaults.iconButtonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = content),
        size = IconButtonDefaults.iconButtonSize(width = KeyWidth, height = KeyHeight),
        enabled = enabled,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = description, modifier = Modifier.size(24.dp), tint = content)
    }
}

/**
 * A worded button: capsule, 48 dp high, optional leading icon (profile B19: icons that are not obvious carry a word).
 * [primary] = the orange accent with a dark label (white on orange is only 3:1), [danger] = coral; else the tertiary fill.
 */
@Composable
internal fun WordButton(
    name: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    minWidth: Dp = 104.dp,
    iconRes: Int? = null,
) {
    val container =
        when {
            danger -> PicoTheme.colorScheme.error
            primary -> PicoTheme.colorScheme.interaction
            else -> PicoTheme.colorScheme.fillTertiary
        }
    val content =
        when {
            !enabled -> PicoTheme.colorScheme.labelQuaternary
            danger || primary -> KnotColors.OnAccent
            else -> PicoTheme.colorScheme.labelPrimary
        }
    Button(
        onClick = onClick,
        modifier = modifier.probe(name, onClick),
        size = ButtonDefaults.Regular.copy(height = 48.dp, minWidth = minWidth),
        shape = CircleShape,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        if (iconRes != null) {
            Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp), tint = content)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = text, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis, style = KnotType.title)
    }
}

/** A full-width list row (knot rows, menu items): title, a supporting line, trailing figure, optional leading picture. */
@Composable
internal fun RowButton(
    name: String,
    text: String,
    sub: String,
    trailing: String,
    onClick: () -> Unit,
    height: Dp = 56.dp,
    trailingColor: androidx.compose.ui.graphics.Color? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val content = PicoTheme.colorScheme.labelPrimary
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(height).probe(name, onClick),
        size = ButtonDefaults.Regular.copy(height = height, minWidth = 300.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = content),
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(text = text, color = content, style = KnotType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(text = sub, color = PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = trailing, color = trailingColor ?: PicoTheme.colorScheme.labelSecondary, style = KnotType.label, maxLines = 1, softWrap = false)
        }
    }
}

/** Title row of a panel: an optional leading key, the title, optional trailing keys. */
@Composable
internal fun TitleRow(title: String, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (leading != null) leading()
        Text(text = title, color = PicoTheme.colorScheme.labelPrimary, style = KnotType.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

/** A thin strip of colour with a dark label (the S09 controller hint: amber, design `palette.semantic`). */
@Composable
internal fun HintStrip(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, color = KnotColors.OnAccent, style = KnotType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
