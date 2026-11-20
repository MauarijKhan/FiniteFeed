# Finite Feed release rules. Android's defaults (proguard-android-optimize.txt) cover the rest:
# the accessibility service and activity are kept because the manifest names them.

# Readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
