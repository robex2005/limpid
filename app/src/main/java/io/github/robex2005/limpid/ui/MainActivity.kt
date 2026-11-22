package io.github.robex2005.limpid.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.robex2005.limpid.R
import io.github.robex2005.limpid.core.Controller
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { HOME, APPS, INFO }

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LimpidTheme { LimpidRoot() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimpidRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val showSnack: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                Controller.refresh()
                delay(2000)
            }
        }
    }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (screen) {
                            Screen.HOME -> stringResource(R.string.app_name)
                            Screen.APPS -> stringResource(R.string.apps_title)
                            Screen.INFO -> stringResource(R.string.info_title)
                        }
                    )
                },
                navigationIcon = {
                    if (screen != Screen.HOME) IconButton(onClick = { screen = Screen.HOME }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    LanguageMenu()
                    if (screen == Screen.HOME) IconButton(onClick = { screen = Screen.INFO }) {
                        Icon(Icons.Filled.Info, stringResource(R.string.info_title))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (screen) {
                Screen.HOME -> HomeScreen(onOpen = { screen = it }, showSnack = showSnack)
                Screen.APPS -> AppsScreen()
                Screen.INFO -> InfoScreen(showSnack = showSnack)
            }
        }
    }
}

@Composable
private fun LanguageMenu() {
    var open by remember { mutableStateOf(false) }
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().take(2).uppercase().ifEmpty { "A" }
    Box {
        TextButton(onClick = { open = true }) { Text(if (current == "A") "RO/EN" else current) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("" to stringResource(R.string.lang_system), "ro" to "Română", "en" to "English").forEach { (tag, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    open = false
                    AppCompatDelegate.setApplicationLocales(
                        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                    )
                })
            }
        }
    }
}
