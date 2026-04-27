package com.notify2email.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.notify2email.app.di.appContainer
import com.notify2email.app.domain.formatter.EventFormatter
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.email.BatchQueueEvent
import com.notify2email.app.email.SmtpConfigProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneStateReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (state == TelephonyManager.EXTRA_STATE_RINGING && number != null) {
            val container = context.appContainer
            scope.launch {
                val settings = container.settingsRepository.getSettings()
                if (!settings.enabled || !settings.callsEnabled) return@launch

                val contactName = if (settings.resolveContactNames) {
                    container.contactNameResolver.resolve(number)
                } else null

                val simInfo = if (settings.showSimInfo) {
                    container.simSlotResolver.resolveBestEffort(intent = intent)
                } else null

                val source = contactName ?: number
                val simSuffix = simInfo?.displayName?.let { " ($it)" } ?: ""
                val content = "Incoming call ringing: $number$simSuffix"

                container.eventBatchQueueManager.enqueue(
                    BatchQueueEvent(
                        sourceTag = source,
                        enabledKey = SmtpConfigProvider.KEY_CALL_LOGS_ENABLED,
                        eventType = EventType.CALL,
                        identity = "ringing_${System.currentTimeMillis()}",
                        contentPreview = content,
                        detailBody = EventFormatter.formatEventHtml(
                            type = EventType.CALL,
                            source = source,
                            timestampMillis = System.currentTimeMillis(),
                            content = content,
                            customTypeLabel = "INCOMING CALL"
                        ),
                        timestampMillis = System.currentTimeMillis(),
                        dedupeKey = "ringing_${number}_${System.currentTimeMillis() / 30000}",
                        customTypeLabel = "INCOMING CALL"
                    )
                )
                container.logRepository.addLog("Phone state: captured ringing call from $source.")
            }
        }
    }
}
