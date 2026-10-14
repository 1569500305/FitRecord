package com.fitrecord.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
        assertEquals(11, ExercisePalette.colors.size)
        assertEquals(11, ExercisePalette.colors.distinct().size)
    }

    @Test fun `palette index wraps for arbitrary type id`() {
        assertEquals(ExercisePalette.colors[0], ExercisePalette.colorForId(0))
        assertEquals(ExercisePalette.colors[0], ExercisePalette.colorForId(11))
        assertEquals(ExercisePalette.colors[3], ExercisePalette.colorForId(3))
        // 负数（导入的异常数据）也必须安全
        assertEquals(ExercisePalette.colors[6], ExercisePalette.colorForId(-5))
    }

    @Test fun `rgb long round trips through color`() {
        val c = Color(0xFF2E9E5B)
        assertEquals(c, c.toRgbLong().toColor())
    }

    /** 位级往返：包括半透明与全透明。防止有人把 toColor() 改成丢 alpha 的写法而测试仍绿 */
    @Test fun `rgb long round trip preserves the alpha bits`() {
        for (c in listOf(Color(0xFF2E9E5B), Color(0x80FF0000), Color(0x00FFFFFF), Color(0xFFFFFFFF))) {
            assertEquals(c.toArgb(), c.toRgbLong().toColor().toArgb())
        }
    }

    @Test fun `stored color long is opaque ARGB not a 24-bit rgb`() {
        // 存库值必须是带 alpha 的 32 位 ARGB，否则日历点会透明不可见
        assertEquals(0xFF2E9E5BL, Color(0xFF2E9E5B).toRgbLong())
    }
}
