package com.fitrecord.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.fitrecord.app.data.db.entity.DailyNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM daily_note WHERE date = :date")
    suspend fun note(date: Int): DailyNoteEntity?

    @Query("SELECT * FROM daily_note WHERE date = :date")
    fun observeNote(date: Int): Flow<DailyNoteEntity?>

    @Upsert
    suspend fun upsert(entity: DailyNoteEntity)

    @Query("DELETE FROM daily_note WHERE date = :date")
    suspend fun delete(date: Int)

    /** 备份导出用 */
    @Query("SELECT * FROM daily_note ORDER BY date")
    suspend fun all(): List<DailyNoteEntity>
}
