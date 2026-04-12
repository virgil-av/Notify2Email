package com.notify2email.app.events

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notify2email.app.MainActivity
import com.notify2email.app.R
import com.notify2email.app.data.EventHistoryStore
import com.notify2email.app.settings.SettingsActivity
import com.google.android.material.button.MaterialButton

class EventsActivity : AppCompatActivity() {

    private lateinit var eventHistoryStore: EventHistoryStore
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: EventRecordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        eventHistoryStore = EventHistoryStore(applicationContext)

        bindViews()
        bindNavigation()
        bindActions()
    }

    override fun onResume() {
        super.onResume()
        renderEvents()
    }

    private fun bindViews() {
        recyclerView = findViewById(R.id.recycler_events)
        emptyView = findViewById(R.id.text_events_empty)
        adapter = EventRecordAdapter(
            onDeleteClicked = { eventId ->
                eventHistoryStore.deleteEvent(eventId)
                renderEvents()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun bindNavigation() {
        findViewById<MaterialButton>(R.id.button_nav_home).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.button_nav_events).isEnabled = false
        findViewById<MaterialButton>(R.id.button_nav_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun bindActions() {
        findViewById<MaterialButton>(R.id.button_delete_all_events).setOnClickListener {
            eventHistoryStore.clearEvents()
            renderEvents()
        }
    }

    private fun renderEvents() {
        val events = eventHistoryStore.getEvents()
        adapter.submit(events)
        val hasItems = events.isNotEmpty()
        recyclerView.visibility = if (hasItems) View.VISIBLE else View.GONE
        emptyView.visibility = if (hasItems) View.GONE else View.VISIBLE
    }
}
