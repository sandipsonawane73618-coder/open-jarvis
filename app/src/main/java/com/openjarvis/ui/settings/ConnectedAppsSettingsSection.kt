package com.openjarvis.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
internal fun ConnectedAppsSettingsSection() {
    val context = LocalContext.current
    val permissionState = remember {
        mutableStateMapOf(
            Manifest.permission.READ_CONTACTS to (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED),
            Manifest.permission.READ_CALENDAR to (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED),
            Manifest.permission.READ_SMS to (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
        )
    }
    var notificationEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val notificationSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        notificationEnabled = isNotificationListenerEnabled(context)
    }
    val contactsRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionState[Manifest.permission.READ_CONTACTS] = it
    }
    val calendarRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionState[Manifest.permission.READ_CALENDAR] = it
    }
    val smsRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionState[Manifest.permission.READ_SMS] = it
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CONNECTED APPS", style = MaterialTheme.typography.titleMedium)
        Text("Read-only access. Jarvis never sends, replies, pays or deletes automatically.",
            style = MaterialTheme.typography.bodySmall)
        ConnectedAppRow("Notifications", if (notificationEnabled) "Enabled" else "Disabled") {
            notificationSettings.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        ConnectedAppRow("Contacts", status(permissionState[Manifest.permission.READ_CONTACTS])) {
            contactsRequest.launch(Manifest.permission.READ_CONTACTS)
        }
        ConnectedAppRow("Calendar", status(permissionState[Manifest.permission.READ_CALENDAR])) {
            calendarRequest.launch(Manifest.permission.READ_CALENDAR)
        }
        ConnectedAppRow("SMS", status(permissionState[Manifest.permission.READ_SMS])) {
            smsRequest.launch(Manifest.permission.READ_SMS)
        }
        listOf(
            "WhatsApp" to "com.whatsapp",
            "Telegram" to "org.telegram.messenger",
            "Instagram" to "com.instagram.android",
            "YouTube" to "com.google.android.youtube"
        ).forEach { (label, packageName) ->
            val installed = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
            ConnectedAppRow(label, if (installed) "Installed" else "Not installed") {
                val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) context.startActivity(launch)
            }
        }
    }
}

@Composable
private fun ConnectedAppRow(name: String, state: String, onEnable: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(state, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onEnable) { Text(if (state == "Enabled" || state == "Installed") "Open" else "Enable") }
        }
    }
}

private fun status(granted: Boolean?): String = if (granted == true) "Enabled" else "Disabled"

private fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
    ).orEmpty()
    val expected = android.content.ComponentName(context, com.openjarvis.intelligence.JarvisNotificationListener::class.java)
        .flattenToString()
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
