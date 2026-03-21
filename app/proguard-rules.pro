# Room database entities
-keep class com.smokless.smokeless.data.entity.** { *; }
-keep class com.smokless.smokeless.data.dao.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Keep widget provider
-keep class com.smokless.smokeless.widget.SmokelessWidget { *; }

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
