# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-dontpreverify
-repackageclasses ''
-keepparameternames
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, Exceptions, InnerClasses, SourceFile, LineNumberTable,
                Signature, Deprecated, EnclosingMethod

# Remove log statements
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Keep public classes/interfaces
-keep public class com.google.android.things.iotcore.* {
    public protected *;
}
