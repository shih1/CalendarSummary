# README_AI.md - AI Assistant Context

This document provides context for AI assistants (like Claude) working on this Android project.

## Project Overview

**Calendar Day Planner** - An Android accessibility service that automates calendar checking and provides AI-powered daily summaries using Google's Gemini API.

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 14+)
- **Build System**: Gradle (Kotlin DSL)
- **AI Integration**: Google Gemini 2.5 Flash API

## Architecture

### Core Components

1. **MainActivity.kt** - Jetpack Compose UI
    - Manages permissions (Calendar, Accessibility)
    - Displays automation controls and AI summary
    - Uses `rememberScrollState()` for scrollable summary display

2. **CalendarAutomationService.kt** - AccessibilityService
    - Extends `AccessibilityService` for system-level automation
    - Reads calendar events via ContentResolver
    - Calls Gemini API for AI summaries
    - Performs global actions (home button, app launching)

3. **AndroidManifest.xml**
    - Declares accessibility service with metadata
    - Requests READ_CALENDAR and INTERNET permissions
    - Links to accessibility_service_config.xml

4. **accessibility_service_config.xml**
    - Configures service capabilities (gestures, window content)
    - Defines event types the service monitors

## Key Design Patterns

### 1. Accessibility Service Pattern
```kotlin
class CalendarAutomationService : AccessibilityService() {
    companion object {
        var instance: CalendarAutomationService? = null  // Singleton access
    }
    
    override fun onServiceConnected() {
        instance = this  // Register instance for MainActivity access
    }
}
```

**Why**: Accessibility services run as system-level services. The singleton pattern allows MainActivity to trigger automation methods.

### 2. Background Threading for Network Calls
```kotlin
fun runDailyAutomationWithCallback(onSummaryReady: (String) -> Unit) {
    Thread {
        // Network calls and automation logic
        val summary = generateAISummary(events)
        onSummaryReady(summary)  // Callback to UI thread
    }.start()
}
```

**Why**: Android blocks network calls on main thread. Background threads + callbacks enable async operations without freezing UI.

### 3. Calendar Instances Query (Not Events)
```kotlin
val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
    appendPath(startOfDay.toString())
    appendPath(endOfDay.toString())
}.build()
```

**Why**: `Instances` API automatically expands recurring events into individual occurrences. `Events` API only returns master events, missing recurring instances.

### 4. BuildConfig for Secrets
```kotlin
// build.gradle.kts loads from secrets.properties
buildConfigField("String", "GEMINI_API_KEY", "\"$apiKey\"")

// CalendarAutomationService.kt accesses it
val apiKey = BuildConfig.GEMINI_API_KEY
```

**Why**: Keeps API keys out of version control while making them accessible at runtime.

## API Integration Details

### Gemini API Call Structure
```kotlin
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=API_KEY

Body: {
  "contents": [{
    "parts": [{
      "text": "prompt here"
    }]
  }]
}

Response: {
  "candidates": [{
    "content": {
      "parts": [{
        "text": "AI response here"
      }]
    }
  }]
}
```

**Model**: `gemini-2.5-flash` (free tier, fast responses)
**Endpoint**: v1beta (stable as of Dec 2024)

## Common Modification Scenarios

### Adding New Automation Actions

1. Add method to `CalendarAutomationService.kt`:
```kotlin
fun performAction() {
    Thread {
        performGlobalAction(GLOBAL_ACTION_BACK)  // Example: back button
        // or startActivity(intent) for launching apps
    }.start()
}
```

2. Call from MainActivity via service instance:
```kotlin
getAccessibilityService()?.performAction()
```

### Changing AI Prompt

Edit `generateAISummary()` in CalendarAutomationService.kt:
```kotlin
val prompt = """
    Your new prompt here
    Event data: $eventList
""".trimIndent()
```

### Adding New Permissions

