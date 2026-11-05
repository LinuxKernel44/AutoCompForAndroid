package io.github.linuxkernel44.autocomp

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.linuxkernel44.autocomp.spike.ProbeService
import io.github.linuxkernel44.autocomp.spike.SpikeCompressor
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var probe: IProbeService? = null
    private var log by mutableStateOf("")

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) {
            probe = IProbeService.Stub.asInterface(b); log += "probe bound\n"
        }
        override fun onServiceDisconnected(n: ComponentName) { probe = null }
    }
    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, ProbeService::class.java.name))
            .processNameSuffix("probe").debuggable(true).version(1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener { _, r -> log += "perm result=$r\n" }
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AutoComp – phase 0 spike")
                    Button(onClick = {
                        if (!Shizuku.pingBinder()) log += "Shizuku not running\n"
                        else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(1)
                        else { Shizuku.bindUserService(args, conn); log += "binding\n" }
                    }) { Text("Shizuku connect") }
                    Button(onClick = { log += (probe?.exec("dumpsys media.audio_flinger | grep -i -E 'session|active' | head -60") ?: "not bound") + "\n" }) { Text("Probe flinger") }
                    Button(onClick = { SpikeCompressor.releaseAll(); log += "released all\n" }) { Text("Release all") }
                    Text(log)
                }
            }
        }
    }
}
