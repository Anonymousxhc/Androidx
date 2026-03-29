-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

-keep class com.akatsuki.trading.data.model.** { *; }

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

-keep class org.json.** { *; }

-keepclassmembers class ** {
    @dagger.hilt.android.lifecycle.HiltViewModel *;
}
