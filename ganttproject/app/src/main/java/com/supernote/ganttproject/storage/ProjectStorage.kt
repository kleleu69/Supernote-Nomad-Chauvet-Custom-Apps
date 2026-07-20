package com.supernote.ganttproject.storage

import android.content.Context
import android.util.Log
import com.supernote.ganttproject.models.Project
import org.json.JSONArray
import org.json.JSONException

private const val PREFS_NAME = "ganttproject_data"
private const val KEY_PROJECTS = "projects"
private const val TAG = "ProjectStorage"

object ProjectStorage {

    fun load(context: Context): MutableList<Project> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PROJECTS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<Project>()
            for (i in 0 until arr.length()) {
                list.add(Project.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (error: JSONException) {
            Log.w(TAG, "Clearing corrupted stored projects JSON", error)
            prefs.edit().remove(KEY_PROJECTS).apply()
            mutableListOf()
        }
    }

    fun save(context: Context, projects: List<Project>) {
        val arr = JSONArray()
        projects.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROJECTS, arr.toString())
            .apply()
    }
}
