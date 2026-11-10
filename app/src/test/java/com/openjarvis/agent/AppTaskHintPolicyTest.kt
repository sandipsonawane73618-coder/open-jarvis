package com.openjarvis.agent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
class AppTaskHintPolicyTest {
 @Test fun acceptsGenericGuidance(){assertEquals("com.example.shop",AppTaskHintPolicy.create("com.example.shop","open search","Open search screen and choose visible result")?.packageName)}
 @Test fun rejectsInvalidAndSensitiveContent(){assertNull(AppTaskHintPolicy.create("bad package","open search","Tap search"));assertNull(AppTaskHintPolicy.create("com.example.app","send message","Open chat and type message"));assertNull(AppTaskHintPolicy.create("com.example.app","find user","Search 9876543210"));assertNull(AppTaskHintPolicy.create("com.example.app","login","Enter user@example.com"));assertNull(AppTaskHintPolicy.create("com.example.app","settings","Tap node_id=button_4"))}
 @Test fun mergeReplacesDuplicateAndIsBounded(){val old=AppTaskHintPolicy.create("com.example.app","open search","Use top search")!!;val newer=AppTaskHintPolicy.create("com.example.app","open search","Use toolbar search icon")!!;val r=AppTaskHintPolicy.merge(listOf(old),newer,3,200);assertEquals(1,r.size);assertEquals("Use toolbar search icon",r.single().guidance)}
 @Test fun mergeKeepsRecentEntries(){val a=AppTaskHintPolicy.create("com.example.app","open home","Open home tab")!!;val b=AppTaskHintPolicy.create("com.example.app","open search","Open search tab")!!;val c=AppTaskHintPolicy.create("com.example.app","open profile","Open profile tab")!!;val r=AppTaskHintPolicy.merge(listOf(a,b),c,2,500);assertEquals(2,r.size);assertTrue(r.none{it.taskLabel=="open home"});assertEquals("open profile",r.last().taskLabel)}
 @Test fun rejectsEmptyAndOtp(){assertNull(AppTaskHintPolicy.create("com.example.app"," ","Open settings"));assertNull(AppTaskHintPolicy.create("com.example.app","open settings","Use OTP field"))}
}