# RideSync ProGuard / R8 rules.

# kotlinx.serialization — keep serializers for the wire protocol.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ridesync.app.**$$serializer { *; }
-keepclassmembers class com.ridesync.app.** { *** Companion; }
-keepclasseswithmembers class com.ridesync.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Concentus (pure-Java Opus) uses no reflection, but keep it intact to be safe.
-keep class io.github.jaredmdobson.concentus.** { *; }
-keep class org.concentus.** { *; }

# zxing
-keep class com.google.zxing.** { *; }
