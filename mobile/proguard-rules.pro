# R8 rules for the phone app. build.gradle pairs this file with proguard-android-optimize.txt, so a
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

# Crashlytics and Play retrace reports with the mapping each release build uploads, which needs
# the line table and the source file attribute.
-keepattributes SourceFile,LineNumberTable

# Obfuscated classes all move into one package, shortening every type reference in the DEX.
-repackageclasses

# Exception type names stay readable without a mapping: they are the issue titles, the start of
# every toString() written to the logs, and what log filters match on. Unused ones may still go.
-keepnames class * extends java.lang.Throwable

# Actions are stored by class name. Bundlable.writeToBundle puts getCanonicalName() into every
# saved action - both button configs, the action list, every backup, and res/raw/default_config.json -
# and PhoneAction.deserialize resolves it with Class.forName and the (Context, PersistableBundle)
# constructor. The same name is the actionKey the watch receives and matches against
# StandardActions. A renamed class would therefore silently drop every saved assignment of it and
# every watch-side match, so each subclass keeps its exact name and that constructor - including
# one that no code constructs any more, since a config written by an older build can still name it.
-keep class * extends com.svartifoss.snfell.actions.PhoneAction {
    public <init>(android.content.Context, android.os.PersistableBundle);
}
# The same contract for any other Bundlable (ButtonInfo is the one written to disk today), which
# Bundlable.deserialize rebuilds through its one-argument constructor.
-keep class * extends com.matejdro.wearutils.serialization.Bundlable {
    public <init>(android.os.PersistableBundle);
}

# protobuf-javalite ships no consumer rule of its own, and a generated message's schema table -
# baked into its static initializer by protoc - names every field it declares. The runtime resolves
# that table by reflection the first time a message is parsed or serialized, so a field R8 removed
# (because this side of the wire never reads or never writes it - e.g. the MusicState fields the
# phone only writes, positionAgeMs, albumArtPending, sourceIconTemplate) or renamed makes the lookup
# fail and the whole message throw. That is the release-only bug that left the watch stuck on
# "ERROR" - see wear/proguard-rules.pro. Keeping every declared field, under its own name, on every
# generated message class is the rule Google's own protobuf-lite consumer rules would supply.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Dagger
-dontwarn com.google.errorprone.annotations.*

# AutoValue
-dontwarn javax.lang.model.**
-dontwarn net.ltgt.gradle.incap.*
