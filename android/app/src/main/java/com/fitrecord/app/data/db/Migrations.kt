package com.fitrecord.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 迁移登记表（规格 模块4-2）。
 *
 * 升版流程：
 *  1. 改实体 → @Database(version = n+1)
 *  2. 在此追加 Migration(n → n+1)
 *  3. 跑 SchemaGuardTest + 新增的 MigrationTestHelper 测试
 *  4. schemas/ 下新增的 JSON 一起提交
 * 绝不使用 fallbackToDestructiveMigration —— 那等于用户升级即丢数据。
 */
object Migrations {
    /**
     * v1 → v2：力量类增加组数/次数，并默认收起瑜伽、游泳。
     * 已有时长记录的 setCount 保持 0，界面仍显示分钟。
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE exercise_type ADD COLUMN recordBySets INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE training_record ADD COLUMN setCount INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE training_record ADD COLUMN repsPerSet INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "UPDATE exercise_type SET archived = 1 WHERE name IN ('瑜伽', '游泳') AND isBuiltin = 1",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}

/** meta 表的固定键，避免散落的字符串字面量 */
object MetaKeys {
    const val SCHEMA_VERSION = "schema_version"
    const val CREATED_AT = "schema_created_at"
    const val LAST_IMPORT = "last_import"
}
