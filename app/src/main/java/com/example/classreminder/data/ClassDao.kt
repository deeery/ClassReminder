package com.example.classreminder.data

import androidx.room.*

/**
 * 课程表的数据访问对象。
 *
 * 这里刻意用**抽象类**而不是接口：需要一层「落库前补同步元数据」的包装方法，
 * 而 Room 的 DAO 实现是 kapt 生成的 Java 类，**不会**为 Kotlin 接口的默认方法
 * 生成桥接（`-Xjvm-default=disable` 下接口默认方法编译成 `DefaultImpls` + 抽象方法），
 * 调用时会 `AbstractMethodError`。抽象类的具体方法没有这个问题
 * （Room 官方文档里的 `@Transaction` 包装方法就是这个形态）。
 *
 * 对调用方而言签名**完全不变**（`getAll` / `insert` / `delete` / `deleteAll`），
 * 所以 `MainViewModel` 与 `ClassReminderService` 一行都不用改。
 */
@Dao
abstract class ClassDao {

    /** 与原来一致：按星期（字符串字典序恰好 Monday<Tuesday<…）再按开始时间升序；**已软删的不返回** */
    @Query("SELECT * FROM classes WHERE deletedAt = 0 ORDER BY dayOfWeek, startTime")
    abstract suspend fun getAll(): List<ClassEntity>

    /**
     * 原始写入。**不要直接调用** —— 用 [insert]，它会补齐 uid 与 updatedAt。
     * 之所以是公开的抽象方法，是因为 Room 只认带注解的抽象方法。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRaw(entity: ClassEntity)

    /** 新建或覆盖一条课程；落库时补 uid（**仅当为空**，保证记录身份稳定）、刷新 updatedAt */
    open suspend fun insert(entity: ClassEntity) = insertRaw(entity.stamped())

    /** 软删除的底层实现：只打标记。 */
    @Query("UPDATE classes SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    abstract suspend fun markDeleted(id: Int, now: Long)

    /**
     * 软删除：不再物理删行，只打 `deletedAt` 标记。
     * 物理删行会让同步把「别的设备早已删掉、本机还没收到通知」的课程又推回来（数据复活）。
     */
    open suspend fun delete(entity: ClassEntity) =
        markDeleted(entity.id, System.currentTimeMillis())

    /**
     * 覆盖导入用：清空整表再写入备份里的课程。
     * 这里保持**物理删除** —— 它是本地恢复，不是同步删除，不该留墓碑。
     */
    @Query("DELETE FROM classes")
    abstract suspend fun deleteAll()
}
