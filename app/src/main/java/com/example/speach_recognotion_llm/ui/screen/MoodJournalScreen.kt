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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.speach_recognotion_llm.R
import com.example.speach_recognotion_llm.data.model.MoodPhase
import com.example.speach_recognotion_llm.data.model.ToneAnalysisResult
import com.example.speach_recognotion_llm.ui.viewmodel.MoodViewModel

@Composable
fun MoodJournalScreen(viewModel: MoodViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.moodState.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.mood_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (state.phase) {
                MoodPhase.NOT_STARTED -> {
                    Button(onClick = { viewModel.startMoodSession() }) {
                        Text(stringResource(R.string.mood_start_checkin))
                    }
                }

                MoodPhase.INITIAL_QUESTION -> {
                    QuestionCard(
                        question = state.initialQuestion,
                        isRecording = state.isRecordingVoice,
                        hasAudio = state.lastAudioBytes != null,
                        onStartRecording = { viewModel.startVoiceRecording() },
                        onStopRecording = {
                            viewModel.stopVoiceRecording()
                            viewModel.submitVoiceAnswer()
                        },
                        onSubmit = { viewModel.submitInitialAnswer(it) }
                    )
                }

                MoodPhase.PROCESSING_INITIAL, MoodPhase.PROCESSING_FOLLOWUP -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.mood_analyzing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                MoodPhase.FOLLOWUP_QUESTIONS -> {
                    ProgressBar(current = state.currentQuestionIndex + 1, total = 3)
                    Spacer(modifier = Modifier.height(16.dp))

                    state.emotion?.let { emotion ->
                        EmotionBadge(emotion.primaryEmotion, emotion.sentiment)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    state.toneResult?.let { tone ->
                        ToneResultCard(tone)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (state.currentQuestionIndex < state.followupQuestions.size) {
                        QuestionCard(
                            question = state.followupQuestions[state.currentQuestionIndex],
                            isRecording = state.isRecordingVoice,
                            hasAudio = state.lastAudioBytes != null,
                            onStartRecording = { viewModel.startVoiceRecording() },
                            onStopRecording = {
                                viewModel.stopVoiceRecording()
                                viewModel.submitVoiceAnswer()
                            },
                            onSubmit = { viewModel.submitFollowupAnswer(it) }
                        )
                    }
                }

                MoodPhase.COMPLETE -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.mood_session_complete),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    state.toneResult?.let { tone ->
                        ToneResultCard(tone)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    SummaryCard(summary = state.summary)

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.resetMoodSession() }) {
                        Text(stringResource(R.string.mood_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(current: Int, total: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.mood_followup_title),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "$current/$total",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { current.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmotionBadge(emotion: String, sentiment: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (sentiment) {
                "positive" -> MaterialTheme.colorScheme.primaryContainer
                "negative" -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Text(
            text = stringResource(R.string.mood_detected, emotion),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun QuestionCard(
    question: String,
    isRecording: Boolean,
    hasAudio: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var answer by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Voice recording button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val transition = rememberInfiniteTransition(label = "mic_pulse")
                val pulse by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isRecording) 1.2f else 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(if (isRecording) 500 else 800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                val buttonColor by animateColorAsState(
                    targetValue = if (isRecording)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    label = "mic_color"
                )

                FloatingActionButton(
                    onClick = {
                        if (isRecording) {
                            onStopRecording()
                        } else {
                            onStartRecording()
                        }
                    },
                    shape = CircleShape,
                    containerColor = buttonColor,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp),
                    modifier = Modifier
                        .size(56.dp)
                        .scale(pulse)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording)
                            stringResource(R.string.mood_voice_stop)
                        else
                            stringResource(R.string.mood_voice_record),
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Recording status text
            Text(
                text = when {
                    isRecording -> stringResource(R.string.mood_voice_listening)
                    hasAudio -> stringResource(R.string.mood_voice_recorded)
                    else -> stringResource(R.string.mood_voice_record)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isRecording)
                    MaterialTheme.colorScheme.error
                else if (hasAudio)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.mood_voice_or),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Text input
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.mood_type_answer)) },
                minLines = 3,
                maxLines = 6,
                trailingIcon = {
                    AnimatedVisibility(
                        visible = answer.isNotBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = {
                            if (answer.isNotBlank()) {
                                onSubmit(answer)
                                answer = ""
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.mood_submit)
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ToneResultCard(tone: ToneAnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tone_voice_analysis),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Acoustic emotion + tone descriptor
            if (tone.acousticEmotion.isNotBlank()) {
                ToneRow(
                    label = stringResource(R.string.tone_acoustic_emotion),
                    value = tone.acousticEmotion
                )
            }
            if (tone.toneDescriptor.isNotBlank()) {
                ToneRow(
                    label = stringResource(R.string.tone_descriptor),
                    value = tone.toneDescriptor
                )
            }

            // Vocal stress bar
            if (tone.vocalStressScore > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.tone_vocal_stress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { tone.vocalStressScore.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = stressColor(tone.vocalStressScore),
                )
            }

            // Fusion results
            tone.fusionEmotion?.let { fusionEmotion ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.tone_fusion),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                ToneRow(
                    label = stringResource(R.string.tone_acoustic_emotion),
                    value = fusionEmotion
                )
                tone.compositeTone?.let {
                    ToneRow(label = stringResource(R.string.tone_descriptor), value = it)
                }
            }

            // Mismatch warning
            if (tone.mismatchDetected) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.tone_mismatch_warning),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            tone.reflectivePrompt?.let { prompt ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun stressColor(stress: Float) = when {
    stress < 0.3f -> MaterialTheme.colorScheme.primary
    stress < 0.6f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun SummaryCard(summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.mood_reflection),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
