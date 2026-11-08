# NoFence ProGuard / R8 Rules
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.geckoview.**
-keep class org.torproject.jni.** { *; }
-keep class info.guardianproject.** { *; }

# Keep Room entities & DAOs
-keep class com.nofence.browser.storage.** { *; }
