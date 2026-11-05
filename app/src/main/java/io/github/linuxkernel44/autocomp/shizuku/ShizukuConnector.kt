package io.github.linuxkernel44.autocomp.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import io.github.linuxkernel44.autocomp.BuildConfig
import io.github.linuxkernel44.autocomp.IShellService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, CONNECTING, READY }

class ShizukuConnector(private val context: Context) {
    private val _state = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val state: StateFlow<ShizukuState> = _state

    @Volatile var service: IShellService? = null
        private set
    private var binding = false

    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
        .processNameSuffix("shell").debuggable(BuildConfig.DEBUG).daemon(false).version(BuildConfig.VERSION_CODE)

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            binding = false
            service = IShellService.Stub.asInterface(binder)
            refresh()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; binding = false; refresh() }
    }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { service = null; binding = false; refresh() }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    fun isInstalled() = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    fun refresh() {
        _state.value = when {
            !Shizuku.pingBinder() -> if (isInstalled()) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
            Shizuku.isPreV11() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.NEEDS_PERMISSION
            service != null && service!!.asBinder().pingBinder() -> ShizukuState.READY
            else -> { bind(); ShizukuState.CONNECTING }
        }
    }

    private fun bind() {
        if (binding) return
        binding = true
        runCatching { Shizuku.bindUserService(args, conn) }.onFailure { binding = false }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(REQUEST_CODE)
    }

    companion object {
        const val PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 4401
    }
}
