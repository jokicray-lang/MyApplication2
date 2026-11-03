package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.database.ExerciseRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : ListAdapter<ExerciseRecord, HistoryAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tvItemType)
        val tvDate: TextView = view.findViewById(R.id.tvItemDate)
        val tvCount: TextView = view.findViewById(R.id.tvItemCount)
        val tvScore: TextView = view.findViewById(R.id.tvItemScore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvType.text = item.type
        holder.tvCount.text = "${item.count} 次"
        holder.tvScore.text = "${item.score} 分"

        // 格式化时间
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(item.timestamp))
    }

    class DiffCallback : DiffUtil.ItemCallback<ExerciseRecord>() {
        override fun areItemsTheSame(oldItem: ExerciseRecord, newItem: ExerciseRecord) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ExerciseRecord, newItem: ExerciseRecord) = oldItem == newItem
    }
}