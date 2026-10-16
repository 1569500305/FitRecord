package com.fitrecord.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitrecord.app.data.db.entity.FoodItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Query("SELECT * FROM food_item ORDER BY id")
    suspend fun all(): List<FoodItemEntity>

    @Query("SELECT * FROM food_item ORDER BY id")
    fun observeAll(): Flow<List<FoodItemEntity>>

    /**
     * 种子专用 IGNORE。food_item 目前没有入向外键，REPLACE 也不会丢数据，
     * 但与 BodyPartDao.insertSeedAll 保持同一口径，避免将来给食物表加引用关系时踩同一个坑。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeedAll(items: List<FoodItemEntity>)
}
