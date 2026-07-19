package com.example.classreminder.data

import androidx.room.*

@Dao
interface ClassDao {
    // Order by day and startTime
    @Query("SELECT * FROM classes ORDER BY dayOfWeek, startTime")
    suspend fun getAll(): List<ClassEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ClassEntity)

    @Delete
    suspend fun delete(entity: ClassEntity)
}

