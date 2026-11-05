package io.github.linuxkernel44.autocomp.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.linuxkernel44.autocomp.R

@Composable
fun SetupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    ScreenScaffold(stringResource(R.string.setup_title), onBack) {
        Text(stringResource(R.string.setup_why))
        Text(stringResource(R.string.setup_step_install))
        Text(stringResource(if (Build.VERSION.SDK_INT >= 30) R.string.setup_step_start_11 else R.string.setup_step_start_old))
        Text(stringResource(R.string.setup_step_grant))
        Text(stringResource(R.string.setup_step_reboot), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_dev_settings))
        }
        Text(stringResource(R.string.setup_privacy), style = MaterialTheme.typography.bodySmall)
    }
}
