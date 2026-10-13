package com.fitrecord.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.AppContainer
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.MainActivity
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.db.entity.UserProfileEntity
import com.fitrecord.app.data.repo.SeedRepository
import com.fitrecord.app.data.repo.TrainingRepository
import com.fitrecord.app.ui.theme.FitRecordTheme
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 启动冒烟测试：真机报"装完就闪退"而设备未连接拿不到 logcat，
 * 于是在 JVM 上真实创建并 resume MainActivity —— 任何 onCreate / 组合期崩溃都会在这里抛出。
 *
 * 这条测试就是抓出 "No initializer set for given class CalendarViewModel" 的那一条。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppStartupTest {

    @Test fun `main activity can be created and resumed without crashing`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            var resumed = false
            scenario.onActivity { activity ->
                resumed = !activity.isFinishing &&
                    activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
            assertTrue("Activity 未能进入 RESUMED（启动路径上有崩溃或提前结束）", resumed)
        }
    }
}

/**
 * 逐屏组合测试：只测起始页不够，每个 Tab 都要真的组合出来，
 * 否则"编译通过但运行时崩"的界面（尤其 Canvas 自绘图表）会漏网。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScreenRenderTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before fun seed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FitRecordDatabase::class.java).build()
        SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao()).ensureSeeded()
        db.profileDao().upsert(UserProfileEntity(weightKg = 70.0, updatedAt = 0))
        val repo = TrainingRepository(db.trainingDao(), db.typeDao(), db.profileDao(), db.noteDao(), db.foodDao())
        // 造两条真实记录，让统计页走"有数据"分支而不是只测空态
        repo.add(LocalDate.of(2026, 10, 12), 1, 45, listOf(2, 5), "背有点紧")
        repo.add(LocalDate.of(2026, 10, 13), 4, 30, listOf(8), "")
        container = AppContainer(context, overrideDb = db)
    }

    private fun content(screen: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                FitRecordTheme { screen() }
            }
        }
    }

    /**
     * LazyColumn 只组合视口内的项，直接 onNodeWithText 找屏幕外的卡片会“找不到节点”。
     * 先滚到那里再断言。onAllNodes().onFirst() 是因为页面上可能有多个滚动容器
     * （如记录编辑页的类型 LazyRow），onNode 匹到多个会直接报错。
     */
    private fun assertCardPresent(title: String) {
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(title))
        rule.onNodeWithText(title).assertExists()
    }

    /**
     * 只验统计页的**外壳**能组合出来（标题、维度切换、周期标签）。
     * 不验五张卡片：uiState 是 stateIn(...)，Robolectric 下 viewModelScope 的
     * Main.immediate 上游不被采集，model 恒为 null。聚合口径由
     * StatsRepositoryTest 的 14 条用例保证，不在这里重复测一遍假的。
     */
    @Test fun `stats screen shell composes without crashing`() {
        content {
            val nav = rememberNavController()
            StatsScreen(nav)
        }
        rule.waitForIdle()
        rule.onNodeWithText("数据统计").assertExists()
        rule.onNodeWithText("周").assertExists()
        rule.onNodeWithText("月").assertExists()
    }

    @Test fun `calendar screen composes and exposes the add-training action`() {
        content {
            val nav = rememberNavController()
            CalendarScreen(nav)
        }
        rule.waitForIdle()
        // FAB 只有图标，"添加训练" 是 contentDescription（规格 5.3 要求可访问性标签）
        rule.onNodeWithContentDescription("添加训练").assertExists()
    }

    @Test fun `profile screen composes`() {
        content {
            val nav = rememberNavController()
            ProfileScreen(nav)
        }
        rule.waitForIdle()
        rule.onNodeWithText("我的").assertExists()
        rule.onNodeWithText("数据备份与恢复").assertExists()
    }

    @Test fun `appearance screen composes and offers three theme modes`() {
        content {
            val nav = rememberNavController()
            AppearanceScreen(nav)
        }
        rule.waitForIdle()
        rule.onNodeWithText("跟随系统").assertExists()
        rule.onNodeWithText("浅色").assertExists()
        rule.onNodeWithText("深色").assertExists()
    }

    @Test fun `body screen composes and requires weight`() {
        content {
            val nav = rememberNavController()
            BodyProfileScreen(nav)
        }
        rule.waitForIdle()
        rule.onNodeWithText("体重 kg（必填）").assertExists()
    }

    @Test fun `day screen composes for a date with records`() {
        content {
            val nav = rememberNavController()
            DayRecordScreen(nav, LocalDate.of(2026, 10, 12))
        }
        rule.waitForIdle()
        rule.onNodeWithText("今日已健身").assertExists()
        rule.onNodeWithText("划船机").assertExists()
    }

    @Test fun `record editor screen composes with builtin types`() {
        content {
            val nav = rememberNavController()
            RecordEditorScreen(nav, LocalDate.of(2026, 10, 12), -1L)
        }
        rule.waitForIdle()
        rule.onNodeWithText("新增训练").assertExists()
        assertCardPresent("锻炼部位（可多选）")
        assertCardPresent("保存")
    }
}
