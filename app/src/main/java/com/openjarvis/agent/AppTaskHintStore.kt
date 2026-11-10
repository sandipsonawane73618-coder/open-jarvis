package com.openjarvis.agent
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
class AppTaskHintStore(context:Context) {
 private val preferences=context.applicationContext.getSharedPreferences("app_task_hints_v1",Context.MODE_PRIVATE)
 fun getHints(packageName:String):List<AppTaskHint>{if(packageName.isBlank())return emptyList();val raw=preferences.getString(key(packageName),null)?:return emptyList();return runCatching{val a=JSONArray(raw);(0 until a.length()).mapNotNull{i->val item=a.optJSONObject(i)?:return@mapNotNull null;AppTaskHintPolicy.create(packageName,item.optString("task"),item.optString("guidance"))}}.getOrDefault(emptyList())}
 fun recordSuccessfulFlow(packageName:String,taskLabel:String,guidance:String):Boolean{val candidate=AppTaskHintPolicy.create(packageName,taskLabel,guidance)?:return false;val merged=AppTaskHintPolicy.merge(getHints(packageName),candidate);val a=JSONArray();merged.forEach{a.put(JSONObject().put("task",it.taskLabel).put("guidance",it.guidance))};preferences.edit().putString(key(packageName),a.toString()).apply();return true}
 fun clear(packageName:String){if(packageName.isNotBlank())preferences.edit().remove(key(packageName)).apply()}
 private fun key(packageName:String)="app_"+packageName.replace(Regex("[^A-Za-z0-9_]"),"_")
}