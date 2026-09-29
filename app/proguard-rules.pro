# Add project specific ProGuard rules here.
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.myenvironment.launcher.core.model.**$$serializer { *; }
-keepclassmembers class com.myenvironment.launcher.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.myenvironment.launcher.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
