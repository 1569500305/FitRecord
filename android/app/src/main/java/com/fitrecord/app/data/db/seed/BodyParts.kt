package com.fitrecord.app.data.db.seed

/**
 * 锻炼部位（规格模块1-2）。V1 固定 8 条、id 写死 1..8：
 * 雷达图 8 轴与 ExerciseTypeEntity.defaultBodyPartIds 都按这套 id 硬引用，
 * 因此不允许用户增删（表结构已支持，V1.1 再开放）。
 */
object BodyParts {

    const val CHEST = 1L
    const val BACK = 2L
    const val SHOULDER = 3L
    const val ARM = 4L
    const val CORE = 5L
    const val GLUTES = 6L
    const val LEG = 7L
    const val CARDIO = 8L

    val items: List<Pair<Long, String>> = listOf(
        CHEST to "胸",
        BACK to "背",
        SHOULDER to "肩",
        ARM to "手臂",
        CORE to "核心",
        GLUTES to "臀",
        LEG to "腿",
        CARDIO to "全身有氧",
    )

    /** 雷达图轴顺序（规格 2.5） */
    val axisIds: List<Long> = items.map { it.first }

    fun name(id: Long): String = items.first { it.first == id }.second

    fun parseIds(csv: String): List<Long> = csv.split(",").mapNotNull { it.trim().toLongOrNull() }
}
