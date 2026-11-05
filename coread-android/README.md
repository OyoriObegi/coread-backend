# CoRead Android
Kotlin/Compose app, package com.coread.app. Open this folder in Android Studio with SDK 35 and JDK 17/21. The sample runs without Firebase. See root SETUP.md.

Build: gradlew.bat testDebugUnitTest assembleDebug assembleRelease
Size gate: gradlew.bat checkReleaseSize (5,000,000 bytes)
R8 full mode and resource shrinking are enabled. No signing key is included.
Add your google-services.json to app/ for cloud features; set the actual Render URL in app Settings.
