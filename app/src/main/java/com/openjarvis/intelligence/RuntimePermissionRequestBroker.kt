package com.openjarvis.intelligence

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal object RuntimePermissionRequestBroker {
    private val mutex = Mutex()
    @Volatile private var pending: CompletableDeferred<Boolean>? = null

    suspend fun ensure(context: Context, permission: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) return true
        return mutex.withLock {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) return@withLock true
            val result = CompletableDeferred<Boolean>()
            pending = result
            try {
                context.startActivity(
                    Intent(context, RuntimePermissionRequestActivity::class.java)
                        .putExtra(RuntimePermissionRequestActivity.EXTRA_PERMISSION, permission)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                withTimeoutOrNull(60_000L) { result.await() } ?: false
            } catch (_: Exception) {
                false
            } finally {
                pending = null
            }
        }
    }

    fun complete(granted: Boolean) {
        pending?.complete(granted)
    }
}
