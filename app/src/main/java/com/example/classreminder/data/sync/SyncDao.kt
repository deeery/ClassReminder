package com.example.classreminder.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.classreminder.data.ClassEntity
import com.example.classreminder.data.NoteEntity

/**
 * 同步专用的数据访问方法。
 *
 * ## 为什么不加进 [com.example.classreminder.data.ClassDao] / `NoteDao`
 *
 * 那两个 DAO 的方法签名是**业务层在用**的。同步需要「看到已软删的行」
 * 「原样写回 `updatedAt`」「按 uid 查」这几类操作，性质完全不同，
 * 混进去会让业务 DAO 变得含义模糊。
 *
 * 另外桌面端的 [com.example.classreminder.data.SyncDao] 也是独立文件 ——
 * 两端保持一致，改协议时好对照。
 *
 * ## 与业务 DAO 的分工
 *
 * 业务 DAO 只处理「用户可见的」记录（`deletedAt = 0`）；
 * 同步要看到**全部**行 —— 包括已软删的，因为删除本身也要传播。
 */
@Dao
abstract class SyncDao {

    // ── 读：全量快照（含已软删） ─────────────────────────────────

    /** 课程全量。**含**已软删的行 —— 删除要传播给别的设备 */
    @Query("SELECT * FROM classes ORDER BY id ASC")
    abstract suspend fun allClasses(): List<ClassEntity>

    /** 便签全量，同样含已软删 */
    @Query("SELECT * FROM notes ORDER BY id ASC")
    abstract suspend fun allNotes(): List<NoteEntity>

    // ── 读：按 uid 查 ────────────────────────────────────────────

    /** @return 该 uid 在本机的 id；不存在返回 null */
    @Query("SELECT id FROM classes WHERE uid = :uid")
    abstract suspend fun classIdOfUid(uid: String): Int?

    @Query("SELECT id FROM notes WHERE uid = :uid")
    abstract suspend fun noteIdOfUid(uid: String): Int?

    /** 本地那条的 `updatedAt`，用来跟远端比新旧；不存在返回 null */
    @Query("SELECT updatedAt FROM classes WHERE uid = :uid")
    abstract suspend fun classUpdatedAtOfUid(uid: String): Long?

    @Query("SELECT updatedAt FROM notes WHERE uid = :uid")
    abstract suspend fun noteUpdatedAtOfUid(uid: String): Long?

    // ── 写：同步专用 ─────────────────────────────────────────────

    /**
     * 把服务端来的记录写进本地。
     *
     * ⚠️ 这里**必须走 [upsertRaw] 而不是 [com.example.classreminder.data.ClassDao.insert]：
     * 业务 insert 会无条件刷新 `updatedAt = now`，于是「刚从服务端拉下来的记录」
     * 会被打上本地当前时间，下次同步再推上去又变成「客户端最新」→ 无休止的来回翻转。
     * 同步落库必须**原样保留服务端给的 `updatedAt`**。
     *
     * `id` 的分配由调用方（[SyncEngine]）通过 [SyncMerge.pickId] 决定，
     * 因为它需要「已占用 id 集合」才能避开撞号。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRawClass(entity: ClassEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRawNote(entity: NoteEntity)

    // ── 写：清空（首端切换时用） ─────────────────────────────────

    /**
     * 物理清空课程与便签。
     *
     * ⚠️ **只在首端切换（`replace_local = true`）时调用**，且调用方**必须**已经
     * 做过本地备份（设计 v1.3 §5.8 明确要求）——
     * 用户可能在手机上攒了一批只存在本地的课，直接清空就是丢数据。
     *
     * 这里是物理 `DELETE` 而不是软删：这些行马上会被全量拉取的服务端数据替换掉，
     * 留着旧行只会让下一次同步把它们当成「本地新增」又推回服务端。
     */
    @Query("DELETE FROM classes")
    abstract suspend fun deleteAllClasses()

    @Query("DELETE FROM notes")
    abstract suspend fun deleteAllNotes()

    // ── 写：按 id 物理删除（同 uid 收敛时用） ────────────────────

    /**
     * 按主键物理删除若干行。
     *
     * ⚠️ 只给「同一个 uid 出现了多行」这种**铁定是脏数据**的场景用：
     * uid 是跨设备唯一标识，同一 uid 的多行必然是同一条记录被重复写进去的，
     * 留哪一行都不影响语义（见 `SyncEngine.collapseSameUidDuplicates`）。
     * 正常删除一律走软删（`deletedAt`），否则删除传播不出去。
     *
     * @return 实际删掉的行数
     */
    @Query("DELETE FROM classes WHERE id IN (:ids)")
    abstract suspend fun deleteClassesByIds(ids: List<Int>): Int

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    abstract suspend fun deleteNotesByIds(ids: List<Int>): Int

    // ── 维护 ────────────────────────────────────────────────────

    /**
     * 物理清理软删除超过 [cutoff] 的行。
     *
     * 软删除让数据不会真正消失（这是同步正确性的前提），
     * 但代价是删除的东西会一直躺在库里。超过保留期就彻底清掉。
     *
     * 保留 30 天而不是服务端的 90 天：手机空间更紧张，
     * 而且「删了 30 天还想找回」的用户本来就该去用备份文件恢复。
     */
    @Query("DELETE FROM classes WHERE deletedAt > 0 AND deletedAt < :cutoff")
    abstract suspend fun purgeOldClassTombstones(cutoff: Long): Int

    @Query("DELETE FROM notes WHERE deletedAt > 0 AND deletedAt < :cutoff")
    abstract suspend fun purgeOldNoteTombstones(cutoff: Long): Int

    /** 分配一个当前未占用的 id（同步落库时给新记录用） */
    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM classes")
    abstract suspend fun nextFreeClassId(): Int

    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM notes")
    abstract suspend fun nextFreeNoteId(): Int
}
