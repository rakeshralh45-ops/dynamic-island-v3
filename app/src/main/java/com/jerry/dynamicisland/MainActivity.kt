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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermission()

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Dynamic Island", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Floating island for Android")
                        Spacer(Modifier.height(24.dp))
                        Text(
                            if (overlayGranted) "Overlay permission: Granted"
                            else "Overlay permission: Not granted"
                        )
                        Spacer(Modifier.height(12.dp))
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
                        ) {
                            Text("Grant overlay permission")
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { startIsland() },
                                enabled = overlayGranted,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Start Island")
                            }
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Button(
                                onClick = { stopIsland() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Stop")
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Allow Display over other apps, return here, then tap Start Island.")
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
}
