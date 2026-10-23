# Step 1 of 2: rename our classes/methods/fields to meaningless names.
# Do NOT shrink or optimize (that can break event handlers found by reflection).
-dontshrink
-dontoptimize
-dontwarn **
-ignorewarnings

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Record,Exceptions,SourceFile,LineNumberTable

# Entry point named in fabric.mod.json - keep its name
-keep class com.example.addon.AddonTemplate { *; }

# Mixin class names are written in the mixins json - keep their names
-keep class com.example.addon.mixin.** { *; }

# Orbit event handler methods are found by name/annotation at runtime - keep them
-keepclassmembers class * {
    @meteordevelopment.orbit.EventHandler *;
}

# Modules are created with "new X()" by reflection - keep the constructor
-keepclassmembers class * extends meteordevelopment.meteorclient.systems.modules.Module {
    <init>();
}

# Rename and move everything else
-repackageclasses 'x'
-allowaccessmodification
-obfuscationdictionary proguard-rules.pro
-overloadaggressively
