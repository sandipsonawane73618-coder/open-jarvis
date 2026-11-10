package com.openjarvis.agent

data class AppTaskHint(val packageName:String,val taskLabel:String,val guidance:String)
object AppTaskHintPolicy {
 private val packagePattern=Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
 private val email=Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
 private val phone=Regex("(?<!\\d)(?:\\+?91[-\\s]?)?[6-9]\\d{9}(?!\\d)")
 private val longNumber=Regex("\\d{6,}")
 private val forbidden=Regex("(?i)\\b(password|passcode|otp|pin|upi|bank|card|account|message|contact|phone|email|clipboard|type|paste|node[_ -]?id|resource[_ -]?id|view[_ -]?id)\\b")
 fun create(packageName:String,taskLabel:String,guidance:String):AppTaskHint? { val pkg=packageName.trim(); if(!packagePattern.matches(pkg)) return null; val task=normalize(taskLabel).take(64); val hint=normalize(guidance).take(180); if(task.isBlank()||hint.isBlank()||sensitive(task)||sensitive(hint)||forbidden.containsMatchIn(task)||forbidden.containsMatchIn(hint)) return null; return AppTaskHint(pkg,task,hint) }
 fun merge(existing:List<AppTaskHint>,candidate:AppTaskHint,maxEntries:Int=10,maxChars:Int=1200):List<AppTaskHint> { if(maxEntries<=0||maxChars<=0)return emptyList(); val safe=create(candidate.packageName,candidate.taskLabel,candidate.guidance)?:return existing.takeLast(maxEntries); val prior=existing.mapNotNull{create(it.packageName,it.taskLabel,it.guidance)}.filter{it.packageName==safe.packageName}.filterNot{it.taskLabel==safe.taskLabel}; val result=ArrayDeque<AppTaskHint>(); var chars=0; for(entry in (prior+safe).asReversed()){val size=entry.taskLabel.length+entry.guidance.length;if(result.size>=maxEntries||chars+size>maxChars)continue;result.addFirst(entry);chars+=size};return result.toList() }
 private fun sensitive(v:String)=email.containsMatchIn(v)||phone.containsMatchIn(v)||longNumber.containsMatchIn(v)
 private fun normalize(v:String)=v.trim().replace(Regex("\\s+")," ")
}