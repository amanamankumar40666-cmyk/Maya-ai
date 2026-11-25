# MAYA AI — Android Studio & APK Build Guide

## Prerequisites
- **Android Studio** Koala (2024.1+) or newer with **JDK 17**
- **Android SDK Platform 35** (Android 15) and Build-Tools 35.0.0
- Target Phone running **Android 13 (API 33) or newer**
- A **Gemini API Key** from Google AI Studio

## Step 1: Open Project in Android Studio
1. Extract the downloaded `MayaAI-AndroidStudio.zip` archive.
2. Open **Android Studio** -> **Open** -> select the extracted `MayaAI` folder.
3. Wait for Gradle Sync to finish.

## Step 2: Configure API Key
1. Copy `local.properties.example` to `local.properties` (Android Studio auto-creates `local.properties` with `sdk.dir`).
2. Add your Gemini API key on a new line in `local.properties`:
   ```properties
   GEMINI_API_KEY=your_actual_gemini_api_key
   ```
3. Click **Sync Project with Gradle Files**.

## Step 3: Build & Install the APK
### Option A — Direct USB Install (Fastest)
1. On your Android phone: **Settings -> About Phone -> tap Build Number 7 times** to enable Developer Options.
2. Enable **USB Debugging** in Developer Options and connect via USB.
3. Click the green **Run ▶** button in Android Studio.

### Option B — Generate Shareable APK File
1. In Android Studio menu: **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
2. Or via terminal inside the project root:
   ```bash
   ./gradlew assembleDebug
   ```
3. Locate the generated APK at:
   `app/build/outputs/apk/debug/app-debug.apk`
4. Transfer `app-debug.apk` to your Android phone, tap it, and allow **Install unknown apps** if prompted.

## Step 4: Grant Permissions on First Launch
- Approve **Microphone**, **Notifications**, **Camera** (for Flashlight), **Phone**, **Contacts**, and **SMS** when prompted.
- Open **MAYA Settings** inside the app to optionally enable:
  - **Notification Access** (lets Maya read incoming WhatsApp/system notifications aloud)
  - **Accessibility Smart Actions** (lets Maya navigate or confirm UI actions when requested)
