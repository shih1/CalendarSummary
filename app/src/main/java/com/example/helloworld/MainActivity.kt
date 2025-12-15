package com.example.helloworld

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.helloworld.ui.theme.HelloWorldTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Calendar permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Calendar permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HelloWorldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        context = this,
                        requestPermissionLauncher = requestPermissionLauncher
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    context: Context,
    requestPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    var summary by remember { mutableStateOf("Press 'Run Automation' to check your calendar") }
    var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Calendar Day Planner",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status indicators
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAccessibilityEnabled)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isAccessibilityEnabled)
                        "✓ Accessibility Service Enabled"
                    else
                        "✗ Accessibility Service Disabled",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Enable accessibility button
        if (!isAccessibilityEnabled) {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                    Toast.makeText(
                        context,
                        "Enable 'HelloWorld' in accessibility settings",
                        Toast.LENGTH_LONG
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Accessibility Settings")
            }
        }

        // Request calendar permission button
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CALENDAR
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                } else {
                    Toast.makeText(context, "Calendar permission already granted", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Calendar Permission")
        }
// Run automation button
        Button(
            onClick = {
                if (isAccessibilityEnabled) {
                    val service = getAccessibilityService()
                    if (service != null) {
                        summary = "Running automation and generating AI summary..."
                        service.runDailyAutomationWithCallback { aiSummary ->
                            // This runs when AI summary is ready
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                summary = aiSummary
                            }
                        }
                    } else {
                        Toast.makeText(context, "Service not ready", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Please enable accessibility service first",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isAccessibilityEnabled
        ) {
            Text("Run Automation")
        }

        // Summary display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Daily Summary:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Refresh button
        Button(
            onClick = {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Status")
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedServiceName = "com.example.helloworld/com.example.helloworld.CalendarAutomationService"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )

    return enabledServices?.contains(expectedServiceName) == true ||
            enabledServices?.contains("CalendarAutomationService") == true
}

fun getAccessibilityService(): CalendarAutomationService? {
    return CalendarAutomationService.instance
}