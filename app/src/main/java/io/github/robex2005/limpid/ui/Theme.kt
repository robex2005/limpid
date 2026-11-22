package io.github.robex2005.limpid.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Dark = darkColorScheme(
    primary = Color(0xFF5EE0C4), onPrimary = Color(0xFF04201B),
    secondary = Color(0xFF7AA8FF), background = Color(0xFF0B0F14),
    surface = Color(0xFF0B0F14), surfaceContainer = Color(0xFF141A22),
    surfaceContainerHigh = Color(0xFF1B2330),
)
private val Light = lightColorScheme(primary = Color(0xFF00897B), secondary = Color(0xFF3D6FD6))

@Composable
fun LimpidTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
