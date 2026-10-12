package com.fitrecord.app.domain

import com.fitrecord.app.data.db.seed.Foods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 食物等价换算（规格 4.4）—— 用户最常截图炫耀的一行文案，口径必须钉死 */
class FoodEquivalentTest {

    private val foods = Foods.items

    /**
     * 规格 4.4 的算法是“按 |ln(units)| 升序取最接近的 2 项”，
     * 即“换算结果最接近 1 个单位”。960 kcal 下：奶茶 2.7 / 米饭 4.1 / 鸡腿 5.3，
     * 所以前两项是奶茶与米饭 —— 规格里“🍚4.1碗 · 🍗5.3个”那句只是举例，不是算法结果。
     */
    @Test fun `960 kcal picks the two units closest to one whole unit`() {
        val r = FoodEquivalent.closest(960, foods)
        assertEquals(listOf("奶茶", "米饭"), r.map { it.food.name })
        assertEquals(2.7, r[0].units, 0.05)     // 960 / 350
        assertEquals(4.1, r[1].units, 0.05)     // 960 / 232
    }

    @Test fun `ranking is monotonic in log distance`() {
        val r = FoodEquivalent.closest(960, foods, limit = 4)
        val distances = r.map { kotlin.math.abs(kotlin.math.ln(it.units)) }
        assertEquals(distances.sorted(), distances)
    }

    @Test fun `ranking is by log distance from one unit`() {
        // 232 kcal 恰好一碗米饭 → 排第一且 units == 1
        val r = FoodEquivalent.closest(232, foods, limit = 3)
        assertEquals("米饭", r.first().food.name)
        assertEquals(1.0, r.first().units, 1e-9)
    }

    @Test fun `limit caps the result size`() {
        assertEquals(1, FoodEquivalent.closest(500, foods, limit = 1).size)
        assertEquals(2, FoodEquivalent.closest(500, foods, limit = 2).size)
    }

    @Test fun `zero kcal per unit foods are never offered`() {
        val withBroken = foods + foods.first().copy(id = 999, name = "空气", kcalPerUnit = 0.0)
        val r = FoodEquivalent.closest(500, withBroken, limit = 10)
        assertTrue(r.none { it.food.name == "空气" })
    }

    @Test fun `null and non positive kcal render as empty string`() {
        assertEquals("", FoodEquivalent.text(null, foods))
        assertEquals("", FoodEquivalent.text(0, foods))
        assertEquals("", FoodEquivalent.text(-10, foods))
    }

    @Test fun `tiny burn renders as 少量 instead of 0_0`() {
        val text = FoodEquivalent.text(5, foods)
        assertTrue("实际文案：$text", text.contains("少量"))
        assertFalse(text.contains("0.0"))
    }

    @Test fun `text has the approx prefix and middle dot separator`() {
        val text = FoodEquivalent.text(960, foods)
        assertTrue(text.startsWith("≈ "))
        assertTrue(text.contains(" · "))
        assertTrue("实际文案：$text", text.contains("🍚"))
    }

    @Test fun `units are rendered with one decimal in chinese locale`() {
        val text = FoodEquivalent.text(232 * 3, foods)     // 恰好 3 碗
        assertTrue("实际文案：$text", text.contains("3.0碗米饭"))
    }
}
