# FlickTV 使用 Gson 反射读写这些配置与缓存模型；类名可混淆，但字段名必须保留。
-keepclassmembers class com.luhaopeng.flicktv.data.model.** {
    <fields>;
}
-keepattributes Signature,*Annotation*

# Room 运行时会读取数据库实现与实体注解。
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# 以下均为当前 min/compile SDK 或 OkHttp 自动探测的可选运行时，不随 App 打包。
-dontwarn android.media.ApplicationMediaCapabilities$Builder
-dontwarn android.media.ApplicationMediaCapabilities
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
