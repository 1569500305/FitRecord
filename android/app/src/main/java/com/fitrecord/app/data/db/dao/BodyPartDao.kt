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

    /**
     * 种子专用。**绝不能用 REPLACE**：SQLite 的 INSERT OR REPLACE 会先隐式 DELETE 再插入，
     * 而 record_body_part.bodyPartId 带 ON DELETE CASCADE —— 每次冷启动会级联清空
     * 用户全部历史部位关联（复审 Critical）。IGNORE 只补齐缺行、不动已有行。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeedAll(entities: List<BodyPartEntity>)
}
