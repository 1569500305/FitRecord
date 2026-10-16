package com.fitrecord.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.fitrecord.app.data.db.entity.MetaEntity

@Dao
interface MetaDao {

    @Query("SELECT value FROM meta WHERE key = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entity: MetaEntity)
}
