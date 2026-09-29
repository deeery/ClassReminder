package com.example.classreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 快速便签。
 *
 * [position] 是便签在列表里的显示顺序（越小越靠上）：新增时取「当前最小值 - 1」，
 * 于是新便签天然排在最顶端；拖动排序时整批重写。
 * [createdAt] 只作为 position 相同时的稳定兜底，不参与排序语义。
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: Int,
    val text: String,
    val position: Int,
    val createdAt: Long = 0L
)
