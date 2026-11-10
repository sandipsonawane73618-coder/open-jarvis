package com.openjarvis.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class CameraPermissionActivity : ComponentActivity() {
    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        getSharedPreferences("jarvis_camera_permission", MODE_PRIVATE).edit()
            .putBoolean("granted", granted).putBoolean("completed", true).apply()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            getSharedPreferences("jarvis_camera_permission", MODE_PRIVATE).edit()
                .putBoolean("granted", true).putBoolean("completed", true).apply()
            finish()
        } else requestCamera.launch(Manifest.permission.CAMERA)
    }
}
