package com.openjarvis.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenshotCaptureTest {

    @Test
    fun `padded width is unchanged when image rows have no padding`() {
        assertEquals(1080, ScreenshotCapture.paddedBitmapWidth(1080, 4320, 4))
    }

    @Test
    fun `padded width accounts for row stride padding`() {
        assertEquals(1088, ScreenshotCapture.paddedBitmapWidth(1080, 4352, 4))
    }

    @Test
    fun `invalid bitmap dimensions and strides are rejected`() {
        assertNull(ScreenshotCapture.paddedBitmapWidth(0, 4320, 4))
        assertNull(ScreenshotCapture.paddedBitmapWidth(1080, 0, 4))
        assertNull(ScreenshotCapture.paddedBitmapWidth(1080, 4320, 0))
        assertNull(ScreenshotCapture.paddedBitmapWidth(1080, 4000, 4))
    }
}
