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
    fun `legacy financial package aliases remain privacy protected`() {
        val legacyAliases = listOf(
            "com.chase",
            "com.usbank",
            "com.citi",
            "com.barclays",
            "com.hsbc",
            "com.db",
            "com.americanexpress",
            "com.discover",
            "com.paypal",
            "com.squareup",
            "com.stripe"
        )
        legacyAliases.forEach {
            assertFalse("$it must remain privacy protected", JarvisNotificationListener.shouldProcessNotification(it))
        }
    }

    @Test
    fun `financial package markers block unfamiliar bank payment and card apps`() {
        val unknownFinancialPackages = listOf(
            "com.example.newbank.mobile",
            "com.example.upi.wallet",
            "com.example.financeapp",
            "com.example.payment.client",
            "com.example.bhim",
            "com.example.cred",
            "com.example.creditcard",
            "com.example.debitcard",
            "com.example.gpay",
            "com.example.kotak.mobile",
            "com.example.idbi.mobile",
            "com.example.indusind.mobile",
            "com.example.canara.mobile",
            "com.example.unionbank.mobile",
            "com.example.pnb.mobile",
            "com.example.sbi.mobile",
            "com.example.axis.mobile"
        )
        unknownFinancialPackages.forEach {
            assertFalse("$it must be privacy protected", JarvisNotificationListener.shouldProcessNotification(it))
        }
    }

    @Test
    fun `ordinary messaging and utility notifications remain processable`() {
        assertTrue(JarvisNotificationListener.shouldProcessNotification("com.whatsapp"))
        assertTrue(JarvisNotificationListener.shouldProcessNotification("org.telegram.messenger"))
        assertTrue(JarvisNotificationListener.shouldProcessNotification("com.google.android.calendar"))
    }

    @Test
    fun `package matching is case insensitive and trims whitespace`() {
        assertFalse(JarvisNotificationListener.shouldProcessNotification("  COM.EXAMPLE.BANK.APP  "))
        assertFalse(JarvisNotificationListener.shouldProcessNotification("COM.PHONEPE.APP"))
    }

    @Test
    fun `blank package names are rejected`() {
        assertFalse(JarvisNotificationListener.shouldProcessNotification(""))
        assertFalse(JarvisNotificationListener.shouldProcessNotification("   "))
    }
}
