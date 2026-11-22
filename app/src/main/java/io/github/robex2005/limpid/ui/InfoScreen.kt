package io.github.robex2005.limpid.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.robex2005.limpid.BuildConfig
import io.github.robex2005.limpid.R
import io.github.robex2005.limpid.backend.ShizukuControl

@Composable
fun InfoScreen() {
    val ctx = LocalContext.current
    fun open(intent: Intent) = runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Section(stringResource(R.string.how_title)) {
            stringArrayResource(R.array.steps).forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Card(shape = CircleShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Text("${i + 1}", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text(step, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
        }
        OutlinedButton(onClick = { ShizukuControl.openShizuku(ctx) }, Modifier.fillMaxWidth()) {
            Text(stringResource(if (ShizukuControl.isInstalled(ctx)) R.string.shizuku_open else R.string.shizuku_install))
        }
        OutlinedButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_dev_options))
        }
        OutlinedButton(onClick = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.a11y_open))
        }

        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer)) {
            Text(stringResource(R.string.note_reboot), Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
        }

        Section(stringResource(R.string.why_title)) { Body(stringResource(R.string.why_text)) }
        Section(stringResource(R.string.batt_title)) { Body(stringResource(R.string.batt_text)) }
        Section(stringResource(R.string.restricted_title)) { Body(stringResource(R.string.restricted_text)) }
        OutlinedButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/robex2005/limpid"))) },
            Modifier.fillMaxWidth()) { Text("GitHub") }
        Text("Limpid ${BuildConfig.VERSION_NAME} · GPL-3.0",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
