package com.openjarvis.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.openjarvis.vision.ScreenCaptureService
import com.openjarvis.vision.ScreenshotCapture

class ScreenCapturePermissionActivity : ComponentActivity() {

    private val requestScreenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val permissionData = result.data
        if (result.resultCode == Activity.RESULT_OK && permissionData != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, permissionData)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            ScreenshotCapture.getInstance(this).markPermissionDenied()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MediaProjectionManager::class.java)
        requestScreenCapture.launch(manager.createScreenCaptureIntent())
    }
}
