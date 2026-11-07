package com.openjarvis.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import android.util.Base64
import java.security.SecureRandom

internal object BridgeTokenStore {
    private const val PREFS_NAME = "jarvis_bridge_auth"
    private const val TOKEN_KEY = "cli_token"
    private val lock = Any()

    fun getOrCreateToken(context: Context): String = synchronized(lock) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(TOKEN_KEY, null)?.takeIf { it.isNotBlank() } ?: run {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                .also { prefs.edit().putString(TOKEN_KEY, it).commit() }
        }
    }
}

/**
 * Exposes the ephemeral bridge port and its random token only to the app itself,
 * Termux, or Android's ADB shell UID. Other installed apps cannot obtain the token.
 */
class BridgeTokenProvider : ContentProvider() {

    companion object {
        private const val TERMUX_PACKAGE = "com.termux"
        private const val SHELL_UID = 2000

        internal fun isTrustedUid(
            callerUid: Int,
            appUid: Int,
            termuxUid: Int?,
            shellUid: Int = SHELL_UID
        ): Boolean = callerUid == appUid || callerUid == shellUid ||
            (termuxUid != null && callerUid == termuxUid)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val appContext = context ?: throw IllegalStateException("Provider context unavailable")
        val callerUid = Binder.getCallingUid()
        val termuxUid = runCatching {
            appContext.packageManager.getApplicationInfo(TERMUX_PACKAGE, 0).uid
        }.getOrNull()

        if (!isTrustedUid(callerUid, Process.myUid(), termuxUid)) {
            throw SecurityException("Caller is not authorized to read the Jarvis bridge configuration")
        }

        return MatrixCursor(arrayOf("port", "token")).apply {
            addRow(arrayOf(BridgeRuntimeConfig.port, BridgeTokenStore.getOrCreateToken(appContext)))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.openjarvis.bridgeconfig"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
