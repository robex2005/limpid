package io.github.robex2005.limpid.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.robex2005.limpid.BuildConfig
import io.github.robex2005.limpid.LimpidApp
import io.github.robex2005.limpid.R
import io.github.robex2005.limpid.adb.Bootstrapper
import io.github.robex2005.limpid.adb.PairingService
import io.github.robex2005.limpid.adb.ShizukuBackend
import io.github.robex2005.limpid.core.Backend
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku
import io.github.robex2005.limpid.adb.PairingStatus
import io.github.robex2005.limpid.core.Controller
import kotlinx.coroutines.launch

@Composable
fun InfoScreen(showSnack: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = LimpidApp.prefs
    val status by PairingService.status.collectAsStateWithLifecycle()
    val paired by prefs.paired.collectAsStateWithLifecycle()
    val helperUp by Controller.helperUp.collectAsStateWithLifecycle()
    val autoOff by prefs.autoDisableWifiDebug.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    val foundPort by PairingService.port.collectAsStateWithLifecycle()
    val lastSeen by PairingService.lastSeen.collectAsStateWithLifecycle()
    var pairPort by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        PairingService.start(ctx)
    }
    fun startPairing() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else PairingService.start(ctx)
    }
    fun open(intent: Intent) = runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    when (val s = status) {
                        PairingStatus.Idle -> stringResource(if (paired) R.string.pair_state_paired else R.string.pair_state_idle)
                        PairingStatus.Searching -> stringResource(R.string.pair_notif_searching)
                        PairingStatus.Ready -> stringResource(R.string.pair_notif_ready)
                        PairingStatus.Working -> stringResource(R.string.pair_notif_working)
                        PairingStatus.Done -> stringResource(R.string.pair_notif_done)
                        is PairingStatus.Failed -> stringResource(R.string.pair_notif_failed, s.message)
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when (status) {
                    PairingStatus.Idle, PairingStatus.Done -> Button(onClick = { startPairing() }, Modifier.fillMaxWidth()) {
                        Text(stringResource(if (paired) R.string.pair_again else R.string.pair_start))
                    }
                    else -> {
                        OutlinedTextField(
                            value = code, onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                            singleLine = true, placeholder = { Text("······", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 6.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pairPort, onValueChange = { v -> pairPort = v.filter { it.isDigit() }.take(5) },
                                singleLine = true, label = { Text(stringResource(R.string.pair_port_label)) },
                                placeholder = { Text(foundPort?.toString() ?: "") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = connectPort, onValueChange = { v -> connectPort = v.filter { it.isDigit() }.take(5) },
                                singleLine = true, label = { Text(stringResource(R.string.connect_port_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(stringResource(R.string.pair_split_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        lastSeen?.let {
                            Text("mDNS: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        val portForPair = pairPort.toIntOrNull() ?: foundPort
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    Bootstrapper.manualConnectPort = connectPort.toIntOrNull()
                                    PairingService.submitCode(ctx, "${portForPair ?: ""} $code")
                                },
                                enabled = code.length == 6 && portForPair != null && status != PairingStatus.Working,
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.pair_btn)) }
                            OutlinedButton(onClick = { PairingService.stop(ctx) }) { Text(stringResource(R.string.cancel)) }
                        }
                    }
                }
                OutlinedButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.open_dev_options))
                }
            }
        }

        ShizukuCard(showSnack)

        Section(stringResource(R.string.how_title)) {
            stringArrayResource(R.array.steps).forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(
                        Modifier.size(26.dp).padding(0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(shape = CircleShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Text("${i + 1}", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(step, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
        }

        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer)) {
            Text(stringResource(R.string.note_reboot), Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
        }

        Section(stringResource(R.string.settings_title)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_off_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.auto_off_desc), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoOff, onCheckedChange = { prefs.setAutoDisableWifiDebug(it) })
            }
            val pm = ctx.getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                OutlinedButton(onClick = {
                    open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.battery_btn)) }
            }
            OutlinedButton(onClick = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                Modifier.fillMaxWidth()) { Text(stringResource(R.string.a11y_open)) }
            if (helperUp) {
                OutlinedButton(onClick = { scope.launch { Controller.stopHelper() } }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.stop_service))
                }
            }
        }

        Section(stringResource(R.string.why_title)) { Body(stringResource(R.string.why_text)) }
        Section(stringResource(R.string.batt_title)) { Body(stringResource(R.string.batt_text)) }
        Section(stringResource(R.string.restricted_title)) { Body(stringResource(R.string.restricted_text)) }
        Text("Limpid ${BuildConfig.VERSION_NAME} · GPL-3.0 · github.com/robex2005/limpid",
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

@Composable
private fun ShizukuCard(showSnack: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = LimpidApp.prefs
    val backend by prefs.backend.collectAsStateWithLifecycle()
    var state by remember { mutableStateOf(ShizukuBackend.state()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val msgOk = stringResource(R.string.shizuku_done)
    val msgFail = stringResource(R.string.connect_failed)

    fun bootstrap() {
        busy = true
        prefs.setBackend(Backend.SHIZUKU)
        scope.launch {
            val r = io.github.robex2005.limpid.adb.Bootstrapper.run(ctx)
            busy = false
            showSnack(if (r.isSuccess) msgOk else msgFail + " " + (r.exceptionOrNull()?.message ?: ""))
        }
    }

    LaunchedEffect(Unit) { while (true) { state = ShizukuBackend.state(); delay(1500) } }
    DisposableEffect(Unit) {
        val l = Shizuku.OnRequestPermissionResultListener { code, result ->
            if (code == ShizukuBackend.REQUEST_CODE && result == PackageManager.PERMISSION_GRANTED) bootstrap()
        }
        runCatching { Shizuku.addRequestPermissionResultListener(l) }
        onDispose { runCatching { Shizuku.removeRequestPermissionResultListener(l) } }
    }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.shizuku_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    when (state) {
                        ShizukuBackend.State.NOT_RUNNING -> R.string.shizuku_not_running
                        ShizukuBackend.State.NO_PERMISSION -> R.string.shizuku_no_permission
                        ShizukuBackend.State.READY -> if (backend == Backend.SHIZUKU) R.string.shizuku_active else R.string.shizuku_ready
                    }
                ),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state != ShizukuBackend.State.NOT_RUNNING && backend != Backend.SHIZUKU) {
                Button(
                    onClick = { if (state == ShizukuBackend.State.READY) bootstrap() else ShizukuBackend.requestPermission() },
                    enabled = !busy, modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.shizuku_use)) }
            }
            if (backend == Backend.SHIZUKU) {
                OutlinedButton(onClick = { prefs.setBackend(Backend.ADB) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shizuku_back_to_adb))
                }
            }
        }
    }
}
