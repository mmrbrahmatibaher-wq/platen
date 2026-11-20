# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.mahdi.platen.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.mahdi.platen.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.mahdi.platen.domain.Glyph { *; }
-keep class com.mahdi.platen.domain.Glyph$$serializer { *; }
# Room entities / generated database.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
# Enum names are persisted in DataStore preferences (theme, correction mode): keep them stable.
-keepclassmembers,allowoptimization enum com.mahdi.platen.data.PaperTheme { *; }
-keepclassmembers,allowoptimization enum com.mahdi.platen.domain.CorrectionMode { *; }
# Room: DAOs and type converters are reached through generated code.
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * { @androidx.room.TypeConverter *; }
# DataStore Preferences uses protobuf-lite internally.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { <fields>; }
