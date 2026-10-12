package com.fitrecord.app.domain

import com.fitrecord.app.data.db.entity.FoodItemEntity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

/**
 * 一种食物及其对应的单位数。
 * 叫 FoodShare 而不叫 FoodEquivalent：后者已被下面的算法 object 占用，
 * 同文件内 class 与 object 同名会被 Kotlin 判为重复声明。
 */
data class FoodShare(val food: FoodItemEntity, val units: Double)

/**
 * 食物等价换算（规格 4.4）。
 * "最接近"的定义是 |ln(units)| 最小 —— 即换算结果最接近"1 个单位"，
 * 这样显示出来的数字永远在个位数量级，读起来最直观。
 */
object FoodEquivalent {

    private const val TRACE_THRESHOLD = 0.1

    fun closest(kcal: Int?, foods: List<FoodItemEntity>, limit: Int = 2): List<FoodShare> {
        if (kcal == null || kcal <= 0) return emptyList()
        return foods.asSequence()
            .filter { it.kcalPerUnit > 0 }
            .map { FoodShare(it, kcal / it.kcalPerUnit) }
            .sortedBy { abs(ln(it.units)) }
            .take(limit)
            .toList()
    }

    fun render(list: List<FoodShare>): String = list.joinToString(" · ") { eq ->
        if (eq.units < TRACE_THRESHOLD) {
            // 不足 0.1 个单位：只给表情，避免"0.0碗米饭"这种误导文案
            "${eq.food.emoji}少量"
        } else {
            val n = String.format(Locale.CHINA, "%.1f", eq.units)
            "${eq.food.emoji}$n${eq.food.unitLabel}"
        }
    }

    /** 一行完整文案，无数据时返回空串（UI 据此隐藏整行） */
    fun text(kcal: Int?, foods: List<FoodItemEntity>): String {
        val list = closest(kcal, foods)
        return if (list.isEmpty()) "" else "≈ " + render(list)
    }
}
