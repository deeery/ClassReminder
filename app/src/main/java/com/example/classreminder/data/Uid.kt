package com.example.classreminder.data

import java.util.UUID

/**
 * 生成同步用的全局唯一标识（UUIDv4）。
 *
 * 为什么需要它：两端的本地主键都是应用自己分配的 `id: Int`（没有 autoGenerate），
 * 各自新建记录必然撞号，不能拿来跨设备对齐。同步层只认 [uid]，
 * 本地 DAO / UI 继续用 `id`，所以现有查询逻辑一行都不用改（设计方案 v1.3 §5.2）。
 *
 * 只在**落库那一刻**生成：DAO 写入时若 `uid` 为空就补一个，之后原样保留。
 * 绝不能每次启动重新生成——那样同一台设备每次开机都会换一批 uid，
 * 同步层会把它们当成一批新记录，历史数据就重复了。
 *
 * ⚠️ 也**不要**把 `UUID.randomUUID()` 写成实体构造参数的默认值：
 * 那样每次 `copy()` 都会换一个新 uid（比如拖动便签排序时的
 * `note.copy(position = i)`），记录的身份就断了。
 */
internal fun newUid(): String = UUID.randomUUID().toString()

/**
 * 落库前补齐同步元数据。
 *
 * - `uid` **只补一次**：已经是非空就原样保留，保证记录身份稳定
 * - `updatedAt` 刷新为当前时刻（LWW 冲突解决要靠它）
 * - `deletedAt` **原样保留**：调用方要软删除时会自己带上标记，
 *   这里不能把它清掉，否则「删除」会被写回成「未删除」
 */
internal fun ClassEntity.stamped(now: Long = System.currentTimeMillis()): ClassEntity =
    copy(uid = uid.ifBlank { newUid() }, updatedAt = now)

internal fun NoteEntity.stamped(now: Long = System.currentTimeMillis()): NoteEntity =
    copy(uid = uid.ifBlank { newUid() }, updatedAt = now)
