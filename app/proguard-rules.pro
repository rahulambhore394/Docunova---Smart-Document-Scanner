# Docunova Production ProGuard & R8 Optimization Rules

# Preserve stacktrace line numbers and essential attributes
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# Gson & Reflection Keep Rules
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}
-keep class com.google.gson.** { *; }

# App Data Models & Room Entities (prevent obfuscation of fields required for serialization/DB)
-keep class com.developer_rahul.docunova.RoomDB.** { *; }
-keep class com.developer_rahul.docunova.Fragments.Files.DriveFileModel { *; }
-keep class com.developer_rahul.docunova.TranslationApiClient$** { *; }

# Retrofit 2
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Google API Client & Google Drive API
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**
-dontwarn org.apache.http.**
-dontnote org.apache.http.**

# Glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# ML Kit Document Scanner & Vision Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# iText 7 PDF Parser
-keep class com.itextpdf.kernel.** { *; }
-keep class com.itextpdf.io.** { *; }
-dontwarn com.itextpdf.**
-dontwarn org.slf4j.**

# DocxHelper
-keep class com.developer_rahul.docunova.DocxHelper { *; }


# Shimmer
-keep class com.facebook.shimmer.** { *; }
-dontwarn com.facebook.shimmer.**

# CircleImageView
-keep class de.hdodenhof.circleimageview.** { *; }
-dontwarn de.hdodenhof.circleimageview.**

# ViewBinding
-keep class com.developer_rahul.docunova.databinding.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
