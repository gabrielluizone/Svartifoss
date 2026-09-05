# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\android-sdk-windows/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Do not obfuscate the code
-dontobfuscate
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable

# Keep exceptions, line numbers and annotations for better crash reporting
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
# com.google.android.wearable.* is a system-provided library on watches (declared via
# <uses-library> in the manifest), so it's absent from the compile classpath - suppress the
# R8 missing-class error for it.
-dontwarn com.google.android.wearable.intent.RemoteIntent

# protobuf-javalite ships no consumer rule of its own, and the `!field/*` term above is a
# no-op under R8 (it only recognizes the coarse -dontoptimize/-optimizations toggle, not
# ProGuard's fine-grained categories), so nothing was stopping R8 from removing a message
# field it saw no live getter/setter for - e.g. MusicState.time, written by the phone but never
# read on the watch. That field's Java declaration is gone from the release build, but the
# schema table protoc baked into the class's static initializer still names it for every
# field, so the *first* MusicState.parseFrom() call resolves that table via reflection, fails
# to find the field, and throws for the whole message - not just that one field. Caught by
# launchWithErrorHandling, this is exactly the release-only "always shows ERROR, taps still
# control playback" bug: outbound commands are raw byte payloads that never touch protobuf,
# only the DataItem-carried MusicState does. Keeping every declared field on every generated
# message class is the standard fix Google's own protobuf-lite consumer rule would supply.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
