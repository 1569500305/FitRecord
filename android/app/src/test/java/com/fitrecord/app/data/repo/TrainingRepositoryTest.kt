package com.fitrecord.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.db.entity.UserProfileEntity
import com.fitrecord.app.data.db.seed.BodyParts
import com.fitrecord.app.data.db.seed.BuiltinExercises
import com.fitrecord.app.data.db.seed.Foods
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 训练记录仓库（规格 模块1-2 / 4.3）：增删改查、撤销快照、今日开关、卡路里落库口径 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TrainingRepositoryTest {

    private lateinit var db: FitRecordDatabase
    private lateinit var repo: TrainingRepository
    private val day = LocalDate.of(2026, 10, 12)

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FitRecordDatabase::class.java
        ).build()
        SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao()).ensureSeeded()
        db.profileDao().upsert(UserProfileEntity(weightKg = 70.0, updatedAt = 0))
        repo = TrainingRepository(db.trainingDao(), db.typeDao(), db.profileDao(), db.noteDao(), db.foodDao())
    }

    @Test fun `seed produced 8 types 8 parts and 10 foods`() = runBlocking {
        assertEquals(BuiltinExercises.items.size, db.typeDao().all().size)
        assertEquals(BodyParts.items.size, db.bodyPartDao().all().size)
        assertEquals(Foods.items.size, db.foodDao().all().size)
    }

    @Test fun `add stores computed calories and the body part links`() = runBlocking {
        val id = repo.add(day, exerciseTypeId = 1, durationMin = 45, bodyPartIds = listOf(2, 5), note = "x")
        val r = repo.dayRecords(day).first { it.id == id }
        assertEquals(368.0, r.calories!!, 1e-9)          // 7.0 × 70 × 0.75 → 368
        assertEquals(listOf(2L, 5L), r.bodyPartIds)      // 由 SQL ORDER BY bodyPartId（V1 中 sortOrder == id）
        assertEquals("划船机", r.type.name)
        assertEquals(day, r.date)
    }

    @Test fun `add without profile stores null calories`() = runBlocking {
        db.profileDao().delete()
        val id = repo.add(day, 1, 30, emptyList(), "")
        assertNull(repo.dayRecords(day).first { it.id == id }.calories)
    }

    @Test fun `segment count is derived from record count, never a stored field`() = runBlocking {
        repo.add(day, 1, 30, emptyList(), "")
        repo.add(day, 4, 20, emptyList(), "")
        assertEquals(2, repo.dayRecords(day).size)       // 早跑晚骑 = 2 段（规格模块1-2）
    }

    @Test fun `update recomputes calories and replaces body parts`() = runBlocking {
        val id = repo.add(day, 1, 30, listOf(2L), "旧")
        repo.update(id, day, exerciseTypeId = 4, durationMin = 60, bodyPartIds = listOf(8L), note = "新")
        val r = repo.dayRecords(day).single()
        assertEquals(60, r.durationMin)
        assertEquals(listOf(8L), r.bodyPartIds)          // 旧关联必须被替换而非叠加
        assertEquals("新", r.note)
        assertEquals(581.0, r.calories!!, 1e-9)          // 跑步机 8.3 × 70kg × 1h = 581
    }

    @Test fun `delete returns a snapshot and restore brings it back intact`() = runBlocking {
        val id = repo.add(day, 1, 45, listOf(2L), "note")
        val snapshot = repo.delete(id)
        assertNotNull(snapshot)
        assertTrue(repo.dayRecords(day).isEmpty())

        val newId = repo.restore(snapshot!!)
        val back = repo.dayRecords(day).first { it.id == newId }
        assertEquals(45, back.durationMin)
        assertEquals(listOf(2L), back.bodyPartIds)
        assertEquals("note", back.note)
        assertEquals(1, repo.dayRecords(day).size)
    }

    @Test fun `delete of a missing id returns null`() = runBlocking {
        assertNull(repo.delete(999_999L))
    }

    @Test fun `setTrainedToday false clears the day and reports Cleared`() = runBlocking {
        repo.add(day, 1, 30, emptyList(), "")
        repo.add(day, 2, 30, emptyList(), "")
        assertEquals(TrainingRepository.TodayToggle.Cleared, repo.setTrainedToday(day, trained = false))
        assertTrue(repo.dayRecords(day).isEmpty())
    }

    @Test fun `setTrainedToday false on an empty day is a no-op`() = runBlocking {
        assertEquals(
            TrainingRepository.TodayToggle.AlreadyUnmarked,
            repo.setTrainedToday(day, trained = false),
        )
    }

    @Test fun `setTrainedToday true creates exactly one default 60min record`() = runBlocking {
        assertEquals(TrainingRepository.TodayToggle.Marked, repo.setTrainedToday(day, trained = true))
        val records = repo.dayRecords(day)
        assertEquals(1, records.size)
        assertEquals(60, records.single().durationMin)
        assertEquals("划船机", records.single().type.name)   // 启用列表首个（sortOrder=0）
    }

    @Test fun `setTrainedToday true on a trained day is AlreadyMarked`() = runBlocking {
        repo.add(day, 1, 30, emptyList(), "")
        assertEquals(
            TrainingRepository.TodayToggle.AlreadyMarked,
            repo.setTrainedToday(day, trained = true),
        )
        assertEquals(1, repo.dayRecords(day).size)
    }

    @Test fun `observeDay re-emits after a write`() = runBlocking {
        assertEquals(0, repo.observeDay(day).first().size)
        repo.add(day, 1, 60, emptyList(), "")
        assertEquals(1, repo.observeDay(day).first().size)
    }

    @Test fun `day note round trips and blank text clears it`() = runBlocking {
        repo.saveDayNote(day, "今天背有点紧")
        assertEquals("今天背有点紧", repo.dayNote(day))
        repo.saveDayNote(day, "   ")
        assertEquals("", repo.dayNote(day))
    }

    @Test fun `observeRecords covers an arbitrary epoch day range`() = runBlocking {
        repo.add(day, 1, 30, emptyList(), "")
        repo.add(day.plusDays(40), 1, 30, emptyList(), "")
        val from = day.toEpochDay().toInt()
        assertEquals(1, repo.observeRecords(IntRange(from, from)).first().size)
        assertEquals(2, repo.observeRecords(IntRange(from, from + 41)).first().size)
    }

    @Test fun `observeWeightKg is null when profile is absent`() = runBlocking {
        assertEquals(70.0, repo.observeWeightKg().first()!!, 1e-9)
        db.profileDao().delete()
        assertNull(repo.observeWeightKg().first())
    }
}
