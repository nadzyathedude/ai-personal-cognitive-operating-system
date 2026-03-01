package com.example.speach_recognotion_llm.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.speach_recognotion_llm.data.model.ChatMessage
import com.example.speach_recognotion_llm.data.remote.ConnectionState
import com.example.speach_recognotion_llm.ui.state.ChatUiState
import com.example.speach_recognotion_llm.ui.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onMicClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ConnectionBanner(state.connectionState)

            ChatMessageList(
                messages = state.messages,
                liveTranscription = state.liveTranscription,
                isProcessing = state.isProcessing,
                modifier = Modifier.weight(1f)
            )

            BottomBar(
                state = state,
                onMicClick = onMicClick
            )
        }
    }
}

@Composable
private fun ConnectionBanner(connectionState: ConnectionState) {
    if (connectionState != ConnectionState.AUTHENTICATED && connectionState != ConnectionState.CONNECTED) {
        val (text, color) = when (connectionState) {
            ConnectionState.CONNECTING -> "Connecting..." to MaterialTheme.colorScheme.tertiary
            ConnectionState.RECONNECTING -> "Reconnecting..." to MaterialTheme.colorScheme.error
            ConnectionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.error
            else -> return
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.15f))
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    liveTranscription: String,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (messages.isEmpty() && liveTranscription.isEmpty() && !isProcessing) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tap the microphone to start",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message)
            }

            if (liveTranscription.isNotEmpty()) {
                item(key = "live_transcription") {
                    LiveTranscriptionBubble(liveTranscription)
                }
            }

            if (isProcessing) {
                item(key = "processing") {
                    ProcessingIndicator()
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "You" else "Assistant",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (message.isStreaming) {
                    val transition = rememberInfiniteTransition(label = "cursor")
                    val alpha by transition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "cursor_alpha"
                    )
                    Text(
                        text = "\u2588",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTranscriptionBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ProcessingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    state: ChatUiState,
    onMicClick: () -> Unit
) {
    val micEnabled = state.connectionState == ConnectionState.AUTHENTICATED && !state.isProcessing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = state.isRecording,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = "Listening...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val buttonColor by animateColorAsState(
            targetValue = if (state.isRecording) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            label = "mic_color"
        )

        val transition = rememberInfiniteTransition(label = "pulse")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = if (state.isRecording) 1.15f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )

        FloatingActionButton(
            onClick = onMicClick,
            shape = CircleShape,
            containerColor = buttonColor,
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
            modifier = Modifier
                .size(72.dp)
                .scale(if (state.isRecording) scale else 1f)
        ) {
            Icon(
                imageVector = when {
                    state.isRecording -> Icons.Default.Stop
                    !micEnabled -> Icons.Default.MicOff
                    else -> Icons.Default.Mic
                },
                contentDescription = if (state.isRecording) "Stop recording" else "Start recording",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
