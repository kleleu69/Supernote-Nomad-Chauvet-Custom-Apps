package com.supernote.ganttproject.models

import org.json.JSONArray
import org.json.JSONObject

data class Project(
    val id: String,
    var name: String,
    val tasks: MutableList<Task> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        val arr = JSONArray()
        tasks.forEach { arr.put(it.toJson()) }
        put("tasks", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): Project {
            val tasks = mutableListOf<Task>()
            val arr = obj.optJSONArray("tasks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                tasks.add(Task.fromJson(arr.getJSONObject(i)))
            }
            return Project(
                id    = obj.getString("id"),
                name  = obj.getString("name"),
                tasks = tasks
            )
        }
    }
}
