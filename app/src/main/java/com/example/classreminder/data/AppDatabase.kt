package com.example.classreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ClassEntity::class, NoteEntity::class], version = 7, exportSchema = false)
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

        /**
         * v5 → v6：便签支持自定义竖线颜色。存的是调色盘下标，不是 ARGB。
         *
         * 用 ALTER TABLE 加列并给默认值 0，**老便签一条都不丢**，
         * 且升级后颜色统一落在调色盘第一格。
         * 这一列必须和 NoteEntity 的声明完全一致（INTEGER NOT NULL + 默认值），
         * 否则 Room 打开库时 schema 校验会失败。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN colorIndex INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v6 → v7：便签支持**分类**与 **Deadline 截止时刻**。
         *
         * 一次加三列，都用 ALTER TABLE + 默认值，**老便签一条都不丢**：
         *  - `typeIndex` 默认 0，正好落在分类表的第 0 项「空」上，语义天然正确
         *  - `customLabel` 默认 ''，只有「自定义 / Deadline 自定义」两类会写它
         *  - `deadlineAt` 默认 0，表示「没设截止时刻」
         *
         * 三列的声明必须和 NoteEntity 完全一致（类型 / NOT NULL / 默认值），
         * 否则 Room 打开库时 schema 校验会失败。
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN typeIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN customLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN deadlineAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "class_reminder_db"
                )
                    .addMigrations(
                        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
