# Calendar Day Planner

An Android app that automates calendar checking and provides AI-powered daily summaries using Gemini.

## Features
- Reads today's calendar events
- Generates AI summaries with preparation tips
- Accessibility service for automation

## Setup

1. Clone the repo
2. Create `app/secrets.properties` with:
```
   GEMINI_API_KEY=your_gemini_api_key_here
```
3. Sync Gradle
4. Enable accessibility service on device
5. Grant calendar permission

## Requirements
- Android Studio
- Android device with API 24+
- Google Gemini API key