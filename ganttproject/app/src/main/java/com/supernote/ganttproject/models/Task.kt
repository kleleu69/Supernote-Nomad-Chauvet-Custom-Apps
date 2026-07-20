package com.supernote.ganttproject.models

import org.json.JSONObject

data class Task(
    val id: String,
    var name: String,
    var startDay: Int,    // 1-based day within the project timeline
    var durationDays: Int // number of days
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("startDay", startDay)
        put("durationDays", durationDays)
    }

    companion object {
        fun fromJson(obj: JSONObject) = Task(
            id           = obj.getString("id"),
            name         = obj.getString("name"),
            startDay     = obj.getInt("startDay"),
            durationDays = obj.getInt("durationDays")
        )
    }
}
