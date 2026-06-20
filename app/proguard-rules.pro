# kage parses age stanzas and looks up types by name; keep it intact to be safe. It is small.
-keep class kage.** { *; }
-keepclassmembers class kage.** { *; }

# Keep attributes R8 might otherwise drop (used by reflection-y lookups and stack traces).
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ZXing reflectively touches some reader/writer classes.
-keep class com.google.zxing.** { *; }

# Compose and AndroidX ship their own consumer rules; nothing extra needed here.
