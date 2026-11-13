package com.openjarvis.devicecontrol

import androidx.activity.ComponentActivity
import com.openjarvis.BuildConfig
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Finishes the voice-confirmed power-off request using Shizuku.
 * The user must first approve Jarvis in Shizuku; no arbitrary shell command is accepted.
 */
class PowerOffActivity : ComponentActivity() {

    private var serviceBound = false
    private var requestListenerRegistered = false
    private var binderListenerRegistered = false
    private var powerFlowStarted = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        beginPowerFlow()
    }

    private val binderTimeout = Runnable {
        if (!powerFlowStarted && !Shizuku.pingBinder()) {
            finishWithMessage("Shizuku service start nahi hai. Shizuku app kholo, Wireless debugging se Start karo, phir Jarvis command dobara bolo.")
            packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let { launch ->
                runCatching { startActivity(launch) }
            }
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(this, ShizukuPowerUserService::class.java)
        )
            .daemon(false)
            .processNameSuffix("power")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != REQUEST_POWER_PERMISSION) return@OnRequestPermissionResultListener
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            bindPowerService()
        } else {
            finishWithMessage("Jarvis ko Shizuku permission nahi mili. Shizuku app mein permission allow karo.")
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (!binder.pingBinder()) {
                finishWithMessage("Shizuku power service connect nahi hua.")
                return
            }
            val service = IJarvisPowerService.Stub.asInterface(binder)
            lifecycleScope.launch(Dispatchers.IO) {
                val result = runCatching { service.requestPowerOff() }
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { response ->
                            if (response == "power-off-request-accepted") {
                                Toast.makeText(
                                    this@PowerOffActivity,
                                    "Phone power off ho raha hai.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(this@PowerOffActivity, "Power-off request verify nahi hua.", Toast.LENGTH_LONG).show()
                            }
                            finish()
                        },
                        onFailure = { error ->
                            finishWithMessage(
                                error.message?.takeIf { it.isNotBlank() }
                                    ?: "Is Android version par power off permission available nahi hai."
                            )
                        }
                    )
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        binderListenerRegistered = true
        if (Shizuku.pingBinder()) {
            beginPowerFlow()
        } else {
            mainHandler.postDelayed(binderTimeout, BINDER_WAIT_TIMEOUT_MS)
        }
    }

    private fun beginPowerFlow() {
        if (powerFlowStarted || isFinishing || !Shizuku.pingBinder()) return
        powerFlowStarted = true
        mainHandler.removeCallbacks(binderTimeout)
        runCatching {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < MIN_SHIZUKU_API) {
                finishWithMessage("Jarvis power control ke liye Shizuku API 11 ya newer chahiye.")
                return
            }
            when (Shizuku.checkSelfPermission()) {
                PackageManager.PERMISSION_GRANTED -> bindPowerService()
                else -> {
                    if (Shizuku.shouldShowRequestPermissionRationale()) {
                        finishWithMessage("Shizuku app mein Open Jarvis ki permission allow karo, phir command dobara bolo.")
                    } else {
                        Shizuku.addRequestPermissionResultListener(permissionListener)
                        requestListenerRegistered = true
                        Shizuku.requestPermission(REQUEST_POWER_PERMISSION)
                    }
                }
            }
        }.onFailure { error ->
            finishWithMessage(error.message ?: "Shizuku permission check failed.")
        }
    }

    private fun bindPowerService() {
        if (isFinishing || serviceBound) return
        runCatching {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            serviceBound = true
        }.onFailure { error ->
            finishWithMessage(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "Shizuku power service start nahi ho saka."
            )
        }
    }

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(binderTimeout)
        if (binderListenerRegistered) {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            binderListenerRegistered = false
        }
        if (requestListenerRegistered) {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            requestListenerRegistered = false
        }
        if (serviceBound) {
            runCatching { Shizuku.unbindUserService(userServiceArgs, serviceConnection, true) }
            serviceBound = false
        }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_POWER_PERMISSION = 7401
        private const val MIN_SHIZUKU_API = 11
        private const val BINDER_WAIT_TIMEOUT_MS = 5_000L
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
