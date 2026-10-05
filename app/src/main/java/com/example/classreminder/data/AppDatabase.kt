package com.example.classreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.classreminder.data.sync.SyncDao

@Database(entities = [ClassEntity::class, NoteEntity::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classDao(): ClassDao
    abstract fun noteDao(): NoteDao

    /**
     * 同步专用的 DAO。
     *
     * 与 [ClassDao] / [NoteDao] **刻意并存**，不是替代关系：
     * 业务 DAO 只看`deletedAt = 0`（软删的当不存在），而同步必须看到全部行 ——
     * 墓碑不推上去的话，「我在桌面端删了」这条信息就丢了，
     * 手机会把它当成还在的记录一直留着。
     *
     * 同理，业务 DAO 的写入会无条件刷新 `updatedAt = now`，
     * 同步落库若走它就会和远端无限互相覆盖（每轮都以为自己更新）。
     * 所以 [SyncDao] 只暴露原样写入的方法。
     */
    abstract fun syncDao(): SyncDao

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

        /**
         * v7 → v8：`classes` / `notes` 各补三列**同步元数据**（uid / updatedAt / deletedAt）。
         *
         * 为什么需要它们（设计方案 v1.3 §5.2 / §5.3）：
         *  - 两端的本地主键都是应用自己分配的 `id: Int`（没有 autoGenerate），各自新建记录必然
         *    撞号，不能拿来跨设备对齐 —— 所以新增 `uid`（UUIDv4）作为同步层的唯一标识，
         *    **本地 DAO / UI 继续用 `id`**，现有查询逻辑一行都不用改。
         *  - 没有 `deletedAt` 的话，「A 删除 → B 不知情 → 下次同步又推回来」会导致**数据复活**，
         *    所以删除改成打标记（见 DAO）。
         *
         * 老库里的行没有 uid，这里**逐行补一个并固化落库**。
         * 绝不能留到运行时惰性生成：那样每次启动都会换一批 uid，同步层会把它们当成一批新记录，
         * 历史数据就被复制了。
         *
         * 三列的声明必须和实体完全一致（类型 / NOT NULL / 默认值），
         * 否则 Room 打开库时 schema 校验会失败。
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("classes", "notes")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `deletedAt` INTEGER NOT NULL DEFAULT 0")
                    backfillUids(db, table)
                }
            }
        }

        /**
         * v8 → v9：把便签的单字段 `text` 拆成 `title` + `content`。
         *
         * **只能重建表，不能只 `ADD COLUMN`** —— 这次要的不是「多两列」而是「少一列」。
         * 而 minSdk 21 自带的 SQLite 是 3.8.6：既没有 `RENAME COLUMN`（3.25+），
         * 也没有 `DROP COLUMN`（3.35+）。桌面端用的是新版 sqlite-jdbc、本来能做，
         * 但两端迁移写法必须一致才谈得上「`.db` 可互开」，所以两边都走
         * 「建新表 → 搬数据 → 删旧表 → 改名」。
         *
         * 搬运规则：`title = text`、`content = ''`。
         * 用户的原话是「原先的内容直接加入标题」，所以老便签整条文本落进标题，
         * 正文从空串起步 —— 不会有「迁移后标题为空」的存量数据。
         *
         * ⚠️ 建表语句里的列顺序必须与 [NoteEntity] 的字段顺序**逐列一致**
         * （Room 会拿它做 schema 校验，且两端 `.db` 要能互开）。
         * 这里刻意**不写 `DEFAULT`** —— 与 Room 自动生成的 DDL 保持同形，
         * 免得「迁移来的库」和「全新装的库」长得不一样。
         *
         * 整段不需要自己开事务：Room 已经把每个迁移包在事务里了。
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes_new` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`colorIndex` INTEGER NOT NULL, " +
                        "`typeIndex` INTEGER NOT NULL, " +
                        "`customLabel` TEXT NOT NULL, " +
                        "`deadlineAt` INTEGER NOT NULL, " +
                        "`uid` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `notes_new` " +
                        "(`id`, `title`, `content`, `position`, `createdAt`, `colorIndex`, " +
                        "`typeIndex`, `customLabel`, `deadlineAt`, `uid`, `updatedAt`, `deletedAt`) " +
                        "SELECT `id`, `text`, '', `position`, `createdAt`, `colorIndex`, " +
                        "`typeIndex`, `customLabel`, `deadlineAt`, `uid`, `updatedAt`, `deletedAt` " +
                        "FROM `notes`"
                )
                db.execSQL("DROP TABLE `notes`")
                db.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")
            }
        }

        /**
         * 给表里所有 uid 为空的行补一个 UUIDv4。
         *
         * 这里不再开事务：Room 已经把整个迁移包在事务里了，
         * 嵌套 `beginTransaction` 在 SQLite 上会抛「cannot start a transaction within a transaction」。
         */
        private fun backfillUids(db: SupportSQLiteDatabase, table: String) {
            val ids = ArrayList<Long>()
            db.query("SELECT `id` FROM `$table` WHERE `uid` IS NULL OR `uid` = ''").use { c ->
                while (c.moveToNext()) ids += c.getLong(0)
            }
            if (ids.isEmpty()) return
            ids.forEach { id ->
                db.execSQL("UPDATE `$table` SET `uid` = ? WHERE `id` = ?", arrayOf<Any>(newUid(), id))
            }
        }

        fun getInstance(context: Context): AppDatabase {            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "class_reminder_db"
                )
                    .addMigrations(
                        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
