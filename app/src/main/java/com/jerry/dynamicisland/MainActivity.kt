package com.jerry.dynamicisland

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var overlayGranted by mutableStateOf(false)
    private var notificationAccessGranted by mutableStateOf(false)
    private var islandWidth by mutableStateOf(280f)
    private var collapsedIslandWidth by mutableStateOf(180f)
    private var glassOpacity by mutableStateOf(82f)
    private var blurStrength by mutableStateOf(28f)
    private var topOffset by mutableStateOf(8f)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermission()
        loadSettings()

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Dynamic Island", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Customize your floating island")
                        Spacer(Modifier.height(22.dp))
                        Text(
                            if (overlayGranted) "Overlay permission: Granted"
                            else "Overlay permission: Not granted"
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (!Settings.canDrawOverlays(this@MainActivity)) {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Grant overlay permission") }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (notificationAccessGranted) "Notification access: Granted"
                            else "Notification access: Not granted"
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Grant notification access") }

                        Spacer(Modifier.height(24.dp))
                        Text("Island width: ${islandWidth.toInt()} dp")
                        Slider(
                            value = islandWidth,
                            onValueChange = { islandWidth = it },
                            valueRange = 160f..340f
                        )

                        Text("Collapsed width: ${collapsedIslandWidth.toInt()} dp")
                        Slider(
                            value = collapsedIslandWidth,
                            onValueChange = { collapsedIslandWidth = it },
                            valueRange = 140f..280f
                        )

                        Text("Glass opacity: ${glassOpacity.toInt()}%")
                        Slider(
                            value = glassOpacity,
                            onValueChange = { glassOpacity = it },
                            valueRange = 35f..100f
                        )

                        Text("Background blur: ${blurStrength.toInt()}")
                        Slider(
                            value = blurStrength,
                            onValueChange = { blurStrength = it },
                            valueRange = 0f..50f
                        )

                        Text("Top position: ${topOffset.toInt()} dp")
                        Slider(
                            value = topOffset,
                            onValueChange = { topOffset = it },
                            valueRange = 0f..120f
                        )

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { saveSettingsAndRefresh() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Apply appearance") }

                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { startIsland() },
                                enabled = overlayGranted,
                                modifier = Modifier.weight(1f)
                            ) { Text("Start Island") }
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Button(
                                onClick = { stopIsland() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Stop") }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Tip: drag the island left or right to reposition it. Tap to expand or collapse.")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermission()
    }

    private fun refreshPermission() {
        overlayGranted = Settings.canDrawOverlays(this)
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ).orEmpty()
        notificationAccessGranted = enabledListeners.contains(packageName)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        islandWidth = prefs.getFloat(KEY_WIDTH, 280f)
        collapsedIslandWidth = prefs.getFloat(KEY_COLLAPSED_WIDTH, 180f)
        glassOpacity = prefs.getFloat(KEY_OPACITY, 82f)
        blurStrength = prefs.getFloat(KEY_BLUR, 28f)
        topOffset = prefs.getFloat(KEY_TOP, 8f)
    }

    private fun saveSettingsAndRefresh() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putFloat(KEY_WIDTH, islandWidth)
            .putFloat(KEY_COLLAPSED_WIDTH, collapsedIslandWidth)
            .putFloat(KEY_OPACITY, glassOpacity)
            .putFloat(KEY_BLUR, blurStrength)
            .putFloat(KEY_TOP, topOffset)
            .apply()

        startService(
            Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_REFRESH)
        )
    }

    private fun startIsland() {
        val intent = Intent(this, OverlayService::class.java)
            .setAction(OverlayService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopIsland() {
        startService(
            Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP)
        )
    }

    companion object {
        const val PREFS = "dynamic_island_settings"
        const val KEY_WIDTH = "island_width"
        const val KEY_COLLAPSED_WIDTH = "collapsed_island_width"
        const val KEY_OPACITY = "glass_opacity"
        const val KEY_BLUR = "blur_strength"
        const val KEY_TOP = "top_offset"
    }
}
