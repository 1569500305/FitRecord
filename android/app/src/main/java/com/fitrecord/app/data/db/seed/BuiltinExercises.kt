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
)

object BuiltinExercises {

    val items: List<BuiltinExercise> = listOf(
        BuiltinExercise("划船机", 7.0, 0, "2,5"),
        BuiltinExercise("椭圆机", 5.0, 1, "7,8"),
        BuiltinExercise("动感单车", 6.8, 2, "7,8"),
        BuiltinExercise("跑步机", 8.3, 3, "8"),
        BuiltinExercise("自由力量", 5.0, 4, "1,2,3,4,5"),
        BuiltinExercise("瑜伽", 2.5, 5, "5"),
        BuiltinExercise("快走", 4.3, 6, "8"),
        BuiltinExercise("游泳", 5.8, 7, "8"),
    )

    fun byName(name: String): BuiltinExercise = items.first { it.name == name }

    fun defaultMet(name: String): Double? = items.find { it.name == name }?.met
}
