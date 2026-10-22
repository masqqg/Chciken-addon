# Only rename OUR code. Do not shrink or optimize (keeps the addon safe to load).
-dontshrink
-dontoptimize
-dontwarn **
-ignorewarnings

# Needed so Meteor, Orbit and Mixin still work
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Record,Exceptions,SourceFile,LineNumberTable

# Entry point named in fabric.mod.json
-keep class com.example.addon.AddonTemplate { *; }

# Mixin class names are written in the mixins json
-keep class com.example.addon.mixin.** { *; }

# Orbit event handlers
-keepclassmembers class * {
    @meteordevelopment.orbit.EventHandler *;
}

# Modules are created with new X()
-keepclassmembers class * extends meteordevelopment.meteorclient.systems.modules.Module {
    <init>();
}

# Move all renamed classes into one package and rename them to a, b, c...
-repackageclasses 'x'
-allowaccessmodification
