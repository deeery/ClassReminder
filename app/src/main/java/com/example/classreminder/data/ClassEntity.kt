package com.example.classreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val dayOfWeek: String,
    // Separate start and end time in HH:mm format
    val startTime: String,
    val endTime: String,
    // Classroom / room number
    val room: String = "",
    val notes: String = ""
)

