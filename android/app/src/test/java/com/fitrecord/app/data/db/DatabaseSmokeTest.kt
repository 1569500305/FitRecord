package com.fitrecord.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.entity.TrainingRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 建库与最核心查询链路的冒烟测试（Task 5）。
 * 守的是三件事：表齐全、外键/索引建起来了、JOIN 查询能拿到带类型名的记录。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseSmokeTest {

    private fun db() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), FitRecordDatabase::class.java
    ).build()

    /** Room/Android 自动建的内部表，不属于 spec 的 8 张业务表：
     *  android_metadata（Android SQLite 惯例）、room_master_table（schema 身份校验） */
    private val systemTables = setOf("android_metadata", "room_master_table")

    private fun tableNames(db: FitRecordDatabase): List<String> =
        db.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            .filter { it !in systemTables }

    @Test fun `all eight spec tables are created`() = runBlocking {
        val db = db()
        val expected = listOf(
            "exercise_type", "body_part", "training_record", "record_body_part",
            "daily_note", "user_profile", "food_item", "meta",
        )
        val actual = tableNames(db)
        assertTrue("缺表：${expected - actual.toSet()}", actual.containsAll(expected))
        // 同时守护"多建表"：只能比 spec 多也不能多（评审 M-3）
        assertEquals("实际表：$actual", expected.size, actual.size)
        db.close()
    }

    @Test fun `training_record has the indices the calendar and stats queries rely on`() = runBlocking {
        val db = db()
        val indexes = db.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='training_record'")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertTrue("索引缺失：$indexes", indexes.any { it.contains("date") })
        assertTrue("索引缺失：$indexes", indexes.any { it.contains("exerciseTypeId") })
        db.close()
    }

    @Test fun `inserting a record then observing its range returns it joined with the type`() = runBlocking {
        val db = db()
        val typeId = db.typeDao().insertType(
            name = "划船机", met = 7.0, color = 0xFF2E9E5BL, isBuiltin = true, sortOrder = 0
        )
        db.trainingDao().insert(
            TrainingRecordEntity(
                date = 20000, exerciseTypeId = typeId, durationMin = 45,
                calories = 320.0, note = "x", createdAt = 1L, updatedAt = 1L,
            )
        )

        val rows = db.trainingDao().observeRange(19990, 20010).first()
        assertEquals(1, rows.size)
        assertEquals(45, rows.first().durationMin)
        // JOIN 必须把类型名/颜色/met 一起带出来，否则日历与图表要 N+1 查询
        assertEquals("划船机", rows.first().typeName)
        assertEquals(0xFF2E9E5BL, rows.first().typeColor)
        assertEquals(7.0, rows.first().met, 1e-9)
        db.close()
    }

    @Test fun `observeRange excludes days outside the range`() = runBlocking {
        val db = db()
        val typeId = db.typeDao().insertType("椭圆机", 5.0, 0xFF3E8FD6L, true, 1)
        db.trainingDao().insert(
            TrainingRecordEntity(date = 20000, exerciseTypeId = typeId, durationMin = 30,
                calories = null, note = "", createdAt = 1L, updatedAt = 1L)
        )
        db.trainingDao().insert(
            TrainingRecordEntity(date = 20050, exerciseTypeId = typeId, durationMin = 30,
                calories = null, note = "", createdAt = 1L, updatedAt = 1L)
        )
        assertEquals(1, db.trainingDao().observeRange(19990, 20010).first().size)
        assertEquals(2, db.trainingDao().observeRange(19990, 20100).first().size)
        db.close()
    }

    @Test fun `null calories survives the round trip`() = runBlocking {
        val db = db()
        val typeId = db.typeDao().insertType("瑜伽", 2.5, 0xFF20B2AAL, true, 2)
        db.trainingDao().insert(
            TrainingRecordEntity(date = 20000, exerciseTypeId = typeId, durationMin = 60,
                calories = null, note = "无体重", createdAt = 1L, updatedAt = 1L)
        )
        val row = db.trainingDao().observeRange(20000, 20000).first().single()
        assertEquals(null, row.calories)
        db.close()
    }

    @Test fun `deleting a record cascades its body part links`() = runBlocking {
        val db = db()
        val typeId = db.typeDao().insertType("自由力量", 5.0, 0xFF8E6BE8L, true, 3)
        db.bodyPartDao().insert(com.fitrecord.app.data.db.entity.BodyPartEntity(2L, "背", 2))
        val recordId = db.trainingDao().insert(
            TrainingRecordEntity(date = 20000, exerciseTypeId = typeId, durationMin = 40,
                calories = 200.0, note = "", createdAt = 1L, updatedAt = 1L)
        )
        db.trainingDao().linkBodyParts(
            listOf(com.fitrecord.app.data.db.entity.RecordBodyPartEntity(recordId, 2L))
        )
        assertEquals(listOf(2L), db.trainingDao().linksOf(recordId).map { it.bodyPartId })

        db.trainingDao().deleteById(recordId)
        val leftovers = db.openHelper.writableDatabase
            .query("SELECT COUNT(*) FROM record_body_part").use { c -> c.moveToFirst(); c.getInt(0) }
        assertEquals("外键 CASCADE 未生效", 0, leftovers)
        db.close()
    }

    @Test fun `builtin type cannot be deleted while records reference it`() = runBlocking {
        val db = db()
        val typeId = db.typeDao().insertType("跑步机", 8.3, 0xFFF5A623L, true, 4)
        db.trainingDao().insert(
            TrainingRecordEntity(date = 20000, exerciseTypeId = typeId, durationMin = 30,
                calories = 291.0, note = "", createdAt = 1L, updatedAt = 1L)
        )
        // RESTRICT：有引用时删除类型必须抛异常，而不是静默删掉历史（评审 M-3：拆掉弱化或断言）
        val threw = runCatching {
            db.openHelper.writableDatabase.delete("exercise_type", "id = ?", arrayOf(typeId))
        }.isFailure
        assertTrue("删除被引用的类型必须因 RESTRICT 而失败", threw)
        assertNotNull("类型必须仍在", db.typeDao().byId(typeId))
        assertEquals(1, db.trainingDao().rangeOnce(0, Int.MAX_VALUE).size)
        db.close()
    }

    @Test fun `meta upsert overwrites the same key`() = runBlocking {
        val db = db()
        db.metaDao().put(com.fitrecord.app.data.db.entity.MetaEntity("probe", "v1"))
        assertEquals("v1", db.metaDao().get("probe"))
        db.metaDao().put(com.fitrecord.app.data.db.entity.MetaEntity("probe", "v2"))
        assertEquals("v2", db.metaDao().get("probe"))
        db.close()
    }

    @Test fun `duplicate exercise type name is rejected by the unique index`() = runBlocking {
        val db = db()
        db.typeDao().insertType("攀岩", 8.0, 0xFF123456L, isBuiltin = false, sortOrder = 9)
        val duplicate = runCatching {
            db.typeDao().insertType("攀岩", 9.0, 0xFF654321L, isBuiltin = false, sortOrder = 10)
        }
        assertTrue("唯一索引未生效：重名类型被写进去了", duplicate.isFailure)
        assertEquals(1, db.typeDao().all().count { it.name == "攀岩" })
        db.close()
    }
}
