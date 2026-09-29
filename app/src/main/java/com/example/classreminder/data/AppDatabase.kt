package com.example.classreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ClassEntity::class, NoteEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classDao(): ClassDao
    abstract fun noteDao(): NoteDao

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

        /**
         * v4 → v5：新增「快速便签」表。列名/类型/notNull/主键必须和 NoteEntity 完全一致，
         * 否则 Room 打开库时会做 schema 校验失败。老数据不受影响。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "class_reminder_db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
