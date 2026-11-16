# Room and Navigation Compose supply their own consumer rules.

# kotlinx.serialization: keep generated companions, serializers, and the
# synthetic constructors R8 needs for reflective lookup.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2> {
    <2>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class com.niranjan.ninety.** {
    <init>(...);
}
-keep,includedescriptorclasses class com.niranjan.ninety.**$$serializer { *; }
