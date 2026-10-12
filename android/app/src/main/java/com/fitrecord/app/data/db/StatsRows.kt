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

/** training_record JOIN exercise_type 的扁平结果 */
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
)
