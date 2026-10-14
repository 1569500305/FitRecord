package com.fitrecord.app.data.db

/**
 * 聚合查询的返回行类型。刻意不用 @Relation —— 那会让每条记录再发一次查询（N+1）。
 * 所有跨表信息靠一条 JOIN 一次取回（规格 4.6：统计在 SQL 层完成）。
 */

data class PerDayRow(val date: Int, val segments: Int, val minutes: Int)

data class PerTypeRow(
    val typeId: Long,
    val name: String,
    val color: Long,
    val minutes: Int,
    val segments: Int,
)

data class PerPartRow(val partId: Long, val name: String, val segments: Int)

/**
 * training_record JOIN exercise_type 的扁平结果。
 * 类型的全部字段都在这里（评审 I-1）：仓库层用直接构造 ExerciseTypeEntity，
 * 不得再逐行回查 typeDao.byId。新增字段带默认值，供测试构造器省略。
 */
data class RecordWithType(
    val id: Long,
    val date: Int,
    val exerciseTypeId: Long,
    val durationMin: Int,
    val calories: Double?,
    val note: String,
    val typeName: String,
    val typeColor: Long,
    val met: Double,
    val typeIsBuiltin: Boolean = true,
    val typeArchived: Boolean = false,
    val typeMetOverridden: Boolean = false,
    val typeSortOrder: Int = 0,
    val typeDefaultBodyPartIds: String = "",
    val setCount: Int = 0,
    val repsPerSet: Int = 0,
    val typeRecordBySets: Boolean = false,
)