1. Add to AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.PERMISSION_NAME" />
```

2. Request at runtime in MainActivity:
```kotlin
requestPermissionLauncher.launch(Manifest.permission.PERMISSION_NAME)
```

### Reading Different Calendar Data

Modify `readTodaysEvents()` projection array:
```kotlin
val projection = arrayOf(
    CalendarContract.Events.TITLE,
    CalendarContract.Events.EVENT_LOCATION,  // Add location
    CalendarContract.Events.ALL_DAY,         // Add all-day flag
    // etc.
)
```

## Important Constraints

### 1. Accessibility Service Limitations
- Cannot be enabled programmatically (user must enable in Settings)
- Requires user trust warning acceptance
- Can be disabled by system under memory pressure
- Some manufacturers restrict accessibility services

### 2. Calendar Access
- Requires explicit READ_CALENDAR permission
- Query only returns events from calendars user has added
- Recurring events MUST use Instances API, not Events API
- Time zones matter - always use Calendar.getInstance() for "today"

### 3. Network Calls
- Must run on background thread (not main/UI thread)
- Handle failures gracefully (network errors, API rate limits)
- Timeouts needed (15s recommended for Gemini)
- Parse JSON manually (no external JSON libraries in this project)

### 4. Jetpack Compose State
- Use `remember { mutableStateOf() }` for UI state
- State changes trigger recomposition automatically
- Background threads must post to main thread for UI updates:
```kotlin
android.os.Handler(android.os.Looper.getMainLooper()).post {
    // Update UI state here
}
```

## File Structure
```
app/
├── src/main/
│   ├── java/com/example/helloworld/
│   │   ├── MainActivity.kt                    # Compose UI
│   │   ├── CalendarAutomationService.kt       # Core automation logic
│   │   └── ui/theme/                          # Compose theme files
│   ├── res/
│   │   ├── values/
│   │   │   └── strings.xml                    # String resources
│   │   └── xml/
│   │       └── accessibility_service_config.xml  # Service config
│   └── AndroidManifest.xml                    # App manifest
├── secrets.properties                         # API key (NOT in git)
└── build.gradle.kts                          # Build configuration
```

## Testing Checklist

When modifying this project, test:

1. ✅ Accessibility service enables successfully in Settings
2. ✅ Calendar permission granted
3. ✅ Calendar app opens via automation
4. ✅ Events read correctly (including recurring events)
5. ✅ Gemini API returns 200 response code
6. ✅ AI summary displays in scrollable UI
7. ✅ App works after killing and reopening
8. ✅ Permissions persist across app updates (not reinstalls)

## Debug Tips

### Check Logcat Filters
- `CalendarService` - All automation and API logs
- `AccessibilityCheck` - Service detection issues
- `System.err` - Crash stack traces

### Common Issues

**"Service not ready"**: Accessibility service not enabled or crashed
- Solution: Re-enable in Settings, check `onServiceConnected()` logs

**"Cursor count: 0"**: No calendar events found
- Check permission granted
- Verify events exist for today's date
- Ensure using Instances API, not Events API

**"API error 404"**: Wrong model name
- Current model: `gemini-2.5-flash`
- Endpoint: `v1beta`

**"Network on main thread" crash**: Network call not in Thread { }
- Wrap all API calls in `Thread { }.start()`

## Dependencies

Minimal external dependencies - mostly Android SDK:
```kotlin
// Compose UI
implementation(libs.androidx.compose.ui)
implementation(libs.androidx.material3)

// Core Android
implementation(libs.androidx.core.ktx)
implementation(libs.androidx.lifecycle.runtime.ktx)

// No external HTTP libraries (uses HttpURLConnection)
// No external JSON libraries (uses org.json.JSONObject)
```

## Future Enhancement Ideas

- [ ] Add settings screen for user to input own API key
- [ ] Support multiple AI providers (OpenAI, Anthropic)
- [ ] Add voice readout of summary (TTS)
- [ ] Schedule automatic daily summaries at configurable time
- [ ] Add widgets for quick summary view
- [ ] Export summaries to email/notes
- [ ] Analyze calendar patterns over time
- [ ] Suggest optimal meeting times
- [ ] Integration with task management apps

## Security Notes

- API key stored in `secrets.properties` (gitignored)
- API key compiled into APK via BuildConfig
- For production: Use Android Keystore or backend proxy
- Current approach: Fine for personal use, NOT for Play Store distribution

## References

- [Android Accessibility Services](https://developer.android.com/guide/topics/ui/accessibility/service)
- [CalendarContract API](https://developer.android.com/reference/android/provider/CalendarContract)
- [Gemini API Docs](https://ai.google.dev/docs)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)

---

**Last Updated**: December 2024  
**Gemini Model**: gemini-2.5-flash  
**Android Version**: 14+ (API 35)