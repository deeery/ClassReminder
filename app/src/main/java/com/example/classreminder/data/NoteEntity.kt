package com.example.classreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 快速便签。
 *
 * [position] 是便签在列表里的显示顺序（越小越靠上）：新增时取「当前最小值 - 1」，
 * 于是新便签天然排在最顶端；拖动排序时整批重写。
 * [createdAt] 只作为 position 相同时的稳定兜底，不参与排序语义。
 *
 * [colorIndex] 是便签左侧竖线的颜色，取值是 [NOTE_PALETTE] 的下标（0..7）。
 * **存下标而不是 ARGB**：调色盘是一份受控的固定色板，用户是在给定选项里挑，
 * 而不是自由取色；存下标能让「同一份数据在浅色/深色主题下各自取到合适的色值」
 * （见 NoteColors），将来要微调某个颜色的明度也不必改库。
 * 老数据（v5 及以前）没有这一列，迁移时统一补 0，即调色盘第一格。
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: Int,
    val text: String,
    val position: Int,
    val createdAt: Long = 0L,
    val colorIndex: Int = DEFAULT_NOTE_COLOR
)

/** 调色盘里可选的 8 种颜色数量。UI 和取值都以此为准 */
const val NOTE_COLOR_COUNT = 8

/** 新建便签时的默认色号 */
const val DEFAULT_NOTE_COLOR = 0
