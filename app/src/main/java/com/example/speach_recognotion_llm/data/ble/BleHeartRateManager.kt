package com.example.speach_recognotion_llm.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID
import java.util.LinkedList
import kotlin.math.sqrt

data class HrMeasurement(
    val heartRate: Int,
    val rrIntervals: List<Int>, // in milliseconds
    val timestamp: Long = System.currentTimeMillis()
)

data class HrvResult(
    val hrv: Float,       // RMSSD in ms
    val avgHr: Float,     // average heart rate
    val timestamp: Long = System.currentTimeMillis()
)

data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val hasHrService: Boolean = false
)

data class SavedDevice(
    val name: String,
    val address: String
)

@SuppressLint("MissingPermission")
class BleHeartRateManager(private val context: Context) {

    companion object {
        private const val TAG = "BleHR"

        // Standard BLE Heart Rate Profile
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Garmin ML service UUID — used only to detect Garmin devices
        val GARMIN_ML_SERVICE_UUID: UUID = UUID.fromString("6a4e2800-667b-11e3-949a-0800200c9a66")

        private const val RR_BUFFER_SIZE = 120
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val HR_DATA_TIMEOUT_MS = 20_000L
        private const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val PREFS_NAME = "ble_saved_devices"
        private const val KEY_DEVICES = "saved_devices"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null

    private val rrBuffer = mutableListOf<Int>()
    private val hrBuffer = mutableListOf<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Queue for sequential GATT descriptor writes
    private val descriptorWriteQueue = LinkedList<BluetoothGattDescriptor>()
    @Volatile
    private var isWritingDescriptor = false

    @Volatile
    private var receivedHrData = false

    // Cancellable runnable for delayed service discovery
    private var discoverServicesRunnable: Runnable? = null

    // Retry counter for stale GATT cache (HR service not found)
    @Volatile
    private var serviceDiscoveryRetries = 0
    private val MAX_SERVICE_DISCOVERY_RETRIES = 1


    var onDeviceFound: ((ScannedDevice) -> Unit)? = null
    var onScanFinished: (() -> Unit)? = null
    var onConnected: ((String) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onHeartRate: ((HrMeasurement) -> Unit)? = null
    var onHrvUpdate: ((HrvResult) -> Unit)? = null
    var onHrFilterReady: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    @Volatile
    private var isScanning = false

    @Volatile
    private var connectedDeviceName: String? = null

    // Auto-reconnect state
    @Volatile
    private var lastDeviceAddress: String? = null
    @Volatile
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 3
    @Volatile
    private var suppressAutoReconnect = false

    val isConnected: Boolean get() = gatt != null && connectedDeviceName != null
    val deviceName: String? get() = connectedDeviceName

    private val seenAddresses = mutableSetOf<String>()

    // --- Saved Devices ---

    fun getSavedDevices(): List<SavedDevice> {
        val raw = prefs.getStringSet(KEY_DEVICES, emptySet()) ?: emptySet()
        return raw.mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) SavedDevice(name = parts[1], address = parts[0])
            else null
        }.sortedBy { it.name }
    }

    private fun saveDevice(name: String, address: String) {
        val current = prefs.getStringSet(KEY_DEVICES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.removeAll { it.startsWith("$address|") }
        current.add("$address|$name")
        prefs.edit().putStringSet(KEY_DEVICES, current).apply()
    }

    fun removeSavedDevice(address: String) {
        val current = prefs.getStringSet(KEY_DEVICES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.removeAll { it.startsWith("$address|") }
        prefs.edit().putStringSet(KEY_DEVICES, current).apply()
    }

    // --- Scanning ---

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address ?: return
            if (address in seenAddresses) return
            seenAddresses.add(address)

            val name = try { device.name } catch (_: SecurityException) { null }
                ?: result.scanRecord?.deviceName
            if (name.isNullOrBlank()) return

            val serviceUuids = result.scanRecord?.serviceUuids
            val hasHrService = serviceUuids?.any { it.uuid == HR_SERVICE_UUID } == true
            if (hasHrService) {
                Log.i(TAG, "Device $name ($address) advertises HR service!")
            }

            onDeviceFound?.invoke(ScannedDevice(name, address, result.rssi, hasHrService))
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            isScanning = false
            onError?.invoke("BLE scan failed (code $errorCode)")
        }
    }

    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            onError?.invoke("Bluetooth is not enabled")
            return
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            onError?.invoke("BLE scanner not available")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        seenAddresses.clear()

