package com.example.speach_recognotion_llm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.example.speach_recognotion_llm.service.WakeWordService
import com.example.speach_recognotion_llm.ui.screen.AnalyticsScreen
import com.example.speach_recognotion_llm.ui.screen.CalendarScreen
import com.example.speach_recognotion_llm.ui.screen.HrvScreen
import com.example.speach_recognotion_llm.ui.screen.MoodJournalScreen
import com.example.speach_recognotion_llm.ui.screen.SettingsScreen
import com.example.speach_recognotion_llm.ui.theme.Speach_recognotion_llmTheme
import com.example.speach_recognotion_llm.ui.viewmodel.CalendarViewModel
import com.example.speach_recognotion_llm.ui.viewmodel.HrvViewModel
import com.example.speach_recognotion_llm.ui.viewmodel.MoodViewModel
import com.example.speach_recognotion_llm.util.LocaleHelper

private data class NavItem(val labelRes: Int, val icon: ImageVector)

private val navItems = listOf(
    NavItem(R.string.nav_mood, Icons.Default.SelfImprovement),
    NavItem(R.string.nav_hrv, Icons.Default.MonitorHeart),
    NavItem(R.string.nav_calendar, Icons.Default.CalendarMonth),
    NavItem(R.string.nav_analytics, Icons.Default.BarChart),
    NavItem(R.string.nav_settings, Icons.Default.Settings)
)

class MainActivity : ComponentActivity() {

    private val moodViewModel: MoodViewModel by viewModels()
    private val hrvViewModel: HrvViewModel by viewModels()
    private val calendarViewModel: CalendarViewModel by viewModels()

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startWakeWordServiceIfReady()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startWakeWordServiceIfReady()
    }

    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        calendarViewModel.setPermissionGranted(allGranted)
    }

    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            hrvViewModel.startScan()
        } else {
            val permanentlyDenied = permissions.any { (perm, granted) ->
                !granted && !shouldShowRequestPermissionRationale(perm)
            }
            if (permanentlyDenied) {
                hrvViewModel.setError(
                    "Bluetooth permissions required.\n" +
                    "Go to Settings → Apps → ${getString(R.string.app_name)} → Permissions"
                )
            } else {
                hrvViewModel.setError("Bluetooth permissions are required to scan for devices")
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        checkCalendarPermissions()

        setContent {
            Speach_recognotion_llmTheme {
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }

                // Feed mood tone stress into HRV composite stress
                val moodState by moodViewModel.moodState.collectAsState()
                LaunchedEffect(moodState.toneResult) {
                    val tone = moodState.toneResult ?: return@LaunchedEffect
                    val voiceStress = tone.vocalStressScore
                    val textStress = tone.fusionStressIndex ?: voiceStress
                    hrvViewModel.updateStressFromMood(voiceStress, textStress)
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            navItems.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                                    label = { Text(stringResource(item.labelRes)) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> MoodJournalScreen(
                            viewModel = moodViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        1 -> HrvScreen(
                            viewModel = hrvViewModel,
                            onRequestBlePermissions = { requestBlePermissionsAndScan() },
                            modifier = Modifier.padding(innerPadding)
                        )
                        2 -> CalendarScreen(
                            calendarViewModel = calendarViewModel,
                            hrvViewModel = hrvViewModel,
                            onRequestPermissions = { requestCalendarPermissions() },
                            modifier = Modifier.padding(innerPadding)
                        )
                        3 -> AnalyticsScreen(
                            viewModel = moodViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        4 -> SettingsScreen(
                            onLanguageChanged = { language ->
                                changeLanguage(language)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    private fun changeLanguage(language: String) {
        val current = LocaleHelper.getLanguage(this)
        if (current == language) return

        LocaleHelper.setLanguage(this, language)
        recreate()
    }

    private fun requestMicPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

    private fun checkCalendarPermissions() {
        val readGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (readGranted && writeGranted) {
            calendarViewModel.setPermissionGranted(true)
        }
    }

    private fun requestBlePermissionsAndScan() {
        // Check location services (required for BLE scanning on most Android versions)
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!locationEnabled) {
            hrvViewModel.setError(
                "Location services are required for Bluetooth scanning.\n" +
                "Please enable Location in your phone settings."
            )
            return
        }

        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isEmpty()) {
            // All permissions already granted — start scan immediately
            hrvViewModel.startScan()
        } else {
            // Request permissions; scan will start in the callback
            blePermissionLauncher.launch(needed.toTypedArray())
        }
    }

    fun requestCalendarPermissions() {
        calendarPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        )
    }

    private fun startWakeWordServiceIfReady() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            WakeWordService.start(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            WakeWordService.stop(this)
        }
    }
}
