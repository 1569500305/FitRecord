package com.fitrecord.app.domain

/**
 * 力量类与有氧类的展示、折算口径。
 * 组数记录不直接当成分钟填写，统计和消耗仍要一个时长，因此按固定的每组分钟数折算。
 */
object EffortFormat {

    /** 新建力量记录的默认组数 */
    const val DEFAULT_SET_COUNT = 5

    /** 新建力量记录的默认每组个数 */
    const val DEFAULT_REPS_PER_SET = 20

    const val MAX_SET_COUNT = 99
    const val MAX_REPS_PER_SET = 999

    /** 一组（含组间休息）折算的分钟数，只用于消耗和总时长 */
    const val MINUTES_PER_SET = 3

    /** 有组数时显示「5 组 × 20 个」，否则显示分钟 */
    fun label(setCount: Int, repsPerSet: Int, durationMin: Int): String =
        if (setCount > 0) "$setCount 组 × $repsPerSet 个" else "$durationMin 分钟"

    /** 组数折算成分钟，并限制在卡路里公式接受的范围内 */
    fun minutesForSets(setCount: Int): Int = (setCount * MINUTES_PER_SET).coerceIn(1, 1440)
}
