package com.example.helloworld

import android.accessibilityservice.AccessibilityService
import android.content.ContentResolver
import android.content.Intent
import android.provider.CalendarContract
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class CalendarAutomationService : AccessibilityService() {

    companion object {
        var instance: CalendarAutomationService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("CalendarService", "Service connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This gets called when something happens on screen
    }

    override fun onInterrupt() {
        Log.d("CalendarService", "Service interrupted")
    }

    // Function to go home
    fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        Log.d("CalendarService", "Going home")
    }

    // Function to open Calendar app
    fun openCalendar() {
        // Try multiple calendar package names
        val calendarPackages = listOf(
            "com.google.android.calendar",
            "com.android.calendar",
            "com.samsung.android.calendar"
        )

        var launched = false
        for (packageName in calendarPackages) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d("CalendarService", "Opening calendar: $packageName")
                launched = true
                break
            }
        }

        if (!launched) {
            Log.e("CalendarService", "No calendar app found, trying generic intent")
            // Fallback: open any calendar app
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = android.provider.CalendarContract.CONTENT_URI
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d("CalendarService", "Opened calendar via generic intent")
            } catch (e: Exception) {
                Log.e("CalendarService", "Failed to open calendar: ${e.message}")
            }
        }
    }

    // Function to read today's calendar events
    fun readTodaysEvents(): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()

        Log.d("CalendarService", "=== Starting Calendar Read ===")

        val contentResolver: ContentResolver = contentResolver

        // Get start and end of today
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfDay = calendar.timeInMillis

        Log.d("CalendarService", "Today's date: ${Date()}")
        Log.d("CalendarService", "Start of day: ${Date(startOfDay)}")
        Log.d("CalendarService", "End of day: ${Date(endOfDay)}")

        // Use Instances URI instead of Events - this handles recurring events
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            appendPath(startOfDay.toString())
            appendPath(endOfDay.toString())
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME
        )

        try {
            Log.d("CalendarService", "Querying calendar instances...")

            val cursor = contentResolver.query(
                instancesUri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            if (cursor == null) {
                Log.e("CalendarService", "Cursor is null - permission might be denied")
                return events
            }

            Log.d("CalendarService", "Cursor count: ${cursor.count}")

            cursor.use {
                val titleIndex = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val startIndex = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = it.getColumnIndex(CalendarContract.Instances.END)
                val descIndex = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val calNameIndex = it.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

                while (it.moveToNext()) {
                    val title = if (titleIndex >= 0) it.getString(titleIndex) else "No Title"
                    val startTime = if (startIndex >= 0) it.getLong(startIndex) else 0L
                    val endTime = if (endIndex >= 0) it.getLong(endIndex) else 0L
                    val description = if (descIndex >= 0) it.getString(descIndex) ?: "" else ""
                    val calName = if (calNameIndex >= 0) it.getString(calNameIndex) else "Unknown"

                    events.add(CalendarEvent(title, startTime, endTime, description))
                    Log.d("CalendarService", "Found event: $title")
                    Log.d("CalendarService", "  Calendar: $calName")
                    Log.d("CalendarService", "  Start: ${Date(startTime)}")
                    Log.d("CalendarService", "  End: ${Date(endTime)}")
                }
            }

            Log.d("CalendarService", "Total events found: ${events.size}")

        } catch (e: SecurityException) {
            Log.e("CalendarService", "Security exception - permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e("CalendarService", "Error reading calendar: ${e.message}")
            e.printStackTrace()
        }

        return events
    }

    // Generate summary
    fun generateDaySummary(): String {
        val events = readTodaysEvents()

        if (events.isEmpty()) {
            return "No events scheduled for today. Enjoy your free day!"
        }

        // For the UI display, we need to run in background
        var summary = "Generating AI summary..."

        Thread {
            try {
                summary = generateAISummary(events)
                Log.d("CalendarService", "UI Summary generated: $summary")
            } catch (e: Exception) {
                Log.e("CalendarService", "AI summary failed: ${e.message}")
                summary = generateBasicSummary(events)
            }
        }.start()

        // Return basic summary immediately, AI summary will be logged
        return generateBasicSummary(events)
    }

    fun generateBasicSummary(events: List<CalendarEvent>): String {
        val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val summary = StringBuilder("Your Day Ahead:\n\n")

        summary.append("You have ${events.size} event(s) today:\n\n")

        events.forEachIndexed { index, event ->
            summary.append("${index + 1}. ${event.title}\n")
            summary.append("   Time: ${dateFormat.format(Date(event.startTime))}")
            if (event.endTime > 0) {
                summary.append(" - ${dateFormat.format(Date(event.endTime))}")
            }
            summary.append("\n")
            if (event.description.isNotEmpty()) {
                summary.append("   Details: ${event.description}\n")
            }
            summary.append("\n")
        }

        return summary.toString()
    }

    fun generateAISummary(events: List<CalendarEvent>): String {
        val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        // Build event list for prompt
        val eventList = events.mapIndexed { index, event ->
            val startTime = dateFormat.format(Date(event.startTime))
            val endTime = if (event.endTime > 0) dateFormat.format(Date(event.endTime)) else ""
            val timeRange = if (endTime.isNotEmpty()) "$startTime - $endTime" else startTime

            "${index + 1}. ${event.title} ($timeRange)" +
                    if (event.description.isNotEmpty()) "\n   Details: ${event.description}" else ""
        }.joinToString("\n")

        val prompt = """
            I have these events scheduled for today:
            
            $eventList
            
            Please provide a brief preparatory summary of my day with:
            1. Overview of what's ahead
            2. Key things to prepare for
            3. Any time management tips
            
            Keep it concise, friendly, and actionable (under 200 words).
        """.trimIndent()

        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        Log.d("CalendarService", "Sending request to: $url")

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val responseCode = connection.responseCode
        Log.d("CalendarService", "Gemini API response code: $responseCode")

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().readText()
            Log.d("CalendarService", "Full Gemini response received")

            val jsonResponse = JSONObject(response)
            val candidates = jsonResponse.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text")
                }
            }
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.readText()
            Log.e("CalendarService", "Gemini API error response code: $responseCode")
            Log.e("CalendarService", "Gemini API error body: $errorStream")
        }

        throw Exception("Failed to get AI response")
    }

    // Main automation function
    fun runDailyAutomation() {
        Thread {
            try {
                // Step 1: Go home
                goHome()
                Thread.sleep(1000)

                // Step 2: Open calendar
                openCalendar()
                Thread.sleep(2000)

                // Step 3: Read events and generate AI summary
                val events = readTodaysEvents()

                if (events.isEmpty()) {
                    val summary = "No events scheduled for today. Enjoy your free day!"
                    Log.d("CalendarService", "Daily Summary:\n$summary")
                } else {
                    // Try AI summary in background
                    try {
                        Log.d("CalendarService", "Generating AI summary...")
                        val aiSummary = generateAISummary(events)
                        Log.d("CalendarService", "AI Daily Summary:\n$aiSummary")
                    } catch (e: Exception) {
                        Log.e("CalendarService", "AI failed, using basic summary: ${e.message}")
                        val basicSummary = generateBasicSummary(events)
                        Log.d("CalendarService", "Basic Daily Summary:\n$basicSummary")
                    }
                }

                // Step 4: Go back home
                Thread.sleep(2000)
                goHome()

            } catch (e: Exception) {
                Log.e("CalendarService", "Automation error: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }


    fun runDailyAutomationWithCallback(onSummaryReady: (String) -> Unit) {
        Thread {
            try {
                // Step 1: Go home
                goHome()
                Thread.sleep(1000)

                // Step 2: Open calendar
                openCalendar()
                Thread.sleep(2000)

                // Step 3: Read events and generate AI summary
                val events = readTodaysEvents()

                val summary = if (events.isEmpty()) {
                    "No events scheduled for today. Enjoy your free day!"
                } else {
                    try {
                        Log.d("CalendarService", "Generating AI summary...")
                        val aiSummary = generateAISummary(events)
                        Log.d("CalendarService", "AI Daily Summary:\n$aiSummary")
                        aiSummary
                    } catch (e: Exception) {
                        Log.e("CalendarService", "AI failed, using basic summary: ${e.message}")
                        generateBasicSummary(events)
                    }
                }

                // Send summary back to UI
                onSummaryReady(summary)

                // Step 4: Go back home
                Thread.sleep(2000)
                goHome()

            } catch (e: Exception) {
                Log.e("CalendarService", "Automation error: ${e.message}")
                e.printStackTrace()
                onSummaryReady("Error generating summary: ${e.message}")
            }
        }.start()
    }
}


// Data class for calendar events
data class CalendarEvent(
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val description: String
)