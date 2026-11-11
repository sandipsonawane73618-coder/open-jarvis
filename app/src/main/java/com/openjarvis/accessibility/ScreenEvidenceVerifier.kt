package com.openjarvis.accessibility

data class ScreenEvidence(val packageName:String?,val visibleText:String,val interactiveElementCount:Int)
enum class ScreenVerification { VERIFIED, OBSERVED_CHANGE, UNVERIFIED, INVALID_EVIDENCE }
object ScreenEvidenceVerifier {
 fun verifyNavigation(before:ScreenEvidence?,after:ScreenEvidence?,expectedPackage:String?=null,expectedVisibleText:String?=null):ScreenVerification {
  if(before==null||after==null||before.interactiveElementCount<0||after.interactiveElementCount<0)return ScreenVerification.INVALID_EVIDENCE
  val target=expectedPackage?.trim()?.takeIf{it.isNotEmpty()};if(target!=null&&after.packageName.equals(target,true))return ScreenVerification.VERIFIED
  val expected=normalize(expectedVisibleText.orEmpty());if(expected.isNotEmpty()&&normalize(after.visibleText).contains(expected))return ScreenVerification.VERIFIED
  val bp=before.packageName?.trim().orEmpty();val ap=after.packageName?.trim().orEmpty();if(ap.isNotEmpty()&&!bp.equals(ap,true))return ScreenVerification.OBSERVED_CHANGE
  val bt=normalize(before.visibleText);val at=normalize(after.visibleText);return if(at.isNotEmpty()&&bt!=at)ScreenVerification.OBSERVED_CHANGE else ScreenVerification.UNVERIFIED
 }
 fun verifyTextVisible(after:ScreenEvidence?,expectedText:String):ScreenVerification{if(after==null||after.interactiveElementCount<0)return ScreenVerification.INVALID_EVIDENCE;val expected=normalize(expectedText);if(expected.isEmpty())return ScreenVerification.INVALID_EVIDENCE;return if(normalize(after.visibleText).contains(expected))ScreenVerification.VERIFIED else ScreenVerification.UNVERIFIED}
 private fun normalize(v:String)=v.trim().lowercase().replace(Regex("\\s+")," ")
}