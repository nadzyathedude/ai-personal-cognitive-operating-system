package com.example.speach_recognotion_llm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.speach_recognotion_llm.service.WakeWordService
import com.example.speach_recognotion_llm.ui.screen.ChatScreen
import com.example.speach_recognotion_llm.ui.theme.Speach_recognotion_llmTheme
import com.example.speach_recognotion_llm.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startWakeWordServiceIfReady()
            viewModel.toggleRecording()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startWakeWordServiceIfReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        startWakeWordServiceIfReady()

        setContent {
            Speach_recognotion_llmTheme {
                ChatScreen(
                    viewModel = viewModel,
                    onMicClick = { handleMicClick() }
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startWakeWordServiceIfReady() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            WakeWordService.start(this)
        }
    }

    private fun handleMicClick() {
        if (!viewModel.hasMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.toggleRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            WakeWordService.stop(this)
        }
    }
}
