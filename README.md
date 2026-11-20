# FocusGuard (working title) — Android MVP

A student focus app inspired by the Regain category: focus timer (Timer / Pomodoro / Stopwatch),
app blocking during sessions, screen-time stats and streaks. Original name, code and design.

**Package:** `com.focusguard.app` (provisional — rename before first Play Store upload if the final brand differs)

## Build
- Local: Android Studio, JDK 17, SDK 35 — or push to GitHub and the Actions workflow builds `app-debug.apk`.
- The debug APK is sideloadable for the real-device blocking test.

## Permissions (each asked with a reason screen first)
Usage Access (foreground detection + stats) · Display over other apps (block screen) ·
Accessibility (instant detection of blocked apps) · Notifications · Battery optimisation exemption.

Blocking data never leaves the device. No account, no server in the MVP.
