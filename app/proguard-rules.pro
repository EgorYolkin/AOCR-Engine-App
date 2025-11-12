# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep NanoHTTPD
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# Keep WebSocket
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

