package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepIntentParserTest {

    private fun p(text: String) = StepIntentParser.parse(text)

    @Test
    fun messages() {
        assertEquals(
            StepIntent.Message(null, "rahul", "kal milte hain"),
            p("Rahul ko message bhejo ki kal milte hain")
        )
        assertEquals(
            StepIntent.Message("whatsapp", "rahul", "kal milte hain"),
            p("WhatsApp par Rahul ko message bhejo ki kal milte hain")
        )
        assertEquals(
            StepIntent.Message("whatsapp", "rahul", "main late hoon"),
            p("Rahul ko WhatsApp pe message bhejo ki main late hoon")
        )
        assertEquals(
            StepIntent.Message(null, "rahul", "call me"),
            p("message bhejo Rahul ko ki call me")
        )
        assertEquals(
            StepIntent.Message("whatsapp", "rahul", "hello"),
            p("Rahul ko hello bhejo WhatsApp par")
        )
        assertEquals(
            StepIntent.Message("sms", "amit", "ghar aa jao"),
            p("SMS bhejo Amit ko ki ghar aa jao")
        )
        assertEquals(
            StepIntent.Message("whatsapp", "राहुल", "कल मिलते हैं"),
            p("राहुल को व्हाट्सएप पर मैसेज भेजो कि कल मिलते हैं")
        )
    }

    @Test
    fun alarms() {
        assertEquals(StepIntent.Alarm(6, 0), p("subah 6 baje ka alarm lagao"))
        assertEquals(StepIntent.Alarm(17, 30), p("shaam 5:30 baje alarm lagao"))
        assertEquals(StepIntent.Alarm(22, 0), p("raat 10 baje ka alarm"))
        assertEquals(StepIntent.Alarm(2, 0), p("raat 2 baje ka alarm"))
        assertEquals(StepIntent.Alarm(14, 0), p("dopahar 2 baje alarm"))
        assertEquals(StepIntent.Alarm(6, 0), p("6 am alarm"))
        assertEquals(StepIntent.Alarm(18, 0), p("6pm alarm"))
        assertEquals(StepIntent.Alarm(7, 30), p("saade 7 baje alarm lagao"))
        assertEquals(StepIntent.Alarm(6, 0), p("सुबह 6 बजे का अलार्म लगाओ"))
        assertEquals(StepIntent.Alarm(0, 0), p("raat 12 baje alarm"))
        assertNull(p("alarm lagao"))
    }

    @Test
    fun timers() {
        assertEquals(StepIntent.Timer(300), p("5 minute ka timer lagao"))
        assertEquals(StepIntent.Timer(3600), p("1 ghante ka timer"))
        assertEquals(StepIntent.Timer(150), p("2 minute 30 second timer"))
        assertNull(p("timer lagao"))
    }

    @Test
    fun torchAndVolume() {
        assertEquals(StepIntent.Torch(true), p("torch on karo"))
        assertEquals(StepIntent.Torch(false), p("torch band karo"))
        assertEquals(StepIntent.Torch(true), p("टॉर्च चालू करो"))
        assertEquals(StepIntent.Torch(false), p("flashlight off"))
        assertEquals(StepIntent.Volume(false), p("volume kam karo"))
        assertEquals(StepIntent.Volume(true), p("awaaz badhao"))
        assertEquals(StepIntent.Volume(true), p("volume up"))
        assertNull(p("volume"))
    }

    @Test
    fun navigation() {
        assertEquals(StepIntent.Navigate("pune station"), p("navigate to Pune station"))
        assertEquals(StepIntent.Navigate("pune"), p("Pune ka rasta batao"))
        assertEquals(StepIntent.Navigate("mumbai"), p("Mumbai tak navigation start karo"))
        assertNull(p("navigate"))
    }

    @Test
    fun screenReadCommandsAreRecognizedExplicitly() {
        assertEquals(StepIntent.ScreenRead, p("read screen"))
        assertEquals(StepIntent.ScreenRead, p("take screenshot"))
        assertEquals(StepIntent.ScreenRead, p("screen par kya hai"))
        assertEquals(StepIntent.ScreenRead, p("स्क्रीन पर क्या है बताओ"))
        assertEquals(StepIntent.ScreenRead, p("स्क्रीनशॉट लो"))
        assertNull(p("read a book"))
        assertNull(p("screen brightness"))
    }

    @Test
    fun otherCommandsAreIgnored() {
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("WhatsApp kholo"))
        assertEquals(StepIntent.AppLaunch("instagram"), p("open insta"))
        assertEquals(StepIntent.AppLaunch("instagram"), p("insta kholo"))
        assertEquals(StepIntent.AppLaunch("instagram"), p("open insta app"))
        assertEquals(StepIntent.AppLaunch("instagram"), p("open ig"))
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("open wa"))
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("open व्हाट्सएप"))
        assertEquals(StepIntent.AppLaunch("youtube"), p("open yt"))
        assertEquals(StepIntent.AppLaunch("facebook"), p("open fb"))
        assertEquals(StepIntent.AppLaunch("google pay"), p("open gpay"))
        assertEquals(StepIntent.AppLaunch("google pay"), p("open google pay"))
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("open whatsapp again"))
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("open whatsapp please"))
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("open whatsapp phir se"))
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("open whatsapp dobara"))
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("open whatsapp ek baar"))
        assertNull(p("Rahul ko call karo"))
        assertNull(p("YouTube par lofi search karo"))
        assertEquals(StepIntent.AppLaunch("chrome"), p("Chrome kholo"))
        assertNull(p("hello"))
        assertNull(p("open instagram and tell me what to do dear"))
        assertNull(p(""))
    }
}
