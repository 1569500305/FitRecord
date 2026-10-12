package com.fitrecord.app.data.db

import androidx.room.migration.Migration

/**
 * 迁移登记表（规格 模块4-2）。V1 只有 version 1，因此数组为空。
 *
 * 升版流程：
 *  1. 改实体 → @Database(version = n+1)
 *  2. 在此追加 Migration(n → n+1)
 *  3. 跑 SchemaGuardTest + 新增的 MigrationTestHelper 测试
 *  4. schemas/ 下新增的 JSON 一起提交
 * 绝不使用 fallbackToDestructiveMigration —— 那等于用户升级即丢数据。
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}

/** meta 表的固定键，避免散落的字符串字面量 */
object MetaKeys {
    const val SCHEMA_VERSION = "schema_version"
    const val CREATED_AT = "schema_created_at"
    const val LAST_IMPORT = "last_import"
}
