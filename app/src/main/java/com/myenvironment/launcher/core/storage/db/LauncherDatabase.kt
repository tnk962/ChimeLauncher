package com.myenvironment.launcher.core.storage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserPageEntity::class,
        LayoutItemEntity::class,
        DockItemEntity::class,
        BackupSnapshotEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun launcherDao(): LauncherDao

    companion object {
        private const val DATABASE_NAME = "my_launcher.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE layout_items ADD COLUMN appWidgetId INTEGER NOT NULL DEFAULT -1"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE layout_items ADD COLUMN folderAppsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE dock_items ADD COLUMN folderAppsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        @Volatile
        private var instance: LauncherDatabase? = null

        fun getInstance(context: Context): LauncherDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LauncherDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
