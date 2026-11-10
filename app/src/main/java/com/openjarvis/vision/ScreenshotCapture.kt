package com.openjarvis.vision

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import com.openjarvis.ui.ScreenCapturePermissionActivity

class ScreenshotCapture(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var isCapturing = false
    @Volatile private var permissionDenied = false
    private var captureWidth = 0
    private var captureHeight = 0
    private var captureDensityDpi = 0

    @Synchronized
    fun startCapture(): Boolean {
        if (isReady()) return true
        permissionDenied = false
        return runCatching {
            context.startActivity(
                Intent(context, ScreenCapturePermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            )
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun initialize(projection: MediaProjection): Boolean {
        permissionDenied = false
        releaseResources(stopProjection = true)
        mediaProjection = projection
        return try {
            val windowManager = context.getSystemService(WindowManager::class.java)
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)

            captureWidth = metrics.widthPixels
            captureHeight = metrics.heightPixels
            captureDensityDpi = metrics.densityDpi
            if (captureWidth <= 0 || captureHeight <= 0 || captureDensityDpi <= 0) {
                releaseResources(stopProjection = true)
                return false
            }

            handlerThread = HandlerThread("JarvisScreenshot").apply { start() }
            handler = Handler(handlerThread!!.looper)
            imageReader = ImageReader.newInstance(
                captureWidth,
                captureHeight,
                PixelFormat.RGBA_8888,
                2
            )

            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    synchronized(this@ScreenshotCapture) {
                        releaseResources(stopProjection = false)
                    }
                    context.stopService(Intent(context, ScreenCaptureService::class.java))
                }
            }
            projection.registerCallback(projectionCallback!!, handler)

            virtualDisplay = projection.createVirtualDisplay(
                "JarvisScreenshot",
                captureWidth,
                captureHeight,
                captureDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                handler
            )

            isCapturing = virtualDisplay != null
            if (!isCapturing) releaseResources(stopProjection = true)
            isCapturing
        } catch (e: Exception) {
            releaseResources(stopProjection = true)
            false
        }
    }

    @Synchronized
    fun capture(): Bitmap? {
        if (!isCapturing) return null
        val image = imageReader?.acquireLatestImage() ?: return null
        var paddedBitmap: Bitmap? = null
        try {
            val plane = image.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            if (pixelStride <= 0) return null

            val paddedWidth = paddedBitmapWidth(image.width, rowStride, pixelStride) ?: return null
            val rowPadding = rowStride - pixelStride * image.width
            val sourceBitmap = Bitmap.createBitmap(
                paddedWidth,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            paddedBitmap = sourceBitmap
            sourceBitmap.copyPixelsFromBuffer(buffer)

            if (rowPadding <= 0) {
                paddedBitmap = null
                return sourceBitmap
            }

            val cropped = Bitmap.createBitmap(sourceBitmap, 0, 0, image.width, image.height)
            sourceBitmap.recycle()
            paddedBitmap = null
            return cropped
        } catch (e: Exception) {
            return null
        } finally {
            paddedBitmap?.let { if (!it.isRecycled) it.recycle() }
            image.close()
        }
    }

    fun markPermissionDenied() { permissionDenied = true }

    fun wasPermissionDenied(): Boolean = permissionDenied

    @Synchronized
    fun isReady(): Boolean = isCapturing && virtualDisplay != null && imageReader != null

    @Synchronized
    fun release() {
        releaseResources(stopProjection = true)
    }

    private fun releaseResources(stopProjection: Boolean) {
        isCapturing = false
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.setOnImageAvailableListener(null, null) }
        runCatching { imageReader?.close() }
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null

        val activeProjection = mediaProjection
        val callback = projectionCallback
        mediaProjection = null
        projectionCallback = null
        if (stopProjection && activeProjection != null) {
            if (callback != null) runCatching { activeProjection.unregisterCallback(callback) }
            runCatching { activeProjection.stop() }
        }
    }

    companion object {
        internal fun paddedBitmapWidth(imageWidth: Int, rowStride: Int, pixelStride: Int): Int? {
            if (imageWidth <= 0 || rowStride <= 0 || pixelStride <= 0) return null
            val minimumRowBytes = imageWidth.toLong() * pixelStride
            if (rowStride.toLong() < minimumRowBytes) return null
            val rowPadding = rowStride.toLong() - minimumRowBytes
            return (imageWidth.toLong() + rowPadding / pixelStride).takeIf { it <= Int.MAX_VALUE }?.toInt()
        }

        @Volatile
        private var instance: ScreenshotCapture? = null

        fun getInstance(context: Context): ScreenshotCapture {
            return instance ?: synchronized(this) {
                instance ?: ScreenshotCapture(context.applicationContext).also { instance = it }
            }
        }
    }
}
