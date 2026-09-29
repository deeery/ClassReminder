package com.example.classreminder.data

import androidx.room.*

@Dao
interface NoteDao {
    /** 按显示顺序取全部便签；position 撞车时用创建时间兜底，保证顺序稳定不跳 */
    @Query("SELECT * FROM notes ORDER BY position ASC, createdAt ASC")
    suspend fun getAll(): List<NoteEntity>

    /** 按主键查单条，避免 updateNote 里跑全表扫描 */
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Int): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NoteEntity)

    /** 回撤时把快照整批写回 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<NoteEntity>)

    /** 拖动排序后整批写回新的 position */
    @Update
    suspend fun updateAll(entities: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Int)

    /** 回撤用：先清空再写快照，等价于整表替换 */
    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
