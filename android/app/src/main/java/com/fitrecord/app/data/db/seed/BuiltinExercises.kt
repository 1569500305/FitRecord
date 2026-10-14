package com.fitrecord.app.data.db.seed

/**
 * 内置运动库（规格 4.5），MET 取 Compendium of Physical Activities 的中等强度值。
 * colorIndex 必须与 ui/theme/ExercisePalette 的下标一一对应，否则日历点与饼图会串色。
 * defaultBodyPartIds 用 BodyParts 的 id（逗号分隔），P5 选完类型后自动带出部位。
 */
data class BuiltinExercise(
    val name: String,
    val met: Double,
    val colorIndex: Int,
    val defaultBodyPartIds: String,
    /** 按组数和每组个数记录，而不是分钟 */
    val recordBySets: Boolean = false,
    /** 仍保留在类型库里，但选择器默认不列出 */
    val archived: Boolean = false,
)

object BuiltinExercises {

    val items: List<BuiltinExercise> = listOf(
        BuiltinExercise("划船机", 7.0, 0, "2,5"),
        BuiltinExercise("椭圆机", 5.0, 1, "7,8"),
        BuiltinExercise("动感单车", 6.8, 2, "7,8"),
        BuiltinExercise("跑步机", 8.3, 3, "8"),
        BuiltinExercise("自由力量", 5.0, 4, "1,2,3,4,5"),
        BuiltinExercise("瑜伽", 2.5, 5, "5", archived = true),
        BuiltinExercise("快走", 4.3, 6, "8"),
        BuiltinExercise("游泳", 5.8, 7, "8", archived = true),
        BuiltinExercise("深蹲", 5.0, 8, "6,7", recordBySets = true),
        BuiltinExercise("俯卧撑", 3.8, 9, "1,3,4", recordBySets = true),
        BuiltinExercise("哑铃", 5.0, 10, "1,2,3,4", recordBySets = true),
    )

    fun byName(name: String): BuiltinExercise = items.first { it.name == name }

    fun defaultMet(name: String): Double? = items.find { it.name == name }?.met
}
