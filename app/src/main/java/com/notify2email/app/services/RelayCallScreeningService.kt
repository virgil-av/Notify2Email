package com.notify2email.app.services

import android.telecom.Call
import android.telecom.CallScreeningService
import com.notify2email.app.di.appContainer
import com.notify2email.app.domain.formatter.EventFormatter
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.email.BatchQueueEvent
import com.notify2email.app.email.SmtpConfigProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RelayCallScreeningService : CallScreeningService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val container = applicationContext.appContainer

        // Always allow the call to proceed by default
        respondToCall(callDetails, CallResponse.Builder().build())

        serviceScope.launch {
            val settings = container.settingsRepository.getSettings()
            if (!settings.callScreeningEnabled || !settings.enabled || !settings.callsEnabled) return@launch

            val number = callDetails.handle?.schemeSpecificPart
            val contactName = if (settings.resolveContactNames) {
                container.contactNameResolver.resolve(number)
            } else null

            val simInfo = if (settings.showSimInfo) {
                container.simSlotResolver.resolveBestEffort(phoneAccountHandle = callDetails.accountHandle)
            } else null

            val source = contactName ?: number ?: "Unknown Caller"
            val simSuffix = simInfo?.displayName?.let { " ($it)" } ?: ""
            val content = "Incoming call screened: $number$simSuffix"

            container.eventBatchQueueManager.enqueue(
                BatchQueueEvent(
                    sourceTag = source,
                    enabledKey = SmtpConfigProvider.KEY_CALL_LOGS_ENABLED,
                    eventType = EventType.CALL,
                    identity = "screened_${callDetails.creationTimeMillis}",
                    contentPreview = content,
                    detailBody = EventFormatter.formatEventHtml(
                        type = EventType.CALL,
                        source = source,
                        timestampMillis = callDetails.creationTimeMillis,
                        content = content,
                        customTypeLabel = "SCREENED CALL"
                    ),
                    timestampMillis = callDetails.creationTimeMillis,
                    dedupeKey = "screened_${number}_${callDetails.creationTimeMillis / 10000}",
                    customTypeLabel = "SCREENED CALL"
                )
            )
            container.logRepository.addLog("Call screening: captured incoming call from $source.")
        }
    }
}
