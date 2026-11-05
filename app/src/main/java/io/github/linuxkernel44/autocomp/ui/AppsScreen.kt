package io.github.linuxkernel44.autocomp.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.linuxkernel44.autocomp.R
import io.github.linuxkernel44.autocomp.app
import io.github.linuxkernel44.autocomp.core.PresetId
import io.github.linuxkernel44.autocomp.core.ProfileRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(val pkg: String, val label: String)

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = ctx.app.settings
    val settings by store.flow.collectAsState()
    val engine by ctx.app.engine.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var all by remember { mutableStateOf(emptyList<AppEntry>()) }
    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        }
    }
    val playing = engine.apps.map { it.pkg }.toSet()
    val filtered = all.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
        .sortedByDescending { it.pkg in playing }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ " + stringResource(R.string.back)) }
            Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineSmall)
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.apps_search)) })
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.pkg }) { a ->
                AppRow(a, settings.profiles[a.pkg] ?: ProfileRef.DEFAULT, a.pkg in playing) { ref ->
                    store.update { s -> s.copy(profiles = if (ref == ProfileRef.DEFAULT) s.profiles - a.pkg else s.profiles + (a.pkg to ref)) }
                }
            }
        }
    }
}

@Composable
private fun AppRow(a: AppEntry, ref: String, playing: Boolean, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, a.pkg) {
        value = withContext(Dispatchers.IO) { runCatching { ctx.packageManager.getApplicationIcon(a.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        icon?.let { Image(it, null, Modifier.size(40.dp)) } ?: Spacer(Modifier.size(40.dp))
        Column(Modifier.weight(1f)) {
            Text(a.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (playing) Text(stringResource(R.string.apps_playing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Box {
            OutlinedButton(onClick = { open = true }) { Text(profileName(ref), maxLines = 1) }
            DropdownMenu(open, { open = false }) {
                (listOf(ProfileRef.DEFAULT, ProfileRef.BYPASS) + PresetId.entries.map { it.name }).forEach { r ->
                    DropdownMenuItem(text = { Text(profileName(r)) }, onClick = { onChange(r); open = false })
                }
            }
        }
    }
}

@Composable
private fun profileName(ref: String): String = when (ref) {
    ProfileRef.DEFAULT -> stringResource(R.string.profile_default)
    ProfileRef.BYPASS -> stringResource(R.string.profile_bypass)
    else -> presetName(runCatching { PresetId.valueOf(ref) }.getOrDefault(PresetId.NORMAL))
}