        try {
            scanner?.startScan(null, settings, scanCallback)
            isScanning = true
            handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
        } catch (e: SecurityException) {
            onError?.invoke("Bluetooth permission denied")
        }
    }

    private val scanTimeoutRunnable = Runnable {
        if (isScanning) {
            stopScan()
            onScanFinished?.invoke()
        }
    }

    private val connectionTimeoutRunnable = Runnable {
        if (gatt != null && connectedDeviceName == null) {
            Log.w(TAG, "Connection timeout — device did not respond")
            cleanupGatt()
            onError?.invoke(
                "Connection timed out.\n\n" +
                "Make sure your device is nearby with Bluetooth enabled.\n" +
                "For Garmin: try toggling Bluetooth off/on on the watch."
            )
            onDisconnected?.invoke()
        }
    }

    private val hrDataTimeoutRunnable = Runnable {
        if (isConnected && !receivedHrData) {
            Log.w(TAG, "No HR data received within timeout")
            onError?.invoke(
                "No heart rate data received.\n\n" +
                "For Garmin watches:\n" +
                "1. Settings \u2192 Sensors & Accessories \u2192 Wrist Heart Rate\n" +
                "2. Enable \"Broadcast Heart Rate\"\n" +
                "3. Start an activity (Walk/Run) on the watch\n" +
                "4. Reconnect here"
            )
        }
    }

    fun stopScan() {
        handler.removeCallbacks(scanTimeoutRunnable)
        if (isScanning) {
            try {
                scanner?.stopScan(scanCallback)
            } catch (_: Exception) {}
            isScanning = false
        }
    }


    // --- Connection ---

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "onConnectionStateChange: status=$status newState=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                handler.removeCallbacks(connectionTimeoutRunnable)
                Log.e(TAG, "GATT connection error: status=$status newState=$newState")
                val address = lastDeviceAddress
                connectedDeviceName = null
                receivedHrData = false

                cleanupGatt()
                if (address != null && !suppressAutoReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++
                    Log.i(TAG, "Connection error — auto-reconnect attempt $reconnectAttempts")
                    handler.postDelayed({ connect(address) }, 2000)
                } else {
                    onError?.invoke("Connection failed (status $status)")
                    onDisconnected?.invoke()
                }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    handler.removeCallbacks(connectionTimeoutRunnable)
                    val name = try { gatt.device.name } catch (_: SecurityException) { null }
                        ?: gatt.device.address
                    Log.i(TAG, "Connected to $name")
                    connectedDeviceName = name
                    lastDeviceAddress = gatt.device.address
                    val isReconnect = reconnectAttempts > 0
                    reconnectAttempts = 0
                    receivedHrData = false
    
                    synchronized(rrBuffer) {
                        rrBuffer.clear()
                        hrBuffer.clear()
                    }
                    saveDevice(name, gatt.device.address)
                    onConnected?.invoke(name)

                    // Cancel any leftover discover runnable from a previous connection
                    discoverServicesRunnable?.let { handler.removeCallbacks(it) }

                    val delay: Long
                    if (!isReconnect) {
                        val refreshed = refreshGattCache(gatt)
                        Log.i(TAG, "GATT cache refresh: $refreshed")
                        delay = if (refreshed) 1500 else 800
                    } else {
                        Log.i(TAG, "Reconnect — skipping cache refresh")
                        delay = 800
                    }

                    // Capture the callback's gatt param directly (guaranteed correct object)
                    val connectedGatt = gatt
                    discoverServicesRunnable = Runnable {
                        Log.i(TAG, "Discovering services...")
                        try {
                            val ok = connectedGatt.discoverServices()
                            Log.i(TAG, "discoverServices() returned: $ok")
                            if (!ok) {
                                onError?.invoke("Service discovery failed to start. Try reconnecting.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "discoverServices() exception: ${e.message}")
                        }
                    }
                    handler.postDelayed(discoverServicesRunnable!!, delay)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    handler.removeCallbacks(connectionTimeoutRunnable)
                    Log.i(TAG, "Disconnected")
                    val wasConnected = connectedDeviceName != null
                    connectedDeviceName = null
                    receivedHrData = false
    
                    cleanupGatt()

                    // Auto-reconnect on unexpected disconnect
                    val address = lastDeviceAddress
                    if (wasConnected && address != null && !suppressAutoReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        reconnectAttempts++
                        Log.i(TAG, "Auto-reconnect attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS")
                        handler.postDelayed({ connect(address) }, 2000)
                    } else {
                        onDisconnected?.invoke()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onError?.invoke("Service discovery failed (status $status)")
                return
            }

            // Log all discovered services & characteristics
            for (service in gatt.services) {
                Log.i(TAG, "Found service: ${service.uuid}")
                for (char in service.characteristics) {
                    Log.i(TAG, "  Char: ${char.uuid} props=${char.properties}")
                }
            }

            receivedHrData = false
            descriptorWriteQueue.clear()
            isWritingDescriptor = false

            // 1. Look for standard BLE Heart Rate service (0x180D / 0x2A37)
            var hrChar: BluetoothGattCharacteristic? = null
            for (service in gatt.services) {
                if (service.uuid == HR_SERVICE_UUID) {
                    val char = service.getCharacteristic(HR_MEASUREMENT_UUID)
                    if (char != null && (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        hrChar = char
                        break
                    }
                }
            }

            if (hrChar != null) {
                // Standard HR found — subscribe to it only
                Log.i(TAG, "Standard HR characteristic found, subscribing...")
                gatt.setCharacteristicNotification(hrChar, true)
                val descriptor = hrChar.getDescriptor(CCC_DESCRIPTOR_UUID)
                if (descriptor != null) {
                    descriptorWriteQueue.add(descriptor)
                    writeNextDescriptor(gatt)
                } else {
                    Log.w(TAG, "No CCC descriptor for HR characteristic")
                }
                handler.postDelayed(hrDataTimeoutRunnable, HR_DATA_TIMEOUT_MS)
                return
            }

            // Standard HR not found
            Log.w(TAG, "Standard HR service (0x180D) not found in GATT")

            // 2. Try GATT cache retry (reconnect for fresh service discovery)
            if (serviceDiscoveryRetries < MAX_SERVICE_DISCOVERY_RETRIES) {
                serviceDiscoveryRetries++
                Log.w(TAG, "Retry $serviceDiscoveryRetries — closing GATT for fresh discovery")
                val address = lastDeviceAddress
                connectedDeviceName = null
                cleanupGatt()
                // Don't fire onDisconnected — this is an internal retry, not a real disconnect
                if (address != null) {
                    handler.postDelayed({ internalConnect(address) }, 2000)
                }
                return
            }

            // 3. Check if this is a Garmin device (ML service present)
            val isGarminDevice = gatt.services.any { it.uuid == GARMIN_ML_SERVICE_UUID }

            // No HR service after retries — suppress auto-reconnect (won't help)
            suppressAutoReconnect = true

            if (isGarminDevice) {
                Log.w(TAG, "Garmin device detected but standard HR broadcast not active")
                onError?.invoke(
                    "Garmin device connected but HR broadcast is off.\n\n" +
                    "On your watch:\n" +
                    "1. Settings \u2192 Sensors & Accessories\n" +
                    "2. Wrist Heart Rate \u2192 Broadcast Heart Rate \u2192 ON\n" +
                    "3. Start an activity (Walk/Run)\n" +
                    "4. Then reconnect here"
                )
            } else {
                Log.w(TAG, "No heart rate source found on this device")
                onError?.invoke(
                    "No heart rate service found.\n\n" +
                    "Make sure your device supports BLE Heart Rate broadcast."
                )
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.i(TAG, "Descriptor write complete for ${descriptor.characteristic.uuid}: status=$status")
            isWritingDescriptor = false

            if (descriptorWriteQueue.isNotEmpty()) {
                writeNextDescriptor(gatt)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.i(TAG, "Characteristic write for ${characteristic.uuid}: status=$status")
        }

        // API 33+ callback — value passed directly
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            routeCharacteristicData(characteristic.uuid, value)
        }

        // Pre-API 33 fallback
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            routeCharacteristicData(characteristic.uuid, data)
        }
    }

    private fun routeCharacteristicData(charUuid: UUID, data: ByteArray) {
        when (charUuid) {
            HR_MEASUREMENT_UUID -> handleStandardHrData(data)
            else -> Log.d(TAG, "Ignoring data from $charUuid: ${data.map { it.toInt() and 0xFF }}")
        }
    }

    private fun handleStandardHrData(data: ByteArray) {
        if (data.isEmpty()) return

        Log.d(TAG, "Standard HR data: ${data.map { it.toInt() and 0xFF }}")

        val measurement = parseHeartRate(data)

        if (measurement.heartRate in 30..220) {
            if (!receivedHrData) {
                receivedHrData = true
                handler.removeCallbacks(hrDataTimeoutRunnable)
                onHrFilterReady?.invoke()
            }

            onHeartRate?.invoke(measurement)

            if (measurement.rrIntervals.isNotEmpty()) {
                synchronized(rrBuffer) {
                    rrBuffer.addAll(measurement.rrIntervals)
                    hrBuffer.add(measurement.heartRate)
                    while (rrBuffer.size > RR_BUFFER_SIZE) rrBuffer.removeAt(0)
                    while (hrBuffer.size > RR_BUFFER_SIZE) hrBuffer.removeAt(0)
                }

                if (rrBuffer.size >= 30) {
                    val hrv = calculateHrv()
                    if (hrv != null) {
                        onHrvUpdate?.invoke(hrv)
                    }
                }
            }
        } else {
            Log.w(TAG, "HR out of range: ${measurement.heartRate} (raw: ${data.map { it.toInt() and 0xFF }})")
        }
    }

    @SuppressLint("NewApi")
    private fun writeNextDescriptor(gatt: BluetoothGatt) {
        if (isWritingDescriptor) return
        val descriptor = descriptorWriteQueue.poll() ?: return
        isWritingDescriptor = true
        Log.i(TAG, "Writing CCC descriptor for ${descriptor.characteristic.uuid}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: must pass value explicitly; the old descriptor.value setter is ignored
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    fun connect(deviceAddress: String) {
        serviceDiscoveryRetries = 0
        suppressAutoReconnect = false
        internalConnect(deviceAddress)
    }

    /**
     * Internal connect that preserves serviceDiscoveryRetries.
     * Used by GATT cache retry to avoid resetting the counter.
     */
    private fun internalConnect(deviceAddress: String) {
        stopScan()
        cleanupGatt()


        val device: BluetoothDevice = try {
            bluetoothAdapter?.getRemoteDevice(deviceAddress)
        } catch (_: Exception) { null } ?: run {
            onError?.invoke("Device not found")
            return
        }

        try {
            Log.i(TAG, "Connecting to $deviceAddress...")
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                onError?.invoke("Failed to create GATT connection")
            } else {
                handler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT_MS)
            }
        } catch (e: SecurityException) {
            onError?.invoke("Bluetooth permission denied")
        }
    }

    fun disconnect() {
        // Clear reconnect state so we don't auto-reconnect after manual disconnect
        lastDeviceAddress = null
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS
        connectedDeviceName = null
        receivedHrData = false

        // For user-initiated disconnect, call disconnect() then close()
        try { gatt?.disconnect() } catch (_: Exception) {}
        cleanupGatt() // cancels runnables, closes
        onDisconnected?.invoke()
    }

    fun destroy() {
        handler.removeCallbacks(hrDataTimeoutRunnable)
        stopScan()
        disconnect()
    }

    private fun refreshGattCache(gatt: BluetoothGatt): Boolean {
        return try {
            val method = gatt.javaClass.getMethod("refresh")
            method.invoke(gatt) as? Boolean ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh GATT cache: ${e.message}")
            false
        }
    }

    private fun cleanupGatt() {
        // Cancel any pending delayed calls (discoverServices, reconnect, timeout)
        discoverServicesRunnable?.let { handler.removeCallbacks(it) }
        discoverServicesRunnable = null
        handler.removeCallbacks(connectionTimeoutRunnable)
        handler.removeCallbacks(hrDataTimeoutRunnable)
        descriptorWriteQueue.clear()
        isWritingDescriptor = false
        // close() releases all GATT resources and cancels pending callbacks.
        // Do NOT call disconnect() here — it triggers async callbacks that can
        // race with a new connectGatt() call.
        val g = gatt
        gatt = null  // null out first so callbacks see it's gone
        try {
            g?.close()
        } catch (_: Exception) {}
    }

    // --- Parsing ---

    private fun parseHeartRate(data: ByteArray): HrMeasurement {
        if (data.isEmpty()) return HrMeasurement(0, emptyList())

        val flags = data[0].toInt() and 0xFF
        val isHrFormat16 = (flags and 0x01) != 0
        val hasRrIntervals = (flags and 0x10) != 0

        var offset = 1
        val heartRate: Int
        if (isHrFormat16) {
            if (data.size < 3) return HrMeasurement(0, emptyList())
            heartRate = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
        } else {
            if (data.size < 2) return HrMeasurement(0, emptyList())
            heartRate = data[offset].toInt() and 0xFF
            offset += 1
        }

        // Skip energy expended if present
        if ((flags and 0x08) != 0) {
            offset += 2
        }

        val rrIntervals = mutableListOf<Int>()
        if (hasRrIntervals) {
            while (offset + 1 < data.size) {
                val rr = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                val rrMs = (rr * 1000) / 1024
                if (rrMs in 200..2000) {
                    rrIntervals.add(rrMs)
                }
                offset += 2
            }
        }

        return HrMeasurement(heartRate, rrIntervals)
    }

    // --- HRV Calculation ---

    private fun calculateHrv(): HrvResult? {
        val intervals: List<Int>
        val heartRates: List<Int>
        synchronized(rrBuffer) {
            if (rrBuffer.size < 30) return null
            intervals = rrBuffer.toList()
            heartRates = hrBuffer.toList()
        }

        val successiveDiffs = (1 until intervals.size).map { i ->
            val diff = (intervals[i] - intervals[i - 1]).toDouble()
            diff * diff
        }

        if (successiveDiffs.isEmpty()) return null

        val rmssd = sqrt(successiveDiffs.average()).toFloat()
        val avgHr = if (heartRates.isNotEmpty()) heartRates.average().toFloat() else 0f

        return HrvResult(hrv = rmssd, avgHr = avgHr)
    }
}
