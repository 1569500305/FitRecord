package com.fitrecord.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.entity.MetaEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * schema 守护（规格模块4-2 / DoD 第 7 条）。
 *
 * application = Application::class 是刻意的：默认的 FitRecordApp 会在 onCreate 里
 * 往后台作用域丢一个 seedNow()，它打开的是**同一个文件库**，与本类的重开测试抢锁
 * （实测 SQLITE_BUSY / BEGIN IMMEDIATE 失败）。本类不需要容器，绕开它。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class SchemaGuardTest {

    private fun ctx() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /**
     * 守护 1：导出的 schema JSON 必须存在、版本为 1、且**恰好**描述 8 张表。
     * Room 把目录名写成带点的 FQCN（不是嵌套目录），且 app/schemas 整体是 test resources 根，
     * 所以路径不带 "schemas/" 前缀（台账 P15 修正）。
     * JSON 是美化格式，"tableName": "x" 冒号后有空格 —— 必须用容忍空白的正则而不是字面 contains。
     */
    @Test fun `exported schema json is on the classpath and describes exactly 8 tables`() {
        val path = "com.fitrecord.app.data.db.FitRecordDatabase/1.json"
        val stream = javaClass.classLoader!!.getResourceAsStream(path)
        assertNotNull(
            "$path 缺失：检查 exportSchema=true、ksp{arg(room.schemaLocation)} 与 sourceSets.test.resources.srcDirs",
            stream,
        )
        val text = stream!!.bufferedReader().use { it.readText() }
        assertTrue("schema JSON 应含 identityHash（Room 用它校验实体与建表 SQL 一致）", text.contains("identityHash"))

        val versionInFile = Regex("\"version\"\\s*:\\s*(\\d+)").find(text)!!.groupValues[1].toInt()
        assertEquals("导出的 schema 版本必须与 @Database(version=…) 一致", 1, versionInFile)

        for (table in listOf(
            "exercise_type", "body_part", "training_record", "record_body_part",
            "daily_note", "user_profile", "food_item", "meta",
        )) {
            assertTrue(
                "导出的 schema 缺表 $table",
                Regex("\"tableName\"\\s*:\\s*\"$table\"").containsMatchIn(text),
            )
        }

        // 不多不少：每个实体在导出 JSON 里恰好一个 "tableName" 键。
        // 逐表比对只能发现"缺"，这条发现"多"——即有人加了实体却忘了升 version、忘了补迁移。
        val declaredTables = Regex("\"tableName\"").findAll(text).count()
        assertEquals("导出的 schema 表数与实体数不一致", 8, declaredTables)
    }

    /**
     * 守护 2（评审 I-4 恢复）：走**同一条生产 builder 链路**建库、关闭、重开，
     * 数据必须还在，且 meta 里的 schema_created_at 不能被刷新 ——
     * 刷新意味着 onCreate 重跑，也就是库被破坏式重建过。这是"升级不丢数据"的行为级证据。
     */
    @Test fun `reopening a file database keeps rows and does not rerun onCreate`() = runBlocking<Unit> {
        val context = ctx()
        val name = "guard_reopen.db"
        context.deleteDatabase(name)

        val first = FitRecordDatabase.build(context, name)
        first.metaDao().put(MetaEntity("probe", "kept"))
        val createdStamp = first.metaDao().get(MetaKeys.CREATED_AT)
        assertNotNull("onCreate 应写入 schema_created_at", createdStamp)
        first.close()

        val second = FitRecordDatabase.build(context, name)
        assertEquals("重开数据库后数据丢失", "kept", second.metaDao().get("probe"))
        assertEquals(
            "schema_created_at 变了，说明库被重建（破坏式回退）而不是原地升级",
            createdStamp, second.metaDao().get(MetaKeys.CREATED_AT),
        )
        second.close()
        context.deleteDatabase(name)
    }

    /**
     * 守护 3：实体定义与生成代码不一致时 Room 会在打开时抛 —— 能建库并查完就是过了。
     * 注：这条兜不住"改了实体却忘了提交 schemas/1.json"（那由守护 1 的表数比对兜）。
     */
    @Test fun `database opens and queries without a validation error`() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(ctx(), FitRecordDatabase::class.java).build()
        assertEquals(0, db.statsDao().segments(0, Int.MAX_VALUE))
        assertEquals(0, db.statsDao().lifetimeMinutes())
        assertNull(db.metaDao().get("nothing"))
        db.close()
    }

    /**
     * 守护 4：V1 迁移登记表必须为空，且文件库里的实际版本必须是 1。
     * RoomDatabase 没有公开的 version 属性，Room 把 schema 版本写在 PRAGMA user_version，
     * 读它而不是读一个会漂移的 Kotlin 常量（后者会与 @Database(version=) 各自跑）。
     */
    @Test fun `migration registry is empty and file database carries schema version 1`() = runBlocking<Unit> {
        assertEquals("V1 不该有任何迁移；升版时必须在这里登记 Migration", 0, Migrations.ALL.size)
        val context = ctx()
        val name = "guard_version.db"
        context.deleteDatabase(name)
        val db = FitRecordDatabase.build(context, name)
        val userVersion = db.openHelper.readableDatabase
            .query("PRAGMA user_version").use { c -> c.moveToFirst(); c.getInt(0) }
        assertEquals("@Database(version=…) 变了就必须先写 Migration 并补迁移测试", 1, userVersion)
        db.close()
        context.deleteDatabase(name)
    }

    /**
     * 守护 5（复审 I-4 的实质加固）：库里的版本与代码不匹配时必须**报错**而不是静默重建。
     *
     * 这才是真正兜住 `fallbackToDestructiveMigration()` 的一条：守护 2 两侧版本都是 1，
     * Room 本来就不会进重建分支，加了那个 flag 也不会变红；而本守护把 user_version 拨到
     * 一个未登记的版本，模"升级到没有迁移路径的版本"：现在会抛；
     * 谁加了破坏式回退，这里就会静默返回 null 而变红。
     */
    @Test fun `version mismatch fails loudly instead of silently wiping data`() = runBlocking<Unit> {
        val context = ctx()
        val name = "guard_fail.db"
        context.deleteDatabase(name)

        val db = FitRecordDatabase.build(context, name)
        db.metaDao().put(MetaEntity("probe", "kept"))
        // SupportSQLiteDatabase 没有 setVersion，Room 的版本就存在 SQLite 的 PRAGMA user_version 里
        db.openHelper.writableDatabase.execSQL("PRAGMA user_version = 99")
        db.close()

        val reopened = FitRecordDatabase.build(context, name)
        val result = runCatching { reopened.metaDao().get("probe") }
        assertTrue(
            "版本不匹配必须报错而不是静默重建洗掉用户数据（禁止 fallbackToDestructiveMigration）",
            result.isFailure,
        )
        reopened.close()
        context.deleteDatabase(name)
    }
}
