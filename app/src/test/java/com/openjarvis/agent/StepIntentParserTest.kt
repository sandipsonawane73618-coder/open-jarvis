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
    fun marathiSendVerbsParseMessageStepsAndRequireACompleteMessage() {
        assertEquals(
            StepIntent.Message("whatsapp", "amit", "mi yetoy"),
            p("Amit la WhatsApp message dhad: mi yetoy")
        )
        assertEquals(
            StepIntent.Message(null, "rahul", "ho"),
            p("Rahul la message pathva: ho")
        )
        assertNull(p("dhad"))
    }

    @Test
    fun whatsappMessageUsesColonAsBodyDelimiter() {
        assertEquals(
            StepIntent.Message("whatsapp", "rahul", "main 10 minute mein pahunch raha hoon"),
            p("WhatsApp Rahul ko message bhejo: main 10 minute mein pahunch raha hoon")
        )
    }

    @Test
    fun recognizesRegionalMarathiWhatsAppMessageCommands() {
        assertEquals(
            StepIntent.Message("whatsapp", "राहुल", "मी येतोय"),
            p("राहुल ला व्हॉट्सॲप मेसेज धाड: मी येतोय")
        )
        assertEquals(
            StepIntent.Message("whatsapp", "amit", "mi yetoy"),
            p("Amit la WhatsApp message dhad: mi yetoy")
        )
    }

    @Test
    fun recognizesMaharashtraDialectAppLaunchPhrases() {
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("व्हॉट्सॲप उघडून दे"))
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("माका व्हॉट्सॲप उघडून दे"))
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("मले व्हॉट्सऍप उघड"))
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("WhatsApp ugadun de"))
    }

    @Test
    fun appCommandLlmFallbackIsCappedAtTenSeconds() {
        assertEquals(true, AppCommandLlmPolicy.isAppCommand("Instagram kholo aur Shah Rukh Khan search karo"))
        assertEquals(true, AppCommandLlmPolicy.isAppCommand("WhatsApp kholo"))
        assertEquals(false, AppCommandLlmPolicy.isAppCommand("aaj ki date kya hai"))
        assertEquals(10_000L, AppCommandLlmPolicy.timeoutMs("YouTube par song search karo", 45_000L))
        assertEquals(4_000L, AppCommandLlmPolicy.timeoutMs("WhatsApp kholo", 4_000L))
        assertEquals(45_000L, AppCommandLlmPolicy.timeoutMs("aaj ki date kya hai", 45_000L))
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
    fun hindiTrailingVerbLaunchesInstagramInsteadOfNavigation() {
        assertEquals(StepIntent.AppLaunch("instagram"), p("Instagram kholo"))
        assertEquals(StepIntent.AppLaunch("whatsapp"), p("WhatsApp kholo"))
        assertNull(p("Instagram kholo aur Shah Rukh Khan search karo"))
    }

    @Test
    fun navigation() {
        assertEquals(StepIntent.Navigate("pune station"), p("navigate to Pune station"))
        assertEquals(StepIntent.Navigate("pune"), p("Pune ka rasta batao"))
        assertEquals(StepIntent.Navigate("mumbai"), p("Mumbai tak navigation start karo"))
        assertNull(p("navigate"))
    }

    @Test
    fun screenshotCaptureAndScreenReadAreDistinctCommands() {
        assertEquals(StepIntent.Screenshot(), p("take screenshot"))
        assertEquals(StepIntent.Screenshot(), p("screenshot"))
        assertEquals(StepIntent.Screenshot(), p("स्क्रीनशॉट लो"))
        assertEquals(StepIntent.Screenshot(), p("screen ka photo lo"))
        assertEquals(StepIntent.Screenshot(), p("screen photo lo"))
        assertEquals(StepIntent.Screenshot(), p("screen shot lo"))
        assertEquals(StepIntent.Screenshot(), p("screen capture karo"))
        assertEquals(StepIntent.Screenshot(), p("photo of screen"))
        assertEquals(StepIntent.Screenshot(), p("screen cha photo kadh"))
        assertEquals(StepIntent.Screenshot(), p("स्क्रीनचा फोटो काढ"))
        assertEquals(StepIntent.Screenshot(), p("पडद्याचा फोटो काढा"))
        assertEquals(StepIntent.ScreenRead(), p("read screen"))
        assertEquals(StepIntent.ScreenRead(), p("screen par kya hai"))
        assertEquals(StepIntent.ScreenRead(), p("स्क्रीन पर क्या है बताओ"))
        assertEquals(StepIntent.ScreenRead(true), p("remember this screen"))
        assertEquals(StepIntent.ScreenRead(true), p("इस स्क्रीन को याद रखो"))
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
