package com.openjarvis.intelligence

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class RuntimePermissionRequestActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PERMISSION = "permission"
        private val ALLOWED = setOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_SMS
        )
    }

    private var requestedPermission: String? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        RuntimePermissionRequestBroker.complete(granted)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = intent.getStringExtra(EXTRA_PERMISSION)
        if (permission == null || permission !in ALLOWED) {
            RuntimePermissionRequestBroker.complete(false)
            finish()
            return
        }
        requestedPermission = permission
        permissionLauncher.launch(permission)
    }
}
