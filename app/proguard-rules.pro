# Live Weather release shrinker rules.
# Keep Retrofit service signatures and Gson DTO field shapes stable in R8 builds.

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Retrofit interfaces are created dynamically from annotations.
-keep interface com.tridev.liveweather.data.remote.api.** { *; }

# Weather/AQI/radar/geocoding responses are deserialized reflectively by Gson.
-keep class com.tridev.liveweather.data.remote.dto.** { *; }
-keepclassmembers class com.tridev.liveweather.data.remote.dto.** { <fields>; }

# Keep astronomy library public model/API names stable for release optimization.
-keep class io.github.cosinekitty.astronomy.** { *; }

# Optional annotation packages may be absent after shrinking.
-dontwarn javax.annotation.**
