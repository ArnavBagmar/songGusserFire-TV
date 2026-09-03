# Minification is disabled for release builds (see app/build.gradle.kts), so
# these rules only matter if you turn R8 back on. They keep kotlinx-serialization
# generated serializers reachable.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class dev.snippet.tv.** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class dev.snippet.tv.**
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
