package com.fitrecord.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.db.entity.UserProfileEntity
import com.fitrecord.app.domain.backup.BackupCodec
import com.fitrecord.app.domain.backup.BackupFile
import com.fitrecord.app.domain.backup.ConflictPolicy
import com.fitrecord.app.domain.backup.CsvCodec
import com.fitrecord.app.domain.backup.ExerciseTypeDto
import com.fitrecord.app.domain.backup.RecordDto
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * 备份仓库（规格模块4-3 / 4.7 / 6.5）。全程内存库 + 内存流，不触真 SAF。
 * 颜色解析器给固定哨兵值即可 —— 这里守的是数据落库与事务原子性，不是取色。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupRepositoryTest {

    private lateinit var db: FitRecordDatabase
    private lateinit var training: TrainingRepository
    private lateinit var backup: BackupRepository
    private val day = LocalDate.of(2026, 10, 12)

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FitRecordDatabase::class.java,
        ).build()
        SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao()).ensureSeeded()
        db.profileDao().upsert(UserProfileEntity(weightKg = 70.0, updatedAt = 0))
        training = TrainingRepository(db.trainingDao(), db.typeDao(), db.profileDao(), db.noteDao(), db.foodDao())
        backup = BackupRepository(
            db = db,
            trainingDao = db.trainingDao(),
            typeDao = db.typeDao(),
            profileDao = db.profileDao(),
            noteDao = db.noteDao(),
            bodyPartDao = db.bodyPartDao(),
            foodDao = db.foodDao(),
            metaDao = db.metaDao(),
            colorForNewType = { 0xFF123456L },
        )
    }

    @Test fun `export then import into empty database restores identical rows`() = runBlocking {
        training.add(day, 1, 45, listOf(2, 5), "note")
        training.add(day.plusDays(1), 4, 30, listOf(8), "")
        val out = ByteArrayOutputStream()
        assertEquals(2, backup.exportJson(out))

        db.trainingDao().deleteAll()
        val outcome = backup.apply(ByteArrayInputStream(out.toByteArray()), ConflictPolicy.SKIP)
        assertEquals(2, outcome.inserted)
        assertEquals(0, outcome.errors)
        assertEquals(2, db.trainingDao().rangeOnce(0, Int.MAX_VALUE).size)
    }

    @Test fun `re-import is a no-op thanks to idempotent key`() = runBlocking {
        training.add(day, 1, 45, listOf(2), "")
        val out = ByteArrayOutputStream()
        backup.exportJson(out)
        val again = backup.apply(ByteArrayInputStream(out.toByteArray()), ConflictPolicy.SKIP)
        assertEquals(0, again.inserted)
        assertEquals(1, again.skipped)
    }

    @Test fun `import creates unknown exercise type automatically`() = runBlocking {
        val json = BackupCodec.toJson(
            BackupFile(
                BackupCodec.CURRENT_VERSION, "", null,
                listOf(ExerciseTypeDto("攀岩", 8.0)),
                listOf(RecordDto("2026-10-12", "攀岩", 8.0, 40, 373.0, listOf("背"), "")),
            ),
        )
        val r = backup.apply(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)), ConflictPolicy.SKIP)
        assertEquals(1, r.inserted)
        assertNotNull(db.typeDao().byName("攀岩"))
        assertEquals(8.0, db.typeDao().byName("攀岩")!!.met, 1e-9)
    }

    /** 规格 6.5 的硬要求：脏行不得让整批半途而废 —— 严格模式抛错且原有数据分毫不动 */
    @Test fun `failed import rolls back the whole transaction`() = runBlocking {
        training.add(day, 1, 45, listOf(2), "")
        val bad = BackupCodec.toJson(
            BackupFile(
                BackupCodec.CURRENT_VERSION, "", null, emptyList(),
                listOf(
                    RecordDto("2026-10-11", "划船机", 7.0, 30, 245.0, emptyList(), ""),
                    RecordDto("2026-13-45", "划船机", 7.0, 30, 245.0, emptyList(), ""),
                ),
            ),
        )
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                backup.applyStrict(ByteArrayInputStream(bad.toByteArray(Charsets.UTF_8)), ConflictPolicy.SKIP)
            }
        }
        assertEquals(1, db.trainingDao().rangeOnce(0, Int.MAX_VALUE).size)   // 合法那条也没进去
    }

    @Test fun `csv export uses bom and header`() = runBlocking {
        training.add(day, 1, 45, listOf(2), "")
        val out = ByteArrayOutputStream()
        backup.exportCsv(out)
        val text = out.toString("UTF-8")
        assertTrue(text.startsWith(CsvCodec.BOM))
        assertTrue(text.contains(CsvCodec.HEADER))
    }

    @Test fun `preview reports counts without writing`() = runBlocking {
        val out = ByteArrayOutputStream()
        training.add(day, 1, 45, listOf(2), "")
        backup.exportJson(out)
        db.trainingDao().deleteAll()
        val p = backup.preview(ByteArrayInputStream(out.toByteArray()), ConflictPolicy.SKIP)
        assertEquals(1, p.toInsert)
        assertEquals(0, db.trainingDao().rangeOnce(0, Int.MAX_VALUE).size)
    }
}
