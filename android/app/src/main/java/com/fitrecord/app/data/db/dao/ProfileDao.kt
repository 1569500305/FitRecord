package com.fitrecord.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.fitrecord.app.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 0")
    suspend fun profile(): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE id = 0")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Upsert
    suspend fun upsert(entity: UserProfileEntity)

    /** 仅测试用：验证"未填体重"分支（规格 6.1） */
    @Query("DELETE FROM user_profile")
    suspend fun delete()
}
