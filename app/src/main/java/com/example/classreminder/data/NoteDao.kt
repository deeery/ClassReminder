package com.example.classreminder.data

import androidx.room.*

/**
 * 便签的数据访问对象。
 *
 * 和 [ClassDao] 一样刻意用**抽象类**而不是接口：需要一层「落库前补同步元数据」的包装方法，
 * 而 Room 的实现是 kapt 生成的 Java 类，不会为 Kotlin 接口的默认方法生成桥接
 * （调用时会 `AbstractMethodError`）。
 *
 * 对调用方而言签名**完全不变**，`MainViewModel` 一行都不用改。
 */
@Dao
abstract class NoteDao {

    /** 按显示顺序取全部便签；position 撞车时用创建时间兜底，保证顺序稳定不跳。**已软删的不返回** */
    @Query("SELECT * FROM notes WHERE deletedAt = 0 ORDER BY position ASC, createdAt ASC")
    abstract suspend fun getAll(): List<NoteEntity>

    /** 按主键查单条，避免 updateNote 里跑全表扫描；已软删的不再返回 */
    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt = 0")
    abstract suspend fun getById(id: Int): NoteEntity?

    /** 原始写入。**不要直接调用** —— 用 [insert] / [insertAll]，它们会补齐同步元数据。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRaw(entity: NoteEntity)

    /** 原始批量写入。**不要直接调用** —— 用 [insertAll]。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAllRaw(entities: List<NoteEntity>)

    /** 新建或覆盖一条便签；落库时补 uid（**仅当为空**）、刷新 updatedAt */
    open suspend fun insert(entity: NoteEntity) = insertRaw(entity.stamped())

    /** 回撤时把快照整批写回；逐条补同步元数据 */
    open suspend fun insertAll(entities: List<NoteEntity>) =
        insertAllRaw(entities.map { it.stamped() })

    /** 原始批量更新。**不要直接调用** —— 用 [updateAll]。 */
    @Update
    abstract suspend fun updateAllRaw(entities: List<NoteEntity>)

    /** 拖动排序后整批写回新的 position；这也是「修改」，所以同样刷新 updatedAt */
    open suspend fun updateAll(entities: List<NoteEntity>) =
        updateAllRaw(entities.map { it.stamped() })

    /** 软删除的底层实现：只打标记。 */
    @Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    abstract suspend fun markDeleted(id: Int, now: Long)

    /**
     * 软删除：只打 `deletedAt` 标记，不物理删行。
     * 物理删行会让同步把「别的设备早已删掉、本机还没收到通知」的便签又推回来（数据复活）。
     */
    open suspend fun deleteById(id: Int) = markDeleted(id, System.currentTimeMillis())

    /**
     * 回撤用：先清空再写快照，等价于整表替换。
     * 这里保持**物理删除** —— 它是本地恢复，不是同步删除，不该留墓碑。
     */
    @Query("DELETE FROM notes")
    abstract suspend fun deleteAll()
}
