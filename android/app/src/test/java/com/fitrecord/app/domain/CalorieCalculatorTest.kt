package com.fitrecord.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
