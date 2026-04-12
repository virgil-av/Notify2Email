package com.notify2email.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.notify2email.app.R
import com.notify2email.app.collectors.calls.CallLogObserver
import com.notify2email.app.di.appContainer
import com.notify2email.app.email.SmtpConfigProvider
import com.notify2email.app.email.SharedPreferencesSmtpConfigProvider
import com.notify2email.app.notifications.PhoneNotificationListenerService
import com.notify2email.app.settings.SettingsActivity
import com.notify2email.app.util.ComponentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EventForegroundService : Service() {

    private var callLogObserver: CallLogObserver? = null
    private lateinit var smtpConfigProvider: SharedPreferencesSmtpConfigProvider
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        smtpConfigProvider = SharedPreferencesSmtpConfigProvider(applicationContext)
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
        serviceScope.launch {
            applicationContext.appContainer.serviceStateRepository.setServiceRunning(true)
            applicationContext.appContainer.logRepository.addLog("Foreground service started.")
            syncComponentStates()
            
            // Observe settings changes to update listeners in real-time
            applicationContext.appContainer.settingsRepository.observeSettings().collect {
                initializeListeners()
                syncComponentStates()
            }
        }
        initializeListeners()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                // Ensure service is marked as running even if listeners are still initializing
                serviceScope.launch {
                    applicationContext.appContainer.serviceStateRepository.setServiceRunning(true)
                }
                initializeListeners()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        callLogObserver?.unregister()
        callLogObserver = null
        
        serviceScope.launch {
            applicationContext.appContainer.serviceStateRepository.setServiceRunning(false)
            applicationContext.appContainer.logRepository.addLog("Foreground service stopped.")
            syncComponentStates(serviceRunning = false)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initializeListeners() {
        // We initialize listeners regardless of SMTP settings so we can at least LOG events locally
        val container = applicationContext.appContainer
        
        // Call Log Observer
        if (hasCallLogPermission() && container.smtpConfigProvider.isFeatureEnabled(SmtpConfigProvider.KEY_CALL_LOGS_ENABLED)) {
            if (callLogObserver == null) {
                Log.d(TAG, "Initializing CallLogObserver")
                callLogObserver = CallLogObserver(applicationContext).also { it.register() }
            }
        } else {
            callLogObserver?.unregister()
            callLogObserver = null
        }

        // Components (SMS Receiver)
        syncComponentStates(serviceRunning = true)
    }

    private fun syncComponentStates(serviceRunning: Boolean = true) {
        val smsEnabled = smtpConfigProvider.isFeatureEnabled(SmtpConfigProvider.KEY_SMS_ENABLED)
        ComponentManager.syncAll(applicationContext, serviceRunning, smsEnabled)
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            1001,
            launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.foreground_service_title))
            .setContentText(
                getString(
                    R.string.foreground_service_text,
                    if (PhoneNotificationListenerService.isAccessGranted(this)) {
                        getString(R.string.foreground_service_notification_access_on)
                    } else {
                        getString(R.string.foreground_service_notification_access_off)
                    }
                )
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.foreground_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.foreground_service_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "EventForegroundSvc"
        private const val NOTIFICATION_CHANNEL_ID = "event_foreground_service"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.notify2email.app.action.START_FOREGROUND"
        const val ACTION_STOP = "com.notify2email.app.action.STOP_FOREGROUND"

        fun start(context: Context) {
            val intent = Intent(context, EventForegroundService::class.java).apply {
                action = ACTION_START
            }

            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EventForegroundService::class.java))
        }
    }
}
