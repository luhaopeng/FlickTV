package com.luhaopeng.flicktv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlayHistoryEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VodBoxDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile private var instance: VodBoxDatabase? = null

        fun get(context: Context): VodBoxDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                VodBoxDatabase::class.java,
                "vodbox.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
