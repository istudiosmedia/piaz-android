# The YouTube player calls back into the app through this bridge.
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class news.piaz.**$$serializer { *; }
-keepclassmembers class news.piaz.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
