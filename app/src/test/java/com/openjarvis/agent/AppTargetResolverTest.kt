package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTargetResolverTest {
    private val apps = listOf(
        InstalledAppTarget("Chrome", "com.android.chrome"),
        InstalledAppTarget("WhatsApp", "com.whatsapp"),
        InstalledAppTarget("YouTube", "com.google.android.youtube"),
        InstalledAppTarget("Settings", "com.android.settings"),
        InstalledAppTarget("Instagram", "com.instagram.android"),
        InstalledAppTarget("Facebook", "com.facebook.katana"),
        InstalledAppTarget("Google Pay", "com.google.android.apps.nbu.paisa.user")
    )

    @Test fun resolvesAppsExplicitlyNamedInCommandInSpokenOrder() {
        val result = AppTargetResolver.findMentionedApps("Open Chrome, then WhatsApp kholo", apps)
        assertEquals(listOf("com.android.chrome", "com.whatsapp"), result?.map { it.packageName })
    }

    @Test fun resolvesDevanagariSpokenAppNamesAgainstLatinInstalledLabels() {
        assertEquals("com.google.android.youtube", AppTargetResolver.findMentionedApps("ओपन यूट्यूब", apps)?.singleOrNull()?.packageName)
        assertEquals("com.whatsapp", AppTargetResolver.findMentionedApps("व्हाट्सएप खोलो", apps)?.singleOrNull()?.packageName)
        assertTrue(VoiceTextNormalizer.appLabelsMatch("YouTube", "यूट्यूब"))
        assertTrue(VoiceTextNormalizer.appLabelsMatch("WhatsApp", "व्हाट्सएप"))
    }

    @Test fun matchesHindiAndMarathiFriendlyAliases() {
        val result = AppTargetResolver.findMentionedApps("व्हॉट्सअॅप kholo", apps)
        assertEquals("com.whatsapp", result?.singleOrNull()?.packageName)
    }

    @Test fun resolvesShortAliasesByInstalledPackage() {
        val commands = mapOf(
            "insta kholo" to "com.instagram.android",
            "wa kholo" to "com.whatsapp",
            "yt kholo" to "com.google.android.youtube",
            "fb kholo" to "com.facebook.katana",
            "gpay kholo" to "com.google.android.apps.nbu.paisa.user"
        )
        commands.forEach { (command, expected) ->
            assertEquals(command, expected, AppTargetResolver.findMentionedApps(command, apps)?.singleOrNull()?.packageName)
        }
    }

    @Test fun prefersInstalledYouTubeModOverOfficialYouTube() {
        val installed = listOf(
            InstalledAppTarget("YouTube", "com.google.android.youtube"),
            InstalledAppTarget("YouTube ReVanced", "app.revanced.android.youtube")
        )
        val result = AppTargetResolver.findMentionedApps("open youtube", installed)
        assertEquals("app.revanced.android.youtube", result?.singleOrNull()?.packageName)
    }

    @Test fun excludesYouTubeCompanionAndDownloaderAppsFromModCandidates() {
        val installed = listOf(
            InstalledAppTarget("YouTube Music", "com.google.android.apps.youtube.music"),
            InstalledAppTarget("YouTube Studio", "com.google.android.apps.youtube.creator"),
            InstalledAppTarget("YouTube Downloader", "com.example.youtube.downloader"),
            InstalledAppTarget("YouTube ReVanced", "app.revanced.android.youtube")
        )
        assertEquals(
            listOf("app.revanced.android.youtube"),
            AppTargetResolver.youtubeCandidates(installed).map { it.packageName }
        )
    }

    @Test fun fallsBackToOfficialYouTubeWhenNoModIsInstalled() {
        val installed = listOf(InstalledAppTarget("YouTube", "com.google.android.youtube"))
        val result = AppTargetResolver.findMentionedApps("open youtube", installed)
        assertEquals("com.google.android.youtube", result?.singleOrNull()?.packageName)
    }

    @Test fun asksForClarificationWhenMultipleYouTubeModsAreInstalled() {
        val installed = listOf(
            InstalledAppTarget("YouTube", "com.google.android.youtube"),
            InstalledAppTarget("YouTube ReVanced", "app.revanced.android.youtube"),
            InstalledAppTarget("YouTube Vanced", "com.vanced.youtube")
        )
        assertEquals(null, AppTargetResolver.findMentionedApps("open youtube", installed))
    }

    @Test fun resolvesChatGptByPackageAliasEvenWhenDisplayedLabelDiffers() {
        val result = AppTargetResolver.findMentionedApps("chat gpt kholo", listOf(
            InstalledAppTarget("OpenAI", "com.openai.chatgpt")
        ))
        assertEquals("com.openai.chatgpt", result?.singleOrNull()?.packageName)
    }

    @Test fun learnedAliasesCanBeProvidedWithoutChangingResolver() {
        val result = AppTargetResolver.findMentionedApps(
            "photo app kholo",
            apps
        ) { app -> if (app.packageName == "com.google.android.apps.nbu.paisa.user") setOf("photo app") else emptySet() }
        assertEquals("com.google.android.apps.nbu.paisa.user", result?.singleOrNull()?.packageName)
    }

    @Test fun rejectsGenericModelPlaceholderLabels() {
        assertTrue(AppTargetResolver.isPlaceholder("App"))
        assertTrue(AppTargetResolver.isPlaceholder("Target App"))
        assertFalse(AppTargetResolver.isPlaceholder("WhatsApp"))
    }

    @Test fun returnsNoTargetIfCommandDoesNotNameInstalledApp() {
        assertEquals(emptyList<InstalledAppTarget>(), AppTargetResolver.findMentionedApps("do something useful", apps))
        assertNull(AppTargetResolver.findMentionedApps("open photos", listOf(
            InstalledAppTarget("Photos", "com.android.photos"),
            InstalledAppTarget("Google Photos", "com.google.android.apps.photos")
        )))
    }

    @Test fun prefersLongestSpecificAppAlias() {
        val result = AppTargetResolver.findMentionedApps("Open Google Chrome", listOf(
            InstalledAppTarget("Google", "com.google.android.googlequicksearchbox"),
            InstalledAppTarget("Chrome", "com.android.chrome")
        ))
        assertEquals("com.android.chrome", result?.singleOrNull()?.packageName)
    }
}
