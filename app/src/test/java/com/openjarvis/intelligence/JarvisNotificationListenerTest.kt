package com.openjarvis.intelligence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisNotificationListenerTest {

    @Test
    fun `known Indian banking and UPI notifications are blocked`() {
        val protectedPackages = listOf(
            "com.sbi.lotusintouch",
            "com.csam.icici.bank.imobile",
            "com.snapwork.hdfc",
            "com.axis.mobile",
            "com.fss.pnbpsp",
            "com.idfcfirstbank.optimus",
            "com.phonepe.app",
            "net.one97.paytm",
            "com.google.android.apps.nbu.paisa.user",
            "in.org.npci.upiapp",
            "com.wf.wellsfargomobile",
            "com.usbank.mobilebanking",
            "com.citi.citimobile"
        )
        protectedPackages.forEach {
            assertFalse("$it must be privacy protected", JarvisNotificationListener.shouldProcessNotification(it))
        }
    }

    @Test
    fun `bank package heuristic blocks previously unknown banking package`() {
        assertFalse(JarvisNotificationListener.shouldProcessNotification("com.example.newbank.mobile"))
        assertFalse(JarvisNotificationListener.shouldProcessNotification("com.example.upi.wallet"))
        assertFalse(JarvisNotificationListener.shouldProcessNotification("com.example.financeapp"))
    }

    @Test
    fun `ordinary messaging and utility notifications remain processable`() {
        assertTrue(JarvisNotificationListener.shouldProcessNotification("com.whatsapp"))
        assertTrue(JarvisNotificationListener.shouldProcessNotification("org.telegram.messenger"))
        assertTrue(JarvisNotificationListener.shouldProcessNotification("com.google.android.calendar"))
    }

    @Test
    fun `blank package names are rejected`() {
        assertFalse(JarvisNotificationListener.shouldProcessNotification(""))
        assertFalse(JarvisNotificationListener.shouldProcessNotification("   "))
    }
}
