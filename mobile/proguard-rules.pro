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

-keepclassmembers public class * extends com.svartifoss.snfell.actions.PhoneAction {
   public <init>(android.content.Context, android.os.PersistableBundle);
}

-keepclassmembers public class * extends com.matejdro.wearutils.serialization.Bundlable {
   public <init>(android.os.PersistableBundle);
}

# protobuf-javalite ships no consumer rule of its own, and the `!field/*` term above is a
# no-op under R8 (it only recognizes the coarse -dontoptimize/-optimizations toggle, not
# ProGuard's fine-grained categories), so nothing was stopping R8 from removing a message
# field it saw no live getter/setter for on this side of the wire - e.g. several MusicState
# fields the phone only writes (positionAgeMs, albumArtPending, sourceIconTemplate) or only
# reads on the watch. Those fields' Java declarations are gone from the release build, but the
# schema table protoc baked into the class's static initializer still names all of them, so
# the *first* time a message with a missing field is parsed or serialized, resolving that
# table via reflection fails to find the field and throws for the whole message. This is what
# leaves the watch stuck on "ERROR" in a release build - see wear/proguard-rules.pro for the
# fuller writeup of the symptom. Keeping every declared field on every generated message class
# is the standard fix Google's own protobuf-lite consumer rule would supply.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Dagger
-dontwarn com.google.errorprone.annotations.*

# AutoValue
-dontwarn javax.lang.model.**
-dontwarn net.ltgt.gradle.incap.*
