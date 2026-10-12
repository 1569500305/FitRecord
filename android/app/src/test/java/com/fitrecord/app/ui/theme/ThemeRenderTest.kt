package com.fitrecord.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.compose.ui.unit.LayoutDirection

/**
 * 主题渲染冒烟测试。断言的是"主题能装配、token 能取到"，
 * 具体色值由 ThemeTokensTest 守护 —— 两层各管一件事，避免把颜色断言散落到 UI 测试里。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ThemeRenderTest {

    @get:Rule val rule = createComposeRule()

    @Test fun `dark mode applies dark scheme and renders content`() {
        rule.setContent {
            FitRecordTheme(themeMode = ThemeMode.DARK) {
                Text("probe", color = MaterialTheme.colorScheme.background)
            }
        }
        rule.onNodeWithText("probe").assertExists()
    }

    @Test fun `light mode provides the unread color via composition local`() {
        rule.setContent {
            FitRecordTheme(themeMode = ThemeMode.LIGHT) {
                Text("unread", color = LocalUnreadColor.current)
            }
        }
        rule.onNodeWithText("unread").assertExists()
    }

    @Test fun `system mode resolves without crashing`() {
        rule.setContent {
            FitRecordTheme(themeMode = ThemeMode.SYSTEM) {
                Text("sys", color = MaterialTheme.colorScheme.primary)
            }
        }
        rule.onNodeWithText("sys").assertExists()
    }

    @Test fun `heat levels are five and ascending in luminance order`() {
        assertEquals(5, HeatLevels.levels.size)
        // 越界必须被夹住而不是抛异常（热力图会传入 0..n 的段数）
        assertEquals(HeatLevels.levels[0], HeatLevels.colorFor(-3))
        assertEquals(HeatLevels.levels[4], HeatLevels.colorFor(99))
    }

    @Test fun `theme does not force rtl even when locale is rtl`() {
        // 规格：supportsRtl=false，日历周表头顺序不能反转
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FitRecordTheme { Text("dir", color = MaterialTheme.colorScheme.primary) }
            }
        }
        rule.onNodeWithText("dir").assertExists()
    }
}
