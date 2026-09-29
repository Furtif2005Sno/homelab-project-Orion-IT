# R8 rules for release builds.
# Most libraries (Retrofit, OkHttp, kotlinx.serialization, Room, Hilt, Coil) ship their own consumer rules.

# Keep source file and line numbers so crash reports stay readable.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# kotlinx.serialization: keep generated serializers and the companion accessors.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }

# API DTOs and persisted/backup models are (de)serialized by name.
-keep class com.homelab.app.data.remote.dto.** { *; }
-keep class com.homelab.app.domain.model.** { *; }
-keep class com.homelab.app.data.model.** { *; }
-keep class com.homelab.app.data.local.entity.** { *; }
-keep enum com.homelab.app.** { *; }

# Retrofit service interfaces are created by reflection.
-keep,allowobfuscation interface com.homelab.app.data.remote.api.**

# Optional dependencies referenced by OkHttp and friends.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
