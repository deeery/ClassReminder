package com.example.classreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ClassEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classDao(): ClassDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * v2 → v3：把「教师 / 周次」从 notes 里拆成独立字段（按周次过滤课表需要它们）。
         * 用 ALTER TABLE 而不是重建表，老数据一条都不丢。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE classes ADD COLUMN teacher TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE classes ADD COLUMN weeks TEXT NOT NULL DEFAULT ''")
                // 导入时写的 notes 形如「孙利荣 · 1-16周」，拆开；手填的 notes 里没有分隔符，保持原样
                db.execSQL(
                    "UPDATE classes SET teacher = substr(notes, 1, instr(notes, ' · ') - 1), " +
                        "weeks = substr(notes, instr(notes, ' · ') + 3) " +
                        "WHERE instr(notes, ' · ') > 0"
                )
            }
        }

        /**
         * v3 → v4：临时提醒需要带具体日期（只生效一次），长期课程这一列为空。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE classes ADD COLUMN date TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "class_reminder_db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
