# Add project specific ProGuard rules here.

# kotlinx-serialization — keep @Serializable classes and their generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.aigamerfriend.data.**$$serializer { *; }
-keepclassmembers class com.example.aigamerfriend.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.aigamerfriend.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
