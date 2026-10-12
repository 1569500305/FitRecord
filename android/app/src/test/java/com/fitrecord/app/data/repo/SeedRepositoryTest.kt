package com.fitrecord.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import com.fitrecord.app.data.db.seed.BodyParts
import com.fitrecord.app.data.db.seed.BuiltinExercises
import com.fitrecord.app.data.db.seed.Foods
import com.fitrecord.app.ui.theme.ExercisePalette
import com.fitrecord.app.ui.theme.toRgbLong
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 种子写入的三条硬保证（评审 I-5 / I-6）：
 * 幂等、部分失败可自愈、不覆盖用户改过的 MET。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SeedRepositoryTest {

    private lateinit var db: FitRecordDatabase
    private lateinit var seed: SeedRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FitRecordDatabase::class.java
        ).build()
        seed = SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao())
    }

    @Test fun `ensureSeeded is idempotent across repeated calls`() = runBlocking {
        seed.ensureSeeded()
        seed.ensureSeeded()
        seed.ensureSeeded()
        assertEquals(BuiltinExercises.items.size, db.typeDao().all().size)
        assertEquals(BodyParts.items.size, db.bodyPartDao().all().size)
        assertEquals(Foods.items.size, db.foodDao().all().size)
    }

    @Test fun `seeding resumes and completes after a partial write`() = runBlocking {
        // 模拟上次启动写到第 3 条就被杀进程
        BuiltinExercises.items.take(3).forEachIndexed { index, item ->
            db.typeDao().insert(
                ExerciseTypeEntity(
                    name = item.name, met = item.met,
                    color = ExercisePalette.colors[item.colorIndex].toRgbLong(),
                    isBuiltin = true, sortOrder = index,
                    defaultBodyPartIds = item.defaultBodyPartIds,
                )
            )
        }
        assertEquals(3, db.typeDao().all().size)

        seed.ensureSeeded()

        assertEquals("缺掉的内置类型必须补齐", BuiltinExercises.items.size, db.typeDao().all().size)
        assertEquals(8, db.bodyPartDao().all().size)
        assertEquals(10, db.foodDao().all().size)
    }

    @Test fun `user edited met survives a later seed run`() = runBlocking {
        seed.ensureSeeded()
        val rowing = db.typeDao().byName("划船机")!!
        db.typeDao().update(rowing.copy(met = 6.0, metOverridden = true))

        seed.ensureSeeded()

        val after = db.typeDao().byName("划船机")!!
        assertEquals(6.0, after.met, 1e-9)
        assertEquals(true, after.metOverridden)
        assertEquals(BuiltinExercises.items.size, db.typeDao().all().size)
    }

    @Test fun `archived custom type is not resurrected as builtin`() = runBlocking {
        seed.ensureSeeded()
        val custom = db.typeDao().insertType("攀岩", 8.0, 0xFF123456L, isBuiltin = false, sortOrder = 99)
        db.typeDao().archive(custom)

        seed.ensureSeeded()

        val after = db.typeDao().byId(custom)!!
        assertEquals("种子不得改用户的归档决定", true, after.archived)
        assertEquals(false, after.isBuiltin)
    }

    @Test fun `builtin colors come from the palette at the declared index`() = runBlocking {
        seed.ensureSeeded()
        BuiltinExercises.items.forEach { item ->
            val stored = db.typeDao().byName(item.name)!!
            val expected = ExercisePalette.colors[item.colorIndex].toRgbLong()
            assertEquals("${item.name} 的颜色必须与规格 2.1 的下标对齐", expected, stored.color)
        }
    }

    @Test fun `every builtin gets a distinct sortOrder matching seed order`() = runBlocking {
        seed.ensureSeeded()
        val types = db.typeDao().all()
        assertEquals(BuiltinExercises.items.map { it.name }, types.map { it.name })
        assertEquals(types.indices.toList(), types.map { it.sortOrder })
        assertNotNull(types.first().defaultBodyPartIds)
    }
}
