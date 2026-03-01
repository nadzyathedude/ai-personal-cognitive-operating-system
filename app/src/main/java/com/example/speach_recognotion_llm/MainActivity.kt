package com.example.speach_recognotion_llm

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.speach_recognotion_llm.ui.screen.ChatScreen
import com.example.speach_recognotion_llm.ui.theme.Speach_recognotion_llmTheme
import com.example.speach_recognotion_llm.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleRecording()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Speach_recognotion_llmTheme {
                ChatScreen(
                    viewModel = viewModel,
                    onMicClick = { handleMicClick() }
                )
            }
        }
    }

    private fun handleMicClick() {
        if (!viewModel.hasMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.toggleRecording()
        }
    }
}
