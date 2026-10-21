# R8 rules for the watch app. build.gradle pairs this file with proguard-android-optimize.txt, so a
# release build is shrunk, optimized *and* obfuscated, and every rule below is a narrow exception
# for a place where a name is data.
#
# Neither -dontobfuscate nor -dontoptimize belongs here, and neither does plain
# proguard-android.txt (which carries -dontoptimize). Play Console grades each bundle on how much
# of its code R8 was allowed to optimize, rename and remove - the percentages R8 writes into the
# bundle's BUNDLE-METADATA/com.android.tools/r8.json - and flags any category under 25%. 4.2
# shipped with both switches off and was graded 0% optimization and 0% obfuscation.
# R8ConfigurationContractTest pins that, and a broad -keep added to silence some release-only
# crash costs the same grade, so keep each rule as narrow as the name it protects.

# Play retraces reports with the mapping inside the bundle, and a crash forwarded to the phone
# (ExceptionWear) is retraced by hand with this build's mapping - both need the line table and the
# source file attribute.
-keepattributes SourceFile,LineNumberTable

# Obfuscated classes all move into one package, shortening every type reference in the DEX.
-repackageclasses

# Exception type names must survive. The watch forwards an exception to the phone as its type name
# plus its frames (ExceptionService), and CrashlyticsExceptionWearHandler drops routine coroutine
# cancellation by finding "CancellationException" in that name: obfuscated, every cancellation would
# become a Crashlytics report, and every other report would be titled with a meaningless name.
-keepnames class * extends java.lang.Throwable

# protobuf-javalite ships no consumer rule of its own, and a generated message's schema table -
# baked into its static initializer by protoc - names every field it declares. The runtime resolves
# that table by reflection the first time a message is parsed or serialized, so a field R8 removed
# or renamed makes the lookup fail and the whole message throw - not just that one field. Removal
# is what happened: MusicState.time is written by the phone and never read here, R8 dropped it, and
# the first MusicState.parseFrom() threw. Caught by launchWithErrorHandling, that was the
# release-only "always shows ERROR, taps still control playback" bug: outbound commands are raw byte
# payloads that never touch protobuf, only the DataItem-carried MusicState does. Keeping every
# declared field, under its own name, on every generated message class is the rule Google's own
# protobuf-lite consumer rules would supply.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
