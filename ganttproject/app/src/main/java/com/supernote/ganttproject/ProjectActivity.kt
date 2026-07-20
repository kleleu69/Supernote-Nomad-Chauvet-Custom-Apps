package com.supernote.ganttproject

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.supernote.ganttproject.models.Project
import com.supernote.ganttproject.storage.ProjectStorage

class ProjectActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
    }

    private lateinit var project: Project
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var ganttView: GanttChartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project)

        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: run { finish(); return }
        val projects = ProjectStorage.load(this)
        project = projects.find { it.id == projectId } ?: run { finish(); return }

        ganttView = findViewById(R.id.ganttChartView)
        ganttView.tasks = project.tasks

        findViewById<TextView>(R.id.tvProjectTitle).text = project.name

        val rvTasks = findViewById<RecyclerView>(R.id.rvTasks)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        taskAdapter = TaskAdapter(
            tasks = project.tasks,
            onEdit = { task ->
                val intent = Intent(this, TaskEditActivity::class.java).apply {
                    putExtra(TaskEditActivity.EXTRA_PROJECT_ID, project.id)
                    putExtra(TaskEditActivity.EXTRA_TASK_ID, task.id)
                }
                startActivity(intent)
            },
            onDelete = { task ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.confirm_delete)
                    .setMessage(getString(R.string.delete_task_msg, task.name))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        val idx = project.tasks.indexOf(task)
                        if (idx >= 0) {
                            project.tasks.removeAt(idx)
                            saveProject()
                            taskAdapter.notifyItemRemoved(idx)
                            ganttView.tasks = project.tasks
                            updateEmptyState(tvEmpty)
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )

        rvTasks.layoutManager = LinearLayoutManager(this)
        rvTasks.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        rvTasks.adapter = taskAdapter

        updateEmptyState(tvEmpty)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnAddTask).setOnClickListener {
            val intent = Intent(this, TaskEditActivity::class.java).apply {
                putExtra(TaskEditActivity.EXTRA_PROJECT_ID, project.id)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        val projects = ProjectStorage.load(this)
        val refreshed = projects.find { it.id == project.id } ?: return
        project.tasks.clear()
        project.tasks.addAll(refreshed.tasks)
        taskAdapter.notifyDataSetChanged()
        ganttView.tasks = project.tasks
        updateEmptyState(findViewById(R.id.tvEmpty))
    }

    private fun saveProject() {
        val projects = ProjectStorage.load(this).toMutableList()
        val idx = projects.indexOfFirst { it.id == project.id }
        if (idx >= 0) projects[idx] = project
        ProjectStorage.save(this, projects)
    }

    private fun updateEmptyState(tvEmpty: TextView) {
        tvEmpty.visibility = if (project.tasks.isEmpty()) View.VISIBLE else View.GONE
    }
}
