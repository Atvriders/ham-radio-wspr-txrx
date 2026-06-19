# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.atvriders.wsprtxrx.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
