package com.fitrecord.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.entity.MetaEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * schema 守护（规格模块4-2）。三条守护都是"通过型"回归测试：
 * 它们不测业务逻辑，测的是"升级安全"这件事本身还成立。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SchemaGuardTest {

    /**
     * 守护 1：导出的 schema JSON 必须存在且内容完整。
     * Room 把目录名写成**带点的 FQCN**（不是嵌套目录），所以路径是
     * `com.fitrecord.app.data.db.FitRecordDatabase/1.json`；
     * 而 `schemas/` 前缀不写，因为 app/schemas 整体被配成 test resources 根（台账 P15）。
     */
    @Test fun `exported schema json is on the test classpath and describes 8 entities`() {
        val path = "com.fitrecord.app.data.db.FitRecordDatabase/1.json"
        val json = javaClass.classLoader!!.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
        assertNotNull(
            "$path 缺失：检查 exportSchema=true、ksp{arg(room.schemaLocation)} 与 sourceSets.test.resources.srcDirs",
            json,
        )
        assertTrue("schema JSON 应含 identityHash（Room 用它校验实体与 schema 一致）", json!!.contains("identityHash"))
        // Room 导出的是美化后的 JSON，"version": 1 冒号后有空格，不能直接 takeWhile(isDigit)
        val versionInFile = Regex(""""version"\s*:\s*(\d+)""").find(json)!!.groupValues[1].toInt()
        assertEquals("导出的 schema 版本必须与 @Database(version=…) 一致", 1, versionInFile)
        for (table in listOf(
            "exercise_type", "body_part", "training_record", "record_body_part",
            "daily_note", "user_profile", "food_item", "meta",
        )) {
            // Room 导出的是美化 JSON："tableName": "x" 冒号后有空格，用正则容忍空白
            assertTrue(
                "导出的 schema 缺表 $table",
                Regex(""""tableName"\s*:\s*"$table"""").containsMatchIn(json),
            )
        }
    }

    /**
     * 守护 2：实体定义与导出的 schema 必须一致。
     * Room 在首次查询时做 identityHash 校验，不一致会直接抛 —— 能查完就是过了。
     *
     * 注：曾有一条"文件库 close 后重开验数据仍在"的守护，已删除：
     * Robolectric 下每个测试类都会创建 FitRecordApp，而它的 onCreate 会在后台
     * 打开同一个文件库做种子，测试再开一次会撞 SQLITE_BUSY（BEGIN IMMEDIATE 失败）。
     * 真正的"无破坏式回退"行为守护需要一个 test-only 的 v2 数据库去触发迁移失败，
     * 应在首个真实 Migration 落地时一并补上（台账已记）。
     */
    @Test fun `database opens and queries without a schema validation error`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FitRecordDatabase::class.java
        ).build()
        assertEquals(0, db.statsDao().segments(0, Int.MAX_VALUE))
        assertEquals(0, db.statsDao().lifetimeMinutes())
        db.close()
    }

    /** 守护 3：迁移登记表在 V1 必须是空的；数据库实际版本必须是 1。
     * RoomDatabase 没有公开的 version 属性，而 Room 把 schema 版本写在 SQLite 的
     * PRAGMA user_version 里 —— 读它而不是读一个会漂移的常量。 */
    @Test fun `migration registry is empty at v1`() = runBlocking {
        assertEquals(0, Migrations.ALL.size)
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FitRecordDatabase::class.java
        ).build()
        val userVersion = db.openHelper.readableDatabase
            .query("PRAGMA user_version").use { c -> c.moveToFirst(); c.getInt(0) }
        assertEquals("@Database(version=…) 变了就必须先写 Migration 并补迁移测试", 1, userVersion)
        db.close()
    }
}
