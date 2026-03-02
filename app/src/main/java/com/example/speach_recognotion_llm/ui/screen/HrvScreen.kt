package com.example.speach_recognotion_llm.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speach_recognotion_llm.R
import com.example.speach_recognotion_llm.data.ble.SavedDevice
import com.example.speach_recognotion_llm.data.ble.ScannedDevice
import com.example.speach_recognotion_llm.ui.viewmodel.HrvViewModel

@Composable
fun HrvScreen(
    viewModel: HrvViewModel,
    onRequestBlePermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title
        Text(
            text = stringResource(R.string.hrv_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Connection card
        BleConnectionCard(
            isConnected = state.isConnected,
            isConnecting = state.isConnecting,
            isScanning = state.isScanning,
            deviceName = state.connectedDeviceName,
            currentHr = if (state.isStabilizing) 0 else state.currentHeartRate,
            onScan = { onRequestBlePermissions() },
            onStopScan = { viewModel.stopScan() },
            onDisconnect = { viewModel.disconnect() }
        )

        // Error message
        if (state.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Snackbar(
                action = {
                    TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
                }
            ) {
                Text(state.error!!)
            }
        }

        // Scanned devices list
        if (state.isScanning || state.scannedDevices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            ScannedDevicesList(
                devices = state.scannedDevices,
                isScanning = state.isScanning,
                onConnect = { viewModel.connectDevice(it.address) }
            )
        }

        // Previously connected devices
        if (!state.isConnected && !state.isConnecting && !state.isScanning && state.savedDevices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            SavedDevicesList(
                devices = state.savedDevices,
                onConnect = { viewModel.connectDevice(it.address) },
                onRemove = { viewModel.removeSavedDevice(it.address) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.isConnected && state.isStabilizing) {
            // Calibrating — collecting initial HR samples
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.hrv_calibrating),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (state.isConnected && state.currentHeartRate > 0) {
            // Show live heart rate
            if (state.hrvData != null) {
                val hrv = state.hrvData!!

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        icon = Icons.Default.MonitorHeart,
                        label = stringResource(R.string.hrv_current),
                        value = "%.0f".format(hrv.hrv),
                        unit = stringResource(R.string.hrv_ms),
                        quality = hrvQuality(hrv.hrv),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        icon = Icons.Default.Favorite,
                        label = stringResource(R.string.hrv_resting_hr),
                        value = "%.0f".format(hrv.restingHr),
                        unit = stringResource(R.string.hrv_bpm),
                        quality = hrQuality(hrv.restingHr),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            state.compositeStress?.let { stress ->
                StressCard(stress)
            }
        } else if (state.isConnected && state.currentHeartRate == 0) {
            // Connected but waiting for HR data
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.hrv_waiting_hr),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (!state.isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.hrv_no_data),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BleConnectionCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    isScanning: Boolean,
    deviceName: String?,
    currentHr: Int,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isConnected -> MaterialTheme.colorScheme.primaryContainer
                isConnecting -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when {
                    isScanning -> Icons.Default.BluetoothSearching
                    isConnected || isConnecting -> Icons.Default.BluetoothConnected
                    else -> Icons.Default.Bluetooth
                }
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isConnected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.hrv_garmin_status),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = when {
                            isConnected -> deviceName ?: stringResource(R.string.hrv_connected)
                            isConnecting -> stringResource(R.string.connection_connecting)
                            isScanning -> stringResource(R.string.hrv_scanning)
                            else -> stringResource(R.string.hrv_not_connected)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (isConnected && currentHr > 0) {
                        HeartRateIndicator(heartRate = currentHr)
                    }
                }
            }

            when {
                isConnected -> {
                    IconButton(onClick = onDisconnect) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                isConnecting -> {
                    // No action button while connecting
                }
                isScanning -> {
                    OutlinedButton(onClick = onStopScan) {
                        Text(stringResource(R.string.hrv_stop_scan))
                    }
                }
                else -> {
                    Button(
                        onClick = onScan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(R.string.hrv_scan))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartRateIndicator(heartRate: Int) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (60000 / heartRate.coerceAtLeast(40))),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(14.dp).alpha(alpha)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$heartRate ${stringResource(R.string.hrv_bpm)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Red,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ScannedDevicesList(
    devices: List<ScannedDevice>,
    isScanning: Boolean,
    onConnect: (ScannedDevice) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.hrv_scanning),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (devices.isEmpty() && isScanning) {
                Text(
                    text = stringResource(R.string.hrv_searching),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Sort: HR-capable devices first, then by signal strength
            val sorted = devices.sortedWith(compareByDescending<ScannedDevice> { it.hasHrService }.thenByDescending { it.rssi })
            sorted.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (device.hasHrService) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "HR",
                                    tint = Color.Red,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = if (device.hasHrService) "Heart Rate ready" else "${device.rssi} dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (device.hasHrService) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Button(
                        onClick = { onConnect(device) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (device.hasHrService) MaterialTheme.colorScheme.primary
                                             else MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(stringResource(R.string.hrv_connect))
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedDevicesList(
    devices: List<SavedDevice>,
    onConnect: (SavedDevice) -> Unit,
    onRemove: (SavedDevice) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.hrv_saved_devices),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            devices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row {
                        IconButton(
                            onClick = { onRemove(device) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Button(
                            onClick = { onConnect(device) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(stringResource(R.string.hrv_connect))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    unit: String,
    quality: Pair<String, androidx.compose.ui.graphics.Color>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = quality.first,
                style = MaterialTheme.typography.labelSmall,
                color = quality.second,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StressCard(stress: com.example.assistant.models.CompositeStressIndex) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.hrv_stress_index),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (stress.voiceStress > 0f) {
                StressBar(label = stringResource(R.string.hrv_voice_stress), value = stress.voiceStress)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (stress.textStress > 0f) {
                StressBar(label = stringResource(R.string.hrv_text_stress), value = stress.textStress)
                Spacer(modifier = Modifier.height(8.dp))
            }
            StressBar(label = stringResource(R.string.hrv_hrv_stress), value = stress.hrvStress)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.hrv_composite),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "%.0f%%".format(stress.composite * 100),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = stressColor(stress.composite)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { stress.composite },
                modifier = Modifier.fillMaxWidth(),
                color = stressColor(stress.composite),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun StressBar(label: String, value: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "%.0f%%".format(value * 100),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.fillMaxWidth(),
            color = stressColor(value),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun stressColor(value: Float): androidx.compose.ui.graphics.Color {
    return when {
        value < 0.3f -> MaterialTheme.colorScheme.primary
        value < 0.6f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun hrvQuality(hrv: Float): Pair<String, androidx.compose.ui.graphics.Color> {
    return when {
        hrv >= 60 -> stringResource(R.string.hrv_excellent) to MaterialTheme.colorScheme.primary
        hrv >= 40 -> stringResource(R.string.hrv_good) to MaterialTheme.colorScheme.tertiary
        hrv >= 20 -> stringResource(R.string.hrv_normal) to MaterialTheme.colorScheme.secondary
        else -> stringResource(R.string.hrv_low) to MaterialTheme.colorScheme.error
    }
}

@Composable
private fun hrQuality(hr: Float): Pair<String, androidx.compose.ui.graphics.Color> {
    // Labels describe the HR level itself, not fitness quality
    return when {
        hr < 60 -> stringResource(R.string.hrv_hr_low) to MaterialTheme.colorScheme.tertiary
        hr < 80 -> stringResource(R.string.hrv_normal) to MaterialTheme.colorScheme.primary
        hr < 100 -> stringResource(R.string.hrv_hr_elevated) to MaterialTheme.colorScheme.secondary
        else -> stringResource(R.string.hrv_hr_high) to MaterialTheme.colorScheme.error
    }
}
