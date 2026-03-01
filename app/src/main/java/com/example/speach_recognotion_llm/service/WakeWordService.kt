package com.example.speach_recognotion_llm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.speach_recognotion_llm.BuildConfig
import com.example.speach_recognotion_llm.MainActivity
import com.example.speach_recognotion_llm.R
import com.example.speach_recognotion_llm.data.audio.AssistantState
import com.example.speach_recognotion_llm.data.audio.AssistantStateManager
import com.example.speach_recognotion_llm.data.audio.WakeWordManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "wake_word_channel"
        const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "speach_recognotion_llm:WakeWordWakeLock"

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }

    private var wakeWordManager: WakeWordManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()

        wakeWordManager = WakeWordManager(
            context = applicationContext,
            accessKey = BuildConfig.PORCUPINE_ACCESS_KEY,
            onWakeWordDetected = {
                if (AssistantStateManager.onWakeWordDetected()) {
                    wakeWordManager?.stop()
                    AssistantStateManager.onReadyToListen()
                }
            },
            onError = { errorMsg ->
                AssistantStateManager.onError(errorMsg)
            }
        )

        serviceScope.launch {
            AssistantStateManager.state.collect { state ->
                when (state) {
                    is AssistantState.Idle -> {
                        if (wakeWordManager?.isActive == false) {
                            wakeWordManager?.start()
                        }
                        updateNotification("Listening for wake word...")
                    }
                    is AssistantState.WakeDetected -> {
                        updateNotification("Wake word detected!")
                    }
                    is AssistantState.Listening -> {
                        updateNotification("Recording your question...")
                    }
                    is AssistantState.Processing -> {
                        updateNotification("Processing...")
                    }
                    is AssistantState.Responding -> {
                        updateNotification("Assistant is responding...")
                    }
                    is AssistantState.Error -> {
                        updateNotification("Error: ${state.message}")
                        delay(2000)
                        AssistantStateManager.reset()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Listening for wake word...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (wakeWordManager?.isActive == false &&
            AssistantStateManager.currentState is AssistantState.Idle
        ) {
            wakeWordManager?.start()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeWordManager?.destroy()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Wake word detection is active"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Assistant")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
