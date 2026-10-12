

package com.fitrecord.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 主题装配测试。每条测试都**捕获组合期实际取到的值**再断言，
 * 而不是"能渲染即通过"——否则把深色板换成纯红、或删掉 ThemeMode 分支，测试仍会绿。
 * 纯数值色板由 ThemeTokensTest 守护，这里守的是"主题把对的值送到了组合里"。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ThemeRenderTest {

    @get:Rule val rule = createComposeRule()

    @Test fun `dark mode sends the dark background value into the composition`() {
        var captured = Color.Unspecified
        rule.setContent {
            FitRecordTheme(themeMode = ThemeMode.DARK) {
                captured = MaterialTheme.colorScheme.background
                Text("probe")
            }
        }
        rule.waitForIdle()
        assertEquals(Color(0xFF101114), captured)
    }

    @Test fun `light mode sends the light background value into the composition`() {
        var captured = Color.Unspecified
        rule.setContent {
            FitRecordTheme(themeMode = ThemeMode.LIGHT) {
                captured = MaterialTheme.colorScheme.background
                Text("probe")
            }
        }
        rule.waitForIdle()
        assertEquals(Color(0xFFF5F6F7), captured)
    }

    @Test fun `unread color local resolves to the light unread grey in light mode`() {
        var captured = Color.Unspecified
        rule.setContent {
            FitRecordTheme(themeMode = ThemeMode.LIGHT) {
                captured = LocalUnreadColor.current
                Text("unread")
            }
        }
        rule.waitForIdle()
        assertEquals(Color(0xFFB6BABD), captured)
    }

    @Test fun `unread color local resolves to the dark unread grey in dark mode`() {
        var captured = Color.Unspecified
        rule.setContent {
            FitRecordTheme(themeMode = ThemeMode.DARK) {
                captured = LocalUnreadColor.current
                Text("unread")
            }
        }
        rule.waitForIdle()
        assertEquals(Color(0xFF5F6468), captured)
    }

    @Test fun `system mode resolves to exactly one of the two brand palettes`() {
        var captured = Color.Unspecified
        rule.setContent {
            FitRecordTheme(themeMode = ThemeMode.SYSTEM) {
                captured = MaterialTheme.colorScheme.primary
                Text("sys")
            }
        }
        rule.waitForIdle()
        // Robolectric 下的夜间模式不确定，但绝不能拿到第三个值
        assertEquals(true, captured == Color(0xFF2E9E5B) || captured == Color(0xFF4FBE7E))
    }

    @Test fun `theme does not swallow the layout direction provided by its parent`() {
        var observed: LayoutDirection? = null
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FitRecordTheme {
                    observed = LocalLayoutDirection.current
                    Text("dir")
                }
            }
        }
        rule.waitForIdle()
        // 主题不得改写方向；日历表头顺序由 Manifest 的 supportsRtl=false 决定，不在此处
        assertEquals(LayoutDirection.Rtl, observed)
    }

    @Test fun `heat levels clamp out-of-range input instead of throwing`() {
        assertEquals(5, HeatLevels.levels.size)
        assertEquals(HeatLevels.levels[0], HeatLevels.colorFor(-3))
        assertEquals(HeatLevels.levels[4], HeatLevels.colorFor(99))
        assertEquals(HeatLevels.levels[2], HeatLevels.colorFor(2))
    }
}
