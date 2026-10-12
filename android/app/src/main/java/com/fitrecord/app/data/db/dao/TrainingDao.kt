package com.fitrecord.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fitrecord.app.data.db.RecordWithType
import com.fitrecord.app.data.db.entity.RecordBodyPartEntity
import com.fitrecord.app.data.db.entity.TrainingRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingDao {

    @Insert
    suspend fun insert(entity: TrainingRecordEntity): Long

    @Insert
    suspend fun insertAll(entities: List<TrainingRecordEntity>): List<Long>

    @Update
    suspend fun update(entity: TrainingRecordEntity)

    @Delete
    suspend fun delete(entity: TrainingRecordEntity)

    @Query("DELETE FROM training_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM training_record")
    suspend fun deleteAll()

    @Query("SELECT * FROM training_record WHERE id = :id")
    suspend fun byId(id: Long): TrainingRecordEntity?

    /**
     * 日历与当日页的主查询。一次 JOIN 带回类型名/颜色/MET，避免 N+1。
     * from == to 时即为"单日"查询。
     */
    @Query(
        """SELECT r.id, r.date, r.exerciseTypeId, r.durationMin, r.calories, r.note,
                  t.name AS typeName, t.color AS typeColor, t.met
           FROM training_record r JOIN exercise_type t ON t.id = r.exerciseTypeId
           WHERE r.date BETWEEN :from AND :to
           ORDER BY r.date, r.id"""
    )
    fun observeRange(from: Int, to: Int): Flow<List<RecordWithType>>

    @Query(
        """SELECT r.id, r.date, r.exerciseTypeId, r.durationMin, r.calories, r.note,
                  t.name AS typeName, t.color AS typeColor, t.met
           FROM training_record r JOIN exercise_type t ON t.id = r.exerciseTypeId
           WHERE r.date BETWEEN :from AND :to
           ORDER BY r.date, r.id"""
    )
    suspend fun rangeOnce(from: Int, to: Int): List<RecordWithType>

    @Query(
        """SELECT r.id, r.date, r.exerciseTypeId, r.durationMin, r.calories, r.note,
                  t.name AS typeName, t.color AS typeColor, t.met
           FROM training_record r JOIN exercise_type t ON t.id = r.exerciseTypeId
           WHERE r.id = :id"""
    )
    suspend fun recordWithType(id: Long): RecordWithType?

    // ---------- 锻炼部位多对多 ----------

    @Query(
        """SELECT bp.id FROM record_body_part rb
           JOIN body_part bp ON bp.id = rb.bodyPartId
           WHERE rb.recordId = :recordId ORDER BY bp.sortOrder"""
    )
    suspend fun bodyPartIds(recordId: Long): List<Long>

    @Query("DELETE FROM record_body_part WHERE recordId = :recordId")
    suspend fun clearBodyParts(recordId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkBodyParts(links: List<RecordBodyPartEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkBodyPart(link: RecordBodyPartEntity)

    /** 备份导出用：一次取回全部记录与部位关联 */
    @Query("SELECT * FROM record_body_part ORDER BY recordId, bodyPartId")
    suspend fun allLinks(): List<RecordBodyPartEntity>
}
