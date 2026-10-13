package com.fitrecord.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TypeDao {

    /** P5 选择器与图表只看未归档的类型 */
    @Query("SELECT * FROM exercise_type WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<ExerciseTypeEntity>>

    @Query("SELECT * FROM exercise_type ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ExerciseTypeEntity>>

    @Query("SELECT * FROM exercise_type ORDER BY sortOrder, id")
    suspend fun all(): List<ExerciseTypeEntity>

    @Query("SELECT * FROM exercise_type WHERE id = :id")
    suspend fun byId(id: Long): ExerciseTypeEntity?

    @Query("SELECT * FROM exercise_type WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): ExerciseTypeEntity?

    @Insert
    suspend fun insert(entity: ExerciseTypeEntity): Long

    @Insert
    suspend fun insertAll(entities: List<ExerciseTypeEntity>): List<Long>

    /**
     * 种子专用：已存在的类型名（唯一索引冲突）跳过而不覆盖，
     * 因此用户改过的 MET 不会被下次启动打回，而缺掉的内置类型会被补齐（评审 I-5）。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeedAll(entities: List<ExerciseTypeEntity>): List<Long>

    /** 种子与导入用的便捷插入 */
    @Query(
        "INSERT INTO exercise_type(name, met, color, isBuiltin, archived, metOverridden, sortOrder, defaultBodyPartIds) " +
            "VALUES(:name, :met, :color, :isBuiltin, 0, 0, :sortOrder, '')"
    )
    suspend fun insertType(
        name: String,
        met: Double,
        color: Long,
        isBuiltin: Boolean,
        sortOrder: Int,
    ): Long

    @Update
    suspend fun update(entity: ExerciseTypeEntity)

    /** 仅用于无任何记录引用的自定义类型（调用前必须先查 usageCount） */
    @Delete
    suspend fun delete(entity: ExerciseTypeEntity)

    @Query("UPDATE exercise_type SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE exercise_type SET archived = 0 WHERE id = :id")
    suspend fun unarchive(id: Long)

    /** 删除前的引用检查（规格 P7：被引用的类型不得删除） */
    @Query("SELECT COUNT(*) FROM training_record WHERE exerciseTypeId = :id")
    suspend fun usageCount(id: Long): Int
}
