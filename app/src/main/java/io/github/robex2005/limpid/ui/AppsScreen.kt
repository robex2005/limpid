package io.github.robex2005.limpid.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.robex2005.limpid.LimpidApp
import io.github.robex2005.limpid.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap?)

@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    val prefs = LimpidApp.prefs
    val selected by prefs.apps.collectAsStateWithLifecycle()
    var all by remember { mutableStateOf<List<AppItem>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map { ri ->
                    AppItem(
                        ri.loadLabel(pm).toString(),
                        ri.activityInfo.packageName,
                        runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text(stringResource(R.string.search)) },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        val list = all
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val q = query.trim().lowercase()
        val shown = list.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
            .sortedByDescending { it.pkg in selected }
        LazyColumn {
            items(shown, key = { it.pkg }) { app ->
                Row(
                    Modifier.fillMaxWidth().clickable { prefs.toggleApp(app.pkg) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    app.icon?.let { Image(it, null, Modifier.size(40.dp)) } ?: Box(Modifier.size(40.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(checked = app.pkg in selected, onCheckedChange = { prefs.toggleApp(app.pkg) })
                }
            }
        }
    }
}
