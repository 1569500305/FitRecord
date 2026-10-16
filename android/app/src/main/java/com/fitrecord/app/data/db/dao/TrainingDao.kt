package com.fitrecord.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fitrecord.app.data.db.RecordWithType
import com.fitrecord.app.data.db.entity.RecordBodyPartEntity
import com.fitrecord.app.data.db.entity.TrainingRecordEntity
import kotlinx.coroutines.flow.Flow

/** JOIN 一次带回类型全字段，避免 toDayRecord 再查 byId（评审 I-1） */
private const val SELECT_WITH_TYPE = """
    SELECT r.id, r.date, r.exerciseTypeId, r.durationMin, r.calories, r.note,
           r.setCount, r.repsPerSet,
           t.name AS typeName, t.color AS typeColor, t.met,
           t.isBuiltin AS typeIsBuiltin, t.archived AS typeArchived,
           t.metOverridden AS typeMetOverridden, t.sortOrder AS typeSortOrder,
           t.defaultBodyPartIds AS typeDefaultBodyPartIds,
           t.recordBySets AS typeRecordBySets
    FROM training_record r JOIN exercise_type t ON t.id = r.exerciseTypeId
"""

/**
 * 训练记录读写。
 *
 * 性能约定（评审 I-1）：一条记录的**全部**类型信息由 JOIN 一次带回
 * （`RecordWithType` 的 type* 字段），部位关联用区间批量查询后在内存分组。
 * 禁止出现"每行一次 byId / 每行一次 bodyPartIds"的 N+1 —— observeDay 是
 * 每次写入都会 re-emit 的热路径，N+1 会直接压在点击保存的响应时间里。
 */
@Dao
interface TrainingDao {

    @Insert
    suspend fun insert(entity: TrainingRecordEntity): Long

    @Update
    suspend fun update(entity: TrainingRecordEntity)

    @Delete
    suspend fun delete(entity: TrainingRecordEntity)

    @Query("DELETE FROM training_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 单语句清空当日（评审 I-3）：天然原子，且返回真实删除行数 */
    @Query("DELETE FROM training_record WHERE date = :day")
    suspend fun deleteByDay(day: Int): Int

    @Query("DELETE FROM training_record")
    suspend fun deleteAll()

    @Query("SELECT * FROM training_record WHERE id = :id")
    suspend fun byId(id: Long): TrainingRecordEntity?

    @Query("$SELECT_WITH_TYPE WHERE r.date BETWEEN :from AND :to ORDER BY r.date, r.id")
    fun observeRange(from: Int, to: Int): Flow<List<RecordWithType>>

    @Query("$SELECT_WITH_TYPE WHERE r.date BETWEEN :from AND :to ORDER BY r.date, r.id")
    suspend fun rangeOnce(from: Int, to: Int): List<RecordWithType>

    @Query("$SELECT_WITH_TYPE WHERE r.id = :id")
    suspend fun recordWithType(id: Long): RecordWithType?

    // ---------- 锻炼部位多对多（区间批量，非逐行） ----------

    @Query(
        """SELECT rb.recordId, rb.bodyPartId FROM record_body_part rb
           JOIN training_record r ON r.id = rb.recordId
           WHERE r.date BETWEEN :from AND :to ORDER BY rb.recordId, rb.bodyPartId"""
    )
    fun observeLinksInRange(from: Int, to: Int): Flow<List<RecordBodyPartEntity>>

    @Query(
        """SELECT rb.recordId, rb.bodyPartId FROM record_body_part rb
           JOIN training_record r ON r.id = rb.recordId
           WHERE r.date BETWEEN :from AND :to ORDER BY rb.recordId, rb.bodyPartId"""
    )
    suspend fun linksInRange(from: Int, to: Int): List<RecordBodyPartEntity>

    /** Room 不支持 @Query 返回 List<Long>，因此取回关联行本身由调用方投影 */
    @Query("SELECT recordId, bodyPartId FROM record_body_part WHERE recordId = :recordId ORDER BY bodyPartId")
    suspend fun linksOf(recordId: Long): List<RecordBodyPartEntity>

    @Query("DELETE FROM record_body_part WHERE recordId = :recordId")
    suspend fun clearBodyParts(recordId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkBodyParts(links: List<RecordBodyPartEntity>)

    /** 备份导出用：一次取回全部关联 */
    @Query("SELECT recordId, bodyPartId FROM record_body_part ORDER BY recordId, bodyPartId")
    suspend fun allLinks(): List<RecordBodyPartEntity>

    // ---------- 原子写路径（评审 I-4）----------

    /**
     * 记录与其部位关联必须在同一事务内落库。
     * 否则中途失败会留下"有一段记录、零个部位"的脏数据 —— 它会从雷达图里静默消失。
     */
    @Transaction
    suspend fun insertWithBodyParts(
        entity: TrainingRecordEntity,
        bodyPartIds: List<Long>,
    ): Long {
        val id = insert(entity)
        if (bodyPartIds.isNotEmpty()) {
            linkBodyParts(bodyPartIds.distinct().map { RecordBodyPartEntity(id, it) })
        }
        return id
    }

    @Transaction
    suspend fun updateWithBodyParts(
        entity: TrainingRecordEntity,
        bodyPartIds: List<Long>,
    ) {
        update(entity)
        clearBodyParts(entity.id)
        if (bodyPartIds.isNotEmpty()) {
            linkBodyParts(bodyPartIds.distinct().map { RecordBodyPartEntity(entity.id, it) })
        }
    }

    /** 删记录并拿回它的部位关联（撤销快照用），全程单事务 */
    @Transaction
    suspend fun deleteAndReturn(entityId: Long): Pair<RecordWithType?, List<Long>> {
        val row = recordWithType(entityId) ?: return null to emptyList()
        val parts = linksOf(entityId).map { it.bodyPartId }
        deleteById(entityId)
        return row to parts
    }
}
