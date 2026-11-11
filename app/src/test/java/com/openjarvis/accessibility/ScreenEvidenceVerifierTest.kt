package com.openjarvis.accessibility
import org.junit.Assert.assertEquals
import org.junit.Test
class ScreenEvidenceVerifierTest {
 @Test fun verifiesExpectedPackage(){val b=ScreenEvidence("com.android.settings","Settings",4);val a=ScreenEvidence("com.whatsapp","Chats",8);assertEquals(ScreenVerification.VERIFIED,ScreenEvidenceVerifier.verifyNavigation(b,a,expectedPackage="com.whatsapp"))}
 @Test fun verifiesExpectedVisibleText(){val b=ScreenEvidence("com.example.app","Home",2);val a=ScreenEvidence("com.example.app","Home Search results",3);assertEquals(ScreenVerification.VERIFIED,ScreenEvidenceVerifier.verifyNavigation(b,a,expectedVisibleText="Search results"))}
 @Test fun reportsChangeWithoutClaimingSuccess(){val b=ScreenEvidence("com.example.app","Home",2);val a=ScreenEvidence("com.example.app","Inbox unread messages",5);assertEquals(ScreenVerification.OBSERVED_CHANGE,ScreenEvidenceVerifier.verifyNavigation(b,a))}
 @Test fun doesNotClaimSuccessWhenNothingChanged(){val e=ScreenEvidence("com.example.app","Home",2);assertEquals(ScreenVerification.UNVERIFIED,ScreenEvidenceVerifier.verifyNavigation(e,e))}
 @Test fun verifiesVisibleText(){val a=ScreenEvidence("com.example.app","Message body: नमस्कार मित्रा",4);assertEquals(ScreenVerification.VERIFIED,ScreenEvidenceVerifier.verifyTextVisible(a,"नमस्कार मित्रा"))}
 @Test fun rejectsMissingOrInvalidEvidence(){assertEquals(ScreenVerification.INVALID_EVIDENCE,ScreenEvidenceVerifier.verifyNavigation(null,ScreenEvidence("app","Home",1)));assertEquals(ScreenVerification.INVALID_EVIDENCE,ScreenEvidenceVerifier.verifyTextVisible(ScreenEvidence("app","",0),"  "))}
}