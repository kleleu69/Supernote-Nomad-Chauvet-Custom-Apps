package com.supernote.ganttproject

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.supernote.ganttproject.models.Project
import com.supernote.ganttproject.storage.ProjectStorage
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val projects = mutableListOf<Project>()
    private lateinit var adapter: ProjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projects.addAll(ProjectStorage.load(this))

        val rvProjects = findViewById<RecyclerView>(R.id.rvProjects)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        adapter = ProjectAdapter(
            projects = projects,
            onClick = { project ->
                val intent = Intent(this, ProjectActivity::class.java)
                intent.putExtra(ProjectActivity.EXTRA_PROJECT_ID, project.id)
                startActivity(intent)
            },
            onDelete = { project -> confirmDeleteProject(project) }
        )

        rvProjects.layoutManager = LinearLayoutManager(this)
        rvProjects.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        rvProjects.adapter = adapter

        updateEmptyState(tvEmpty)

        findViewById<Button>(R.id.btnAddProject).setOnClickListener {
            showAddProjectDialog(tvEmpty)
        }
    }

    override fun onResume() {
        super.onResume()
        projects.clear()
        projects.addAll(ProjectStorage.load(this))
        adapter.notifyDataSetChanged()
        updateEmptyState(findViewById(R.id.tvEmpty))
    }

    private fun showAddProjectDialog(tvEmpty: TextView) {
        val density = resources.displayMetrics.density
        val et = EditText(this).apply {
            hint = getString(R.string.project_name)
            textSize = 16f
            setPadding(
                (32 * density).toInt(),
                (16 * density).toInt(),
                (32 * density).toInt(),
                (16 * density).toInt()
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.new_project)
            .setView(et)
            .setPositiveButton(R.string.add_project) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    val p = Project(id = UUID.randomUUID().toString(), name = name)
                    projects.add(p)
                    ProjectStorage.save(this, projects)
                    adapter.notifyItemInserted(projects.size - 1)
                    updateEmptyState(tvEmpty)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteProject(project: Project) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(getString(R.string.delete_project_msg, project.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                val idx = projects.indexOf(project)
                if (idx >= 0) {
                    projects.removeAt(idx)
                    ProjectStorage.save(this, projects)
                    adapter.notifyItemRemoved(idx)
                    updateEmptyState(findViewById(R.id.tvEmpty))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateEmptyState(tvEmpty: TextView) {
        tvEmpty.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
    }
}
