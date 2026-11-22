package io.github.robex2005.limpid.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.robex2005.limpid.LimpidApp
import io.github.robex2005.limpid.R
import io.github.robex2005.limpid.backend.ShizukuControl
import io.github.robex2005.limpid.backend.ShizukuControl.State
import io.github.robex2005.limpid.core.Controller
import io.github.robex2005.limpid.core.Mode
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit, showSnack: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = LimpidApp.prefs
    val mode by prefs.mode.collectAsStateWithLifecycle()
    val apps by prefs.apps.collectAsStateWithLifecycle()
    val state by Controller.state.collectAsStateWithLifecycle()
    val disabled by Controller.overlaysDisabled.collectAsStateWithLifecycle()
    val lastError by Controller.lastError.collectAsStateWithLifecycle()
    var a11y by remember { mutableStateOf(Controller.accessibilityEnabled(ctx)) }
    LaunchedEffect(Unit) { while (true) { a11y = Controller.accessibilityEnabled(ctx); delay(1500) } }
    val msgNotReady = stringResource(R.string.shizuku_not_ready)

    fun choose(m: Mode) {
        prefs.setMode(m)
        if (m != Mode.OFF && state != State.READY) showSnack(msgNotReady)
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Text(stringResource(R.string.status).uppercase(), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when (mode) {
                        Mode.OFF -> stringResource(R.string.mode_off)
                        Mode.GLOBAL -> stringResource(R.string.mode_global)
                        Mode.APPS -> stringResource(R.string.mode_apps)
                    },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                StatusRow(
                    state == State.READY,
                    stringResource(
                        when (state) {
                            State.NOT_INSTALLED -> R.string.shizuku_state_not_installed
                            State.NOT_RUNNING -> R.string.shizuku_state_not_running
                            State.NO_PERMISSION -> R.string.shizuku_state_no_permission
                            State.READY -> R.string.shizuku_state_ready
                        }
                    )
                )
                if (state == State.READY) {
                    StatusRow(disabled == true,
                        stringResource(when (disabled) {
                            true -> R.string.overlays_disabled
                            false -> R.string.overlays_normal
                            null -> R.string.overlays_unknown
                        }),
                        neutral = disabled != true)
                    lastError?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
                when (state) {
                    State.NOT_INSTALLED -> Button(onClick = { ShizukuControl.openShizuku(ctx) },
                        Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(stringResource(R.string.shizuku_install)) }
                    State.NOT_RUNNING -> Button(onClick = { ShizukuControl.openShizuku(ctx) },
                        Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(stringResource(R.string.shizuku_open)) }
                    State.NO_PERMISSION -> Button(onClick = { ShizukuControl.requestPermission() },
                        Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(stringResource(R.string.shizuku_allow)) }
                    State.READY -> Unit
                }
            }
        }

        ModeButton(stringResource(R.string.g_title), stringResource(R.string.g_desc), "◎", mode == Mode.GLOBAL) { choose(Mode.GLOBAL) }
        ModeButton(stringResource(R.string.a_title), stringResource(R.string.a_desc), "▦", mode == Mode.APPS) { choose(Mode.APPS) }
        Surface(
            onClick = { onOpen(Screen.APPS) }, shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ctx.resources.getQuantityString(R.plurals.apps_selected, apps.size, apps.size), Modifier.weight(1f))
                Text(stringResource(R.string.choose), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        if (mode == Mode.APPS && !a11y) {
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.a11y_off), color = MaterialTheme.colorScheme.onErrorContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state == State.READY || ShizukuControl.hasSecureSettings(ctx)) {
                            TextButton(onClick = { a11y = Controller.enableAccessibility(ctx) }) { Text(stringResource(R.string.a11y_auto)) }
                        }
                        TextButton(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text(stringResource(R.string.a11y_open)) }
                    }
                }
            }
        }
        ModeButton(stringResource(R.string.o_title), stringResource(R.string.o_desc), "⊘", mode == Mode.OFF, neutral = true) { choose(Mode.OFF) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusRow(ok: Boolean, text: String, neutral: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        val color = when {
            ok -> Color(0xFF5EE0A0)
            neutral -> MaterialTheme.colorScheme.outline
            else -> Color(0xFFFF6B7F)
        }
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(text, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModeButton(title: String, desc: String, glyph: String, selected: Boolean, neutral: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = if (neutral) cs.outline else cs.primary
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else cs.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(44.dp).background(if (selected) accent else cs.surfaceContainerHigh, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(glyph, color = if (selected) cs.onPrimary else cs.onSurface, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, color = cs.onSurface, style = MaterialTheme.typography.titleMedium)
                Text(desc, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
