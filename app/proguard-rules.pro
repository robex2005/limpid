# Shizuku.newProcess is called by reflection
-keepclassmembers class rikka.shizuku.Shizuku { private static *** newProcess(...); }
-keep class rikka.shizuku.** { *; }
