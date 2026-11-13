package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankingSafetyPolicyTest {
    @Test fun blocksPaymentAndTransferIntentsAcrossLanguages() {
        listOf(
            "Pay ₹500 to Rahul",
            "Send 500 rupees to Amit",
            "Rahul ko 500 rupees bhejo",
            "UPI payment karo",
            "paise transfer karo",
            "भुगतान करो",
            "पैसे भेजो",
            "पैसे पाठवा",
            "PhonePe se payment kara"
        ).forEach { command ->
            assertTrue("Should block: $command", BankingSafetyPolicy.isBlockedCommand(command))
        }
    }

    @Test fun blocksCredentialSubmissionButNotCredentialEducation() {
        listOf(
            "UPI PIN enter karo",
            "OTP type karo",
            "share CVV",
            "card number paste karo",
            "ओटीपी डालो",
            "यूपीआई पिन बताओ",
            "CVV भरें"
        ).forEach { command ->
            assertTrue("Should block: $command", BankingSafetyPolicy.isBlockedCommand(command))
        }
        assertFalse(BankingSafetyPolicy.isBlockedCommand("What is a UPI PIN?"))
        assertFalse(BankingSafetyPolicy.isBlockedCommand("Explain how OTP works"))
        assertFalse(BankingSafetyPolicy.isBlockedCommand("How to pay a bill"))
        assertFalse(BankingSafetyPolicy.isBlockedCommand("How to enter OTP"))
    }

    @Test fun allowsReadOnlyBankingAndAppLaunchCommands() {
        listOf(
            "Open GPay",
            "PhonePe kholo",
            "show my bank balance",
            "check transaction history",
            "open Paytm"
        ).forEach { command ->
            assertFalse("Should allow: $command", BankingSafetyPolicy.isBlockedCommand(command))
        }
    }

    @Test fun recognizesUnlistedIndianBankPackagesForForegroundProtection() {
        listOf(
            "com.example.bank.app",
            "com.example.wallet.client",
            "com.paytm",
            "net.one97.paytm",
            "com.phonepe.app",
            "com.sbi.lotusintouch",
            "com.csam.icici.bank.imobile",
            "com.axis.mobile",
            "com.snapwork.hdfc",
            "com.fss.pnbpsp",
            "com.mobikwik_new",
            "com.dreamplug.androidapp",
            "com.bankofbaroda.upi",
            "com.samsung.android.spay"
        ).forEach { packageName ->
            assertTrue("Should recognize financial package: $packageName",
                BankingSafetyPolicy.isFinancialPackage(packageName))
        }
        assertFalse(BankingSafetyPolicy.isFinancialPackage("com.example.shopping"))
    }

    @Test fun foregroundBankingContextOverridesUntrustedActionPackage() {
        val actions = listOf(
            Action(action = Action.TYPE_TEXT, packageName = "com.example.shopping", value = "123"),
            Action(action = Action.CLICK, packageName = "com.example.shopping", text = "Pay")
        )
        val scoped = BankingSafetyPolicy.scopeActionsToFinancialForeground(
            actions,
            "com.sbi.lotusintouch"
        )
        assertTrue(scoped.all { it.packageName == "com.sbi.lotusintouch" })
        assertTrue(BankingSafetyPolicy.blocksFinancialActions(scoped))
    }

    @Test fun nonFinancialForegroundDoesNotRewriteExplicitTargets() {
        val actions = listOf(
            Action(action = Action.CLICK, packageName = "com.example.shopping", text = "Search")
        )
        assertEquals(
            actions,
            BankingSafetyPolicy.scopeActionsToFinancialForeground(actions, "com.example.shopping")
        )
        assertEquals(
            actions,
            BankingSafetyPolicy.scopeActionsToFinancialForeground(actions, null)
        )
    }

    @Test fun blocksFinancialAppTextEntryAndPaymentControls() {
        val typeIntoPhonePe = Action(
            action = Action.TYPE,
            packageName = "com.phonepe",
            value = "500"
        )
        val tapPayInGpay = Action(
            action = Action.CLICK,
            packageName = "com.google.android.apps.nbu.paisa.user",
            text = "Pay"
        )
        val tapBalanceInGpay = Action(
            action = Action.CLICK,
            packageName = "com.google.android.apps.nbu.paisa.user",
            text = "Check balance"
        )
        val openGpay = Action(
            action = Action.OPEN_APP,
            packageName = "com.google.android.apps.nbu.paisa.user",
            label = "Google Pay"
        )
        assertTrue(BankingSafetyPolicy.blocksFinancialActions(listOf(typeIntoPhonePe)))
        assertTrue(BankingSafetyPolicy.blocksFinancialActions(listOf(tapPayInGpay)))
        assertFalse(BankingSafetyPolicy.blocksFinancialActions(listOf(tapBalanceInGpay)))
        assertFalse(BankingSafetyPolicy.blocksFinancialActions(listOf(openGpay)))
    }

    @Test fun planPolicyRejectsBankingSideEffectsAndExplainsWhy() {
        val transfer = listOf(
            Action(action = Action.CLICK, packageName = "com.phonepe", text = "Transfer")
        )
        assertFalse(AgentPlanPolicy.isExecutablePlan(transfer, "open PhonePe"))
        assertEquals(
            BankingSafetyPolicy.BLOCKED_TRANSACTION,
            AgentPlanPolicy.failureRule(transfer, "open PhonePe")
        )
        assertTrue(
            AgentPlanPolicy.userGuidance(BankingSafetyPolicy.BLOCKED_TRANSACTION)
                .contains("payment")
        )
    }

    @Test fun doesNotBlockUnrelatedTypingOrReadOnlyActions() {
        val search = listOf(Action(action = Action.TYPE, value = "weather in Pune"))
        val read = listOf(Action(action = Action.CLICK, text = "Account details"))
        assertFalse(BankingSafetyPolicy.blocksFinancialActions(search))
        assertFalse(BankingSafetyPolicy.blocksFinancialActions(read))
    }
    @Test fun messageRecipientKoDoesNotBecomeMoneyTransferWithoutMoneyIntent() {
        assertFalse(BankingSafetyPolicy.isBlockedCommand("WhatsApp per Rahul ko message bhejo ki hello"))
        listOf(
            "Rahul ko 500 rupaye bhejo",
            "paise bhejo",
            "send money to Rahul",
            "UPI payment karo"
        ).forEach { command ->
            assertTrue("Real-money intent must remain blocked: $command", BankingSafetyPolicy.isBlockedCommand(command))
        }
    }
}
