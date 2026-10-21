# Add project specific ProGuard rules here.
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep all application classes and methods intact for Room, Compose, and unit tests
-keep class com.example.** { *; }
-keep class androidx.compose.material.icons.filled.** { *; }
-keep class androidx.room.** { *; }

-dontwarn java.lang.invoke.StringConcatFactory
