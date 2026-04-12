package com.notify2email.app.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.notify2email.app.R
import com.notify2email.app.data.EventRecord
import com.google.android.material.button.MaterialButton
import java.text.DateFormat
import java.util.Date

class EventRecordAdapter(
    private val onDeleteClicked: (String) -> Unit
) : RecyclerView.Adapter<EventRecordAdapter.EventViewHolder>() {

    private val items = mutableListOf<EventRecord>()

    fun submit(newItems: List<EventRecord>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event_record, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeValue: TextView = itemView.findViewById(R.id.text_event_type_value)
        private val previewValue: TextView = itemView.findViewById(R.id.text_event_preview_value)
        private val timestampValue: TextView = itemView.findViewById(R.id.text_event_timestamp_value)
        private val statusValue: TextView = itemView.findViewById(R.id.text_event_status_value)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.button_delete_event)

        fun bind(item: EventRecord) {
            typeValue.text = item.type.name
            previewValue.text = item.contentPreview
            timestampValue.text = DateFormat.getDateTimeInstance().format(Date(item.timestampMillis))
            statusValue.text = item.status.name
            deleteButton.setOnClickListener { onDeleteClicked(item.id) }
        }
    }
}
