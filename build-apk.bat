@echo off
REM One-command debug APK build for Windows.
REM Prereqs: JDK 17 and the Android SDK installed, with ANDROID_HOME set
REM (or a local.properties with sdk.dir=...). See README "Getting the APK".
cd /d "%~dp0"

call gradlew.bat :app:assembleDebug --no-daemon
if errorlevel 1 exit /b 1

set "APK=app\build\outputs\apk\debug\app-debug.apk"
if exist "%APK%" (
  echo.
  echo APK built: %CD%\%APK%
  echo Install on a connected phone with:  adb install -r "%APK%"
) else (
  echo Build finished but APK not found at %APK%
  exit /b 1
)
