package com.openjarvis.voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class IndicModelCompatibilityTest {
 private fun profile(size:Long?=100L, peak:Long?=500L, license:String?="Apache-2.0", api:Int=26, lang:String="mr", abi:String="arm64-v8a") = IndicModelProfile(lang,"test-indic-model",api,abi,size,peak,license)
 @Test fun acceptsFullySpecifiedProfileWithinBudget(){ assertTrue(IndicModelCompatibility.evaluate(profile(),34,"arm64-v8a",1000,2000).compatible) }
 @Test fun rejectsLanguageApiAndAbiMismatch(){ assertEquals(IndicModelRejection.UNSUPPORTED_LANGUAGE,IndicModelCompatibility.evaluate(profile(lang="en"),34,"arm64-v8a",1000,2000).rejection); assertEquals(IndicModelRejection.API_TOO_LOW,IndicModelCompatibility.evaluate(profile(api=34),26,"arm64-v8a",1000,2000).rejection); assertEquals(IndicModelRejection.ABI_MISMATCH,IndicModelCompatibility.evaluate(profile(),34,"x86_64",1000,2000).rejection) }
 @Test fun rejectsUnknownMetadata(){ assertEquals(IndicModelRejection.MODEL_SIZE_UNKNOWN,IndicModelCompatibility.evaluate(profile(size=null),34,"arm64-v8a",1000,2000).rejection); assertEquals(IndicModelRejection.MEMORY_ESTIMATE_UNKNOWN,IndicModelCompatibility.evaluate(profile(peak=null),34,"arm64-v8a",1000,2000).rejection); assertEquals(IndicModelRejection.LICENSE_UNVERIFIED,IndicModelCompatibility.evaluate(profile(license="unknown"),34,"arm64-v8a",1000,2000).rejection) }
 @Test fun rejectsInsufficientResources(){ assertEquals(IndicModelRejection.STORAGE_INSUFFICIENT,IndicModelCompatibility.evaluate(profile(size=2000),34,"arm64-v8a",1000,2000).rejection); assertEquals(IndicModelRejection.MEMORY_BUDGET_EXCEEDED,IndicModelCompatibility.evaluate(profile(peak=1001),34,"arm64-v8a",1000,2000).rejection) }
}