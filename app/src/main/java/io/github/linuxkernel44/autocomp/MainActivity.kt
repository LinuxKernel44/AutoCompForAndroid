package io.github.linuxkernel44.autocomp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import io.github.linuxkernel44.autocomp.ui.AutoCompUi

class MainActivity : ComponentActivity() {
    val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AutoCompUi(this) }
    }

    override fun onStart() { super.onStart(); app.engine.meterWanted = true }
    override fun onStop() { super.onStop(); app.engine.meterWanted = false }
}
