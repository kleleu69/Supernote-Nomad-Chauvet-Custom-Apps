package com.supernote.ganttproject

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.supernote.ganttproject.models.Task
import com.supernote.ganttproject.storage.ProjectStorage
import java.util.UUID

class TaskEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_TASK_ID = "task_id"     // null = new task
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_edit)

        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: run { finish(); return }
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)

        val projects = ProjectStorage.load(this).toMutableList()
        val projectIdx = projects.indexOfFirst { it.id == projectId }
        if (projectIdx < 0) { finish(); return }
        val project = projects[projectIdx]

        val existingTask = taskId?.let { id -> project.tasks.find { it.id == id } }

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val etName = findViewById<EditText>(R.id.etTaskName)
        val etStart = findViewById<EditText>(R.id.etStartDay)
        val etDuration = findViewById<EditText>(R.id.etDuration)

        tvTitle.text = if (existingTask != null) getString(R.string.edit_task) else getString(R.string.add_task)

        existingTask?.let {
            etName.setText(it.name)
            etStart.setText(it.startDay.toString())
            etDuration.setText(it.durationDays.toString())
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val name = etName.text.toString().trim()
            val startDay = etStart.text.toString().toIntOrNull() ?: 0
            val duration = etDuration.text.toString().toIntOrNull() ?: 0

            if (name.isEmpty()) {
                Toast.makeText(this, R.string.error_task_name_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (startDay < 1) {
                Toast.makeText(this, R.string.error_start_day, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (duration < 1) {
                Toast.makeText(this, R.string.error_duration, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (existingTask != null) {
                existingTask.name = name
                existingTask.startDay = startDay
                existingTask.durationDays = duration
            } else {
                project.tasks.add(Task(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    startDay = startDay,
                    durationDays = duration
                ))
            }

            ProjectStorage.save(this, projects)
            finish()
        }
    }
}
