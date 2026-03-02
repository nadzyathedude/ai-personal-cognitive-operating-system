package com.example.speach_recognotion_llm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.assistant.models.CompositeStressIndex
import com.example.assistant.models.HrvData
import com.example.speach_recognotion_llm.data.ble.BleHeartRateManager
import com.example.speach_recognotion_llm.data.ble.SavedDevice
import com.example.speach_recognotion_llm.data.ble.ScannedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HrvUiState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isScanning: Boolean = false,
    val isStabilizing: Boolean = false,
    val connectedDeviceName: String? = null,
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val savedDevices: List<SavedDevice> = emptyList(),
    val currentHeartRate: Int = 0,
    val hrvData: HrvData? = null,
    val compositeStress: CompositeStressIndex? = null,
    val error: String? = null
)

class HrvViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleHeartRateManager(application)

    private val _state = MutableStateFlow(HrvUiState())
    val state: StateFlow<HrvUiState> = _state.asStateFlow()

    // Stabilization & median filter
    private var connectionTime: Long = 0
    private val hrMedianBuffer = mutableListOf<Int>()

    companion object {
        private const val STABILIZATION_MS = 2000L
        private const val MEDIAN_WINDOW = 3
    }

    init {
        setupBleCallbacks()
        loadSavedDevices()
    }

    private fun loadSavedDevices() {
        _state.update { it.copy(savedDevices = bleManager.getSavedDevices()) }
    }

    private fun medianHr(): Int {
        if (hrMedianBuffer.isEmpty()) return 0
        val sorted = hrMedianBuffer.sorted()
        return sorted[sorted.size / 2]
    }

    private fun setupBleCallbacks() {
        bleManager.onDeviceFound = { device ->
            _state.update { current ->
                val existing = current.scannedDevices.any { it.address == device.address }
                if (existing) current
                else current.copy(scannedDevices = current.scannedDevices + device)
            }
        }

        bleManager.onConnected = { name ->
            connectionTime = System.currentTimeMillis()
            hrMedianBuffer.clear()
            _state.update {
                it.copy(
                    isConnected = true,
                    isConnecting = false,
                    isScanning = false,
                    isStabilizing = false, // stays false until first HR data arrives
                    connectedDeviceName = name,
                    currentHeartRate = 0,
                    scannedDevices = emptyList(),
                    savedDevices = bleManager.getSavedDevices()
                )
            }
        }

        bleManager.onHrFilterReady = {
            // Known HR source detected — reset stabilization timer for clean data.
            hrMedianBuffer.clear()
            connectionTime = System.currentTimeMillis()
            _state.update { it.copy(isStabilizing = true, currentHeartRate = 0) }
        }

        bleManager.onDisconnected = {
            hrMedianBuffer.clear()
            _state.update {
                it.copy(
                    isConnected = false,
                    isConnecting = false,
                    isStabilizing = false,
                    connectedDeviceName = null,
                    currentHeartRate = 0
                )
            }
        }

        bleManager.onHeartRate = { measurement ->
            hrMedianBuffer.add(measurement.heartRate)
            if (hrMedianBuffer.size > MEDIAN_WINDOW) hrMedianBuffer.removeAt(0)

            val now = System.currentTimeMillis()
            val stabilizing = now - connectionTime < STABILIZATION_MS

            if (stabilizing) {
                _state.update { it.copy(isStabilizing = true) }
            } else {
                val hr = medianHr()
                _state.update { current ->
                    val updated = current.copy(
                        isStabilizing = false,
                        currentHeartRate = hr
                    )

                    // Estimate stress from HR when no RR-based HRV available
                    if (current.hrvData == null && hr > 0) {
                        val hrStress = when {
                            hr < 60  -> 0.15f
                            hr < 70  -> 0.25f
                            hr < 80  -> 0.35f
                            hr < 90  -> 0.50f
                            hr < 100 -> 0.65f
                            hr < 120 -> 0.80f
                            else     -> 0.90f
                        }
                        val voiceStress = current.compositeStress?.voiceStress ?: 0f
                        val textStress = current.compositeStress?.textStress ?: 0f
                        val composite = if (voiceStress > 0f || textStress > 0f) {
                            voiceStress * 0.4f + textStress * 0.3f + hrStress * 0.3f
                        } else {
                            hrStress
                        }
                        updated.copy(
                            compositeStress = CompositeStressIndex(
                                voiceStress = voiceStress,
                                textStress = textStress,
                                hrvStress = hrStress,
                                composite = composite.coerceIn(0f, 1f),
                                timestamp = System.currentTimeMillis().toString()
                            )
                        )
                    } else {
                        updated
                    }
                }
            }
        }

        bleManager.onHrvUpdate = { result ->
            val hrvData = HrvData(
                hrv = result.hrv,
                restingHr = result.avgHr,
                sleepScore = 0f,
                timestamp = System.currentTimeMillis().toString()
            )

            val hrvStress = when {
                result.hrv > 60 -> 0.2f
                result.hrv > 40 -> 0.5f
                result.hrv > 20 -> 0.7f
                else -> 0.9f
            }

            _state.update { current ->
                val voiceStress = current.compositeStress?.voiceStress ?: 0f
                val textStress = current.compositeStress?.textStress ?: 0f

                // Blend all stress sources
                val composite = if (voiceStress > 0f || textStress > 0f) {
                    voiceStress * 0.4f + textStress * 0.3f + hrvStress * 0.3f
                } else {
                    hrvStress
                }

                current.copy(
                    hrvData = hrvData,
                    compositeStress = CompositeStressIndex(
                        voiceStress = voiceStress,
                        textStress = textStress,
                        hrvStress = hrvStress,
                        composite = composite.coerceIn(0f, 1f),
                        timestamp = System.currentTimeMillis().toString()
                    )
                )
            }
        }

        bleManager.onScanFinished = {
            _state.update { current ->
                val noDevices = current.scannedDevices.isEmpty()
                current.copy(
                    isScanning = false,
                    error = if (noDevices) {
                        "No devices found.\n\n" +
                        "For Garmin watches:\n" +
                        "1. Enable Broadcast Heart Rate in watch settings\n" +
                        "2. Start an activity (Walk/Run) on the watch\n" +
                        "3. Then scan again"
                    } else current.error
                )
            }
        }

        bleManager.onError = { message ->
            _state.update { it.copy(error = message, isScanning = false, isConnecting = false) }
        }
    }

    fun startScan() {
        _state.update { it.copy(isScanning = true, scannedDevices = emptyList(), error = null) }
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
        _state.update { it.copy(isScanning = false) }
    }

    fun connectDevice(address: String) {
        _state.update { it.copy(isConnecting = true, isScanning = false, error = null) }
        bleManager.connect(address)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    fun updateStressFromMood(voiceStress: Float, textStress: Float) {
        _state.update { current ->
            val hrvStress = current.compositeStress?.hrvStress ?: 0f
            val hasHrv = current.hrvData != null

            // Weighted composite: voice 40%, text 30%, HRV 30% (when available)
            val composite = if (hasHrv) {
                voiceStress * 0.4f + textStress * 0.3f + hrvStress * 0.3f
            } else {
                voiceStress * 0.55f + textStress * 0.45f
            }

            current.copy(
                compositeStress = CompositeStressIndex(
                    voiceStress = voiceStress,
                    textStress = textStress,
                    hrvStress = hrvStress,
                    composite = composite.coerceIn(0f, 1f),
                    timestamp = System.currentTimeMillis().toString()
                )
            )
        }
    }

    fun removeSavedDevice(address: String) {
        bleManager.removeSavedDevice(address)
        _state.update { it.copy(savedDevices = bleManager.getSavedDevices()) }
    }

    fun setError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.destroy()
    }
}
