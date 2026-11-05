package io.github.linuxkernel44.autocomp.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.linuxkernel44.autocomp.MainActivity
import io.github.linuxkernel44.autocomp.R
import io.github.linuxkernel44.autocomp.app
import io.github.linuxkernel44.autocomp.service.EngineService
import io.github.linuxkernel44.autocomp.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, ADVANCED, APPS, SETUP }

@Composable
fun AutoCompUi(activity: MainActivity) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(activity) else dynamicLightColorScheme(activity)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize()) {
            var screen by remember { mutableStateOf(Screen.HOME) }
            BackHandler(screen != Screen.HOME) { screen = Screen.HOME }
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                when (screen) {
                    Screen.HOME -> HomeScreen(activity, onNavigate = { screen = it })
                    Screen.ADVANCED -> AdvancedScreen(onBack = { screen = Screen.HOME })
                    Screen.APPS -> AppsScreen(onBack = { screen = Screen.HOME })
                    Screen.SETUP -> SetupScreen(onBack = { screen = Screen.HOME })
                }
            }
        }
    }
}

@Composable
fun ScreenScaffold(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onBack != null) TextButton(onClick = onBack) { Text("‹ " + stringResource(R.string.back)) }
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        content()
    }
}

@Composable
private fun HomeScreen(activity: MainActivity, onNavigate: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app
    val settings by app.settings.flow.collectAsState()
    val shizuku by app.shizuku.state.collectAsState()
    val engine by app.engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    var batteryOk by remember { mutableStateOf(ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)) }

    // Level meter needs RECORD_AUDIO; grant it silently through Shizuku once connected.
    LaunchedEffect(shizuku) {
        app.shizuku.refresh()
        if (shizuku == ShizukuState.READY &&
            ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) withContext(Dispatchers.IO) { runCatching { app.shizuku.service?.grantRecordAudio() } }
    }

    ScreenScaffold(stringResource(R.string.app_name), null) {
        ShizukuCard(shizuku, onSetup = { onNavigate(Screen.SETUP) })

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.enable_label), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.enabled, onCheckedChange = { on ->
                    if (on) { activity.ensureNotificationPermission(); EngineService.start(ctx) } else EngineService.stop(ctx)
                })
            }
        }

        PresetSelector(settings.preset) { p -> app.settings.update { it.copy(preset = p) } }

        Text(stringResource(R.string.active_apps), style = MaterialTheme.typography.titleMedium)
        if (engine.apps.isEmpty()) Text(stringResource(R.string.no_active_apps), style = MaterialTheme.typography.bodyMedium)
        engine.apps.forEach { ActiveAppRow(it) }

        Button(onClick = { onNavigate(Screen.ADVANCED) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_advanced)) }
        OutlinedButton(onClick = { onNavigate(Screen.APPS) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_apps)) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.start_on_boot), Modifier.weight(1f))
            Switch(settings.startOnBoot, { v -> app.settings.update { it.copy(startOnBoot = v) } })
        }
        if (batteryOk) Text(stringResource(R.string.battery_ok), style = MaterialTheme.typography.bodySmall)
        else OutlinedButton(onClick = {
            ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            scope.launch { kotlinx.coroutines.delay(1000); batteryOk = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
        }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_battery)) }
    }
}

@Composable
private fun ShizukuCard(state: ShizukuState, onSetup: () -> Unit) {
    val ctx = LocalContext.current
    val ok = state == ShizukuState.READY
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(when (state) {
                ShizukuState.NOT_INSTALLED -> R.string.shizuku_not_installed
                ShizukuState.NOT_RUNNING -> R.string.shizuku_not_running
                ShizukuState.NEEDS_PERMISSION -> R.string.shizuku_needs_permission
                ShizukuState.CONNECTING -> R.string.shizuku_connecting
                ShizukuState.READY -> R.string.shizuku_ready
            }), style = MaterialTheme.typography.titleSmall)
            when (state) {
                ShizukuState.NOT_INSTALLED -> Button(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases/latest")))
                }) { Text(stringResource(R.string.btn_get_shizuku)) }
                ShizukuState.NOT_RUNNING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        ctx.packageManager.getLaunchIntentForPackage(io.github.linuxkernel44.autocomp.shizuku.ShizukuConnector.PACKAGE)?.let { ctx.startActivity(it) }
                    }) { Text(stringResource(R.string.btn_open_shizuku)) }
                    OutlinedButton(onClick = onSetup) { Text(stringResource(R.string.btn_setup_guide)) }
                }
                ShizukuState.NEEDS_PERMISSION -> Button(onClick = { ctx.app.shizuku.requestPermission() }) { Text(stringResource(R.string.btn_grant)) }
                else -> {}
            }
        }
    }
}

@Composable
fun PresetSelector(selected: io.github.linuxkernel44.autocomp.core.PresetId, onSelect: (io.github.linuxkernel44.autocomp.core.PresetId) -> Unit) {
    Text(stringResource(R.string.presets_title), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        io.github.linuxkernel44.autocomp.core.PresetId.entries.forEach { p ->
            FilterChip(selected == p, { onSelect(p) }, label = { Text(presetName(p), maxLines = 1) })
        }
    }
}

@Composable
fun presetName(p: io.github.linuxkernel44.autocomp.core.PresetId) = stringResource(when (p) {
    io.github.linuxkernel44.autocomp.core.PresetId.SOFT -> R.string.preset_soft
    io.github.linuxkernel44.autocomp.core.PresetId.NORMAL -> R.string.preset_normal
    io.github.linuxkernel44.autocomp.core.PresetId.STRONG -> R.string.preset_strong
    io.github.linuxkernel44.autocomp.core.PresetId.NIGHT -> R.string.preset_night
    io.github.linuxkernel44.autocomp.core.PresetId.CUSTOM -> R.string.preset_custom
})

@Composable
private fun ActiveAppRow(a: io.github.linuxkernel44.autocomp.core.ActiveApp) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                val status = when {
                    a.error != null -> stringResource(R.string.status_error, a.error)
                    !a.compressed -> stringResource(R.string.status_bypassed)
                    !a.playing -> stringResource(R.string.status_paused)
                    else -> stringResource(R.string.status_compressing)
                }
                Text(status, style = MaterialTheme.typography.labelMedium)
            }
            a.levelDb?.let { db ->
                LinearProgressIndicator(progress = { ((db + 60f) / 60f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Text(stringResource(R.string.level_format, kotlin.math.round(db).let { if (it == 0f) 0f else it }), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
