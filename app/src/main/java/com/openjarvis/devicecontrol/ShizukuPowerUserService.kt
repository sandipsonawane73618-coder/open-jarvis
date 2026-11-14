package com.openjarvis.devicecontrol

import android.os.RemoteException
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit

/**
 * Runs under Shizuku's explicitly authorized shell/root identity.
 * It exposes only the single power-off operation; no arbitrary shell input.
 */
@Keep
class ShizukuPowerUserService : IJarvisPowerService.Stub() {

    override fun requestPowerOff(): String {
        val process = try {
            Runtime.getRuntime().exec(arrayOf("/system/bin/reboot", "-p"))
        } catch (error: Exception) {
            throw RemoteException("Could not start Android power-off command: ${error.message}")
        }

        // A successful power-off may terminate this process before it exits.
        // If it exits promptly with an error, report that instead of claiming success.
        val exited = try {
            process.waitFor(1500, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RemoteException("Power-off command was interrupted")
        }
        if (exited && process.exitValue() != 0) {
            val detail = runCatching {
                process.errorStream.bufferedReader().use { it.readText().take(300) }
            }.getOrDefault("")
            throw RemoteException(
                "Android rejected the power-off command (exit ${process.exitValue()}): ${detail.ifBlank { "permission denied or unsupported by this device" }}"
            )
        }
        return "power-off-request-accepted"
    }

    override fun destroy() {
        System.exit(0)
    }
}
