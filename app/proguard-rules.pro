# Keep the BLE payload model — parsed reflectively nowhere, but kept for crash clarity.
-keep class com.silicovegas.wombcare.core.ble.** { *; }

# Room + Hilt generate their own keep rules via their consumer proguard files.
