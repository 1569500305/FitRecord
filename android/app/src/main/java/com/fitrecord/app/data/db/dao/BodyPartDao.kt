package com.fitrecord.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitrecord.app.data.db.entity.BodyPartEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyPartDao {

    @Query("SELECT * FROM body_part ORDER BY sortOrder")
    suspend fun all(): List<BodyPartEntity>

    @Query("SELECT * FROM body_part ORDER BY sortOrder")
    fun observeAll(): Flow<List<BodyPartEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BodyPartEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BodyPartEntity>)
}
