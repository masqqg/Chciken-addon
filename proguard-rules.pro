# Step 1 of 2: rename our classes/methods/fields to meaningless names.
# Do NOT shrink or optimize (that can break event handlers found by reflection).
-dontshrink
-dontoptimize
-dontwarn **
-ignorewarnings

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Record,Exceptions

# Remove the real source file name (e.g. "ClusterBypass.java") and line numbers;
# they leak what a class actually is even after the class name is scrambled.
-renamesourcefileattribute SourceFile

# Entry point named in fabric.mod.json - keep its name
-keep class com.example.addon.AddonTemplate { *; }

# Mixin class names are written in the mixins json - keep their names
-keep class com.example.addon.mixin.** { *; }

# NOTE: @EventHandler methods are NOT kept here on purpose. Orbit finds them by
# the annotation (which we keep via *Annotation*), not by method name, so their
# names (onRender3D, onTick, ...) get scrambled too. Modules are built with plain
# "new X()" calls in our own code, not reflection, so the constructor needs no
# keep rule either - ProGuard tracks that link automatically.
#
# What CANNOT be renamed, and is not covered by any rule below: methods that
# override a method declared in a Meteor/Minecraft library class (render(),
# build(), onActivate(), onDeactivate(), onInitialize(), ...). Java requires an
# override to keep the exact same name, or the engine can no longer find and
# call it. ProGuard enforces this automatically; no rule can change it.

# Rename and move everything else
-repackageclasses 'x'
-allowaccessmodification
-overloadaggressively
