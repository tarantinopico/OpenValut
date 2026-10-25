# OpenVault ProGuard / R8 Rules
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keep @kotlinx.serialization.Serializable class ** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.KSerializer ** Companion;
}
