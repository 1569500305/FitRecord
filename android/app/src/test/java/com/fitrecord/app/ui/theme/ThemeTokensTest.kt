package com.fitrecord.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设计 token 守护测试（规格 2.1 / 2.3）。
 * 这些断言的意义：任何人改色板或轮询逻辑时必须显式意识到规格在何处被约束。
 */
class ThemeTokensTest {

    @Test fun `primary is brand green in light scheme`() {
        assertEquals(Color(0xFF2E9E5B), AppColors.light.primary)
    }

    @Test fun `secondary is brand orange`() {
        assertEquals(Color(0xFFF5A623), AppColors.light.secondary)
    }

    @Test fun `dark scheme uses the lighter paired brand values`() {
        assertEquals(Color(0xFF4FBE7E), AppColors.dark.primary)
        assertEquals(Color(0xFFF7BE55), AppColors.dark.secondary)
    }

    @Test fun `exercise palette has 8 distinct colors`() {
        assertEquals(8, ExercisePalette.colors.size)
        assertEquals(8, ExercisePalette.colors.distinct().size)
    }

    @Test fun `palette index wraps for arbitrary type id`() {
        assertEquals(ExercisePalette.colors[0], ExercisePalette.colorForId(0))
        assertEquals(ExercisePalette.colors[0], ExercisePalette.colorForId(8))
        assertEquals(ExercisePalette.colors[3], ExercisePalette.colorForId(3))
        // 负数（导入的异常数据）也必须安全
        assertEquals(ExercisePalette.colors[3], ExercisePalette.colorForId(-5))
    }

    @Test fun `rgb long round trips through color`() {
        val c = Color(0xFF2E9E5B)
        assertEquals(c, c.toRgbLong().toColor())
    }
}
