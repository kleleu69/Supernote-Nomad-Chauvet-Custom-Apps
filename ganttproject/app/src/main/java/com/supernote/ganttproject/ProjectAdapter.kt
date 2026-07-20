package com.supernote.ganttproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.supernote.ganttproject.models.Project

class ProjectAdapter(
    private val projects: List<Project>,
    private val onClick: (Project) -> Unit,
    private val onDelete: (Project) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvProjectName)
        val tvCount: TextView = view.findViewById(R.id.tvTaskCount)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteProject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val project = projects[position]
        holder.tvName.text = project.name
        val count = project.tasks.size
        holder.tvCount.text = holder.itemView.resources.getQuantityString(R.plurals.task_count, count, count)
        holder.itemView.setOnClickListener { onClick(project) }
        holder.btnDelete.setOnClickListener { onDelete(project) }
    }

    override fun getItemCount() = projects.size
}
