package com.supernote.ganttproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.supernote.ganttproject.models.Task

class TaskAdapter(
    private val tasks: List<Task>,
    private val onEdit: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvTaskName)
        val tvStart: TextView = view.findViewById(R.id.tvTaskStart)
        val tvDuration: TextView = view.findViewById(R.id.tvTaskDuration)
        val btnEdit: Button = view.findViewById(R.id.btnEditTask)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteTask)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val task = tasks[position]
        val resources = holder.itemView.resources
        holder.tvName.text = task.name
        holder.tvStart.text = resources.getString(R.string.day_format, task.startDay)
        holder.tvDuration.text = resources.getQuantityString(R.plurals.duration_days_short, task.durationDays, task.durationDays)
        holder.btnEdit.setOnClickListener { onEdit(task) }
        holder.btnDelete.setOnClickListener { onDelete(task) }
    }

    override fun getItemCount() = tasks.size
}
