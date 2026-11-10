package com.openjarvis.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class ContactPermissionActivity : ComponentActivity() {
    private var requestCallPhone = false

    private val requestCall = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        getSharedPreferences("jarvis_contact_permission", MODE_PRIVATE).edit()
            .putBoolean("call_granted", granted)
            .putBoolean("call_completed", true)
            .apply()
        finish()
    }

    private val requestContacts = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        getSharedPreferences("jarvis_contact_permission", MODE_PRIVATE).edit()
            .putBoolean("granted", granted)
            .putBoolean("completed", true)
            .apply()
        if (granted) requestCallPermissionIfNeeded() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCallPhone = intent.getBooleanExtra(EXTRA_REQUEST_CALL_PHONE, false)
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            getSharedPreferences("jarvis_contact_permission", MODE_PRIVATE).edit()
                .putBoolean("granted", true)
                .putBoolean("completed", true)
                .apply()
            requestCallPermissionIfNeeded()
        } else {
            requestContacts.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun requestCallPermissionIfNeeded() {
        if (!requestCallPhone) {
            finish()
            return
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            getSharedPreferences("jarvis_contact_permission", MODE_PRIVATE).edit()
                .putBoolean("call_granted", true)
                .putBoolean("call_completed", true)
                .apply()
            finish()
        } else {
            getSharedPreferences("jarvis_contact_permission", MODE_PRIVATE).edit()
                .putBoolean("call_completed", false)
                .putBoolean("call_granted", false)
                .apply()
            requestCall.launch(Manifest.permission.CALL_PHONE)
        }
    }

    companion object {
        const val EXTRA_REQUEST_CALL_PHONE = "request_call_phone"
    }
}
