package com.fitrecord.app.domain

import kotlin.math.roundToInt

/** 卡路里口径（规格 4.3）：kcal = MET × 体重kg × 时长小时 */
object CalorieCalculator {

    /**
     * 体重缺失时返回 **null**（不是 0）——UI 据此显示"待完善体重"引导条（规格 6.1），
     * 统计汇总按 null 跳过并注明未计算条数。
     */
    fun kcal(met: Double, weightKg: Double?, durationMin: Int): Double? =
        weightKg?.let { (met * it * durationMin / 60.0).roundToInt().toDouble() }

    /** 供 P5 实时预览：把公式的三个输入一起回显，让数字不显得像魔法 */
    fun explain(met: Double, weightKg: Double, durationMin: Int): String {
        val hours = durationMin / 60.0
        return "MET ${met.toString().trimEnd('0', '.').ifEmpty { "0" }} × ${weightKg}kg × ${"%.2f".format(hours)}h"
    }
}
