package com.example.ocrserver.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ocrserver.R
import com.example.ocrserver.server.RequestLog

class RequestLogAdapter : ListAdapter<RequestLog, RequestLogAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_request_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logTimestamp: TextView = itemView.findViewById(R.id.logTimestamp)
        private val logMethod: TextView = itemView.findViewById(R.id.logMethod)
        private val logPath: TextView = itemView.findViewById(R.id.logPath)
        private val logStatus: TextView = itemView.findViewById(R.id.logStatus)
        private val logProcessingTime: TextView = itemView.findViewById(R.id.logProcessingTime)
        private val logClientIp: TextView = itemView.findViewById(R.id.logClientIp)

        fun bind(log: RequestLog) {
            logTimestamp.text = log.getFormattedTimestamp()
            logMethod.text = log.method
            logPath.text = log.path
            logStatus.text = log.statusCode.toString()
            logProcessingTime.text = "${log.processingTimeMs}ms"
            logClientIp.text = log.clientIp

            val statusColor = when (log.statusCode) {
                in 200..299 -> Color.parseColor("#4CAF50")
                in 400..499 -> Color.parseColor("#FF9800")
                in 500..599 -> Color.parseColor("#F44336")
                else -> Color.parseColor("#9E9E9E")
            }
            logStatus.setTextColor(statusColor)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RequestLog>() {
        override fun areItemsTheSame(oldItem: RequestLog, newItem: RequestLog): Boolean {
            return oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: RequestLog, newItem: RequestLog): Boolean {
            return oldItem == newItem
        }
    }
}

