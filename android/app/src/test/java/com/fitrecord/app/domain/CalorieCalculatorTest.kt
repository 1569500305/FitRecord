package com.fitrecord.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 卡路里口径（规格 4.3）：MET × 体重 × 小时，四舍五入到整数 */
class CalorieCalculatorTest {

    @Test fun `rowing 45min at 70kg is 368 kcal`() {
        // 7.0 × 70 × 0.75 = 367.5 → 368（必须四舍五入，不是截断）
        assertEquals(368.0, CalorieCalculator.kcal(met = 7.0, weightKg = 70.0, durationMin = 45)!!, 1e-9)
    }

    @Test fun `treadmill 30min at 70kg is 291 kcal`() {
        // 8.3 × 70 × 0.5 = 290.5 → 291
        assertEquals(291.0, CalorieCalculator.kcal(8.3, 70.0, 30)!!, 1e-9)
    }

    @Test fun `one hour equals met times weight`() {
        assertEquals(350.0, CalorieCalculator.kcal(5.0, 70.0, 60)!!, 1e-9)
    }

    @Test fun `weight scales linearly`() {
        val light = CalorieCalculator.kcal(7.0, 50.0, 60)!!
        val heavy = CalorieCalculator.kcal(7.0, 100.0, 60)!!
        assertEquals(2.0, heavy / light, 0.02)
    }

    @Test fun `missing weight yields null not zero`() {
        assertNull(CalorieCalculator.kcal(7.0, null, 60))
    }

    @Test fun `zero duration is zero`() {
        assertEquals(0.0, CalorieCalculator.kcal(7.0, 70.0, 0)!!, 1e-9)
    }

    @Test fun `rounding is half up not banker`() {
        // 2.5 × 70 × 0.5/60*60 → 精确 0.5 的用例：MET 1.0 × 70kg × 30min = 35 整
        assertEquals(35.0, CalorieCalculator.kcal(1.0, 70.0, 30)!!, 1e-9)
        // 7.0 × 70 × 1min/60 = 8.1666 → 8
        assertEquals(8.0, CalorieCalculator.kcal(7.0, 70.0, 1)!!, 1e-9)
    }

    /** 真正的 half-way 边界：290.5 → 291 已覆盖；这里钉住"不得变成 290" */
    @Test fun `half way values round up not down`() {
        assertEquals(291.0, CalorieCalculator.kcal(8.3, 70.0, 30)!!, 1e-9)
        assertEquals(368.0, CalorieCalculator.kcal(7.0, 70.0, 45)!!, 1e-9)
    }

    /** 评审 I-7：P5 公式回显不得把 MET 10.0 印成 "1" */
    @Test fun `explain renders met and weight with one decimal and never drops a digit`() {
        assertEquals("MET 7.0 × 70.0kg × 0.75h", CalorieCalculator.explain(7.0, 70.0, 45))
        assertEquals("MET 10.0 × 70.0kg × 1.00h", CalorieCalculator.explain(10.0, 70.0, 60))
        assertEquals("MET 2.5 × 55.5kg × 0.50h", CalorieCalculator.explain(2.5, 55.5, 30))
        // 参考表里 MET 最大的项不得被去尾零逻辑刷小
        assertTrue(CalorieCalculator.explain(15.0, 70.0, 60).contains("MET 15.0"))
        assertTrue(CalorieCalculator.explain(20.0, 70.0, 60).contains("MET 20.0"))
    }
}
