package io.embodit.knots.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme

/**
 * The app theme: the 16-role colour scheme of the design (four fixed roles, twelve forwarded from the system Vibrant
 * scheme, design `palette.colorScheme` = inherit) and the Noto Sans type family. Text on glass uses `labelPrimary`.
 */
@Composable
fun KnotsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val system = systemColorScheme(context)
    val colors =
        remember(system) {
            system.copy(
                fillPrimary = system.fillPrimary,
                fillSecondary = system.fillSecondary,
                fillTertiary = system.fillTertiary,
                fillLight = system.fillLight,
                labelPrimaryLight = system.labelPrimaryLight,
                labelPrimary = system.labelPrimary,
                // measured on the emulator glass: the system secondary label was 3.9:1; 80 % of the primary label clears 4.5:1 (profile D13)
                labelSecondary = system.labelPrimary.copy(alpha = 0.8f),
                labelTertiary = system.labelTertiary,
                labelQuaternary = system.labelQuaternary,
                lightenHover = system.lightenHover,
                lightenPressed = system.lightenPressed,
                error = KnotColors.Error,
                alert = KnotColors.Alert,
                passable = KnotColors.Passable,
                interaction = KnotColors.Interaction,
                dividerLine = system.dividerLine,
            )
        }
    val family = rememberKnotFontFamily()
    val base = PicoTheme.typography
    val typography = remember(base, family) { base.withFamily(family) }
    PicoTheme(colorScheme = colors, typography = typography, content = content)
}
