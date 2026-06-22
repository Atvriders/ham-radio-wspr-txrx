# ---------------------------------------------------------------------------
# R8 / ProGuard keep rules for the release (minified + resource-shrunk) build.
# ---------------------------------------------------------------------------

# --- kotlinx.serialization -------------------------------------------------
# The app uses the kotlinx.serialization JSON runtime (Json.parseToJsonElement,
# JsonObject, builtin serializers for List/Map<String, Long>) rather than much
# @Serializable codegen, but keep the generated-serializer pattern + annotations
# anyway so any future @Serializable class survives shrinking.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.atvriders.wsprtxrx.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# --- App data layer (manual JSON model + parsing) --------------------------
# wspr.live / PSKReporter responses are parsed by hand into these classes; keep
# them and their members so field access can't be shrunk/renamed unexpectedly.
-keep class com.atvriders.wsprtxrx.data.** { *; }

# --- OkHttp / Okio ---------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Room ------------------------------------------------------------------
# Keep the generated DAO/database implementations and the entity.
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.atvriders.wsprtxrx.data.local.**_Impl { *; }
-dontwarn androidx.room.paging.**

# --- MapLibre --------------------------------------------------------------
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# --- Kotlin / coroutines ---------------------------------------------------
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**
