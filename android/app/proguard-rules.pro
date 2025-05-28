# Keep all FaceTec SDK classes
-keep class com.facetec.sdk.** { *; }
-keepclassmembers class com.facetec.sdk.** { *; }

# Specific rules for the problematic class
-keep class com.facetec.sdk.ds.** { *; }
-keepclassmembers class com.facetec.sdk.ds.** { *; }

# Prevent method splitting
-dontobfuscate
-dontoptimize
-dontpreverify

# Increase method size limit
-optimizationpasses 5
-optimizations !method/removal/parameter,!code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Keep R8 from removing unused methods
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions 