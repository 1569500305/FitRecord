package com.fitrecord.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.AppContainer
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 每个 ViewModel 工厂都必须能造出自己那一个类。
 *
 * 这是真机闪退的回归测试：factoryOf 曾把类型擦除成 ViewModel，
 * 导致 viewModelFactory 用 ViewModel::class 当注册 key，
 * 屏幕按具体类索取时抛 "No initializer set for given class …"。
 * 只测起始页不够 —— 每个屏都要走到。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ViewModelFactoryTest {

    private lateinit var container: AppContainer
    private val today: LocalDate = LocalDate.of(2026, 10, 12)

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FitRecordDatabase::class.java).build()
        com.fitrecord.app.data.repo.SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao()).ensureSeeded()
        container = AppContainer(context, overrideDb = db)
    }

    private fun ViewModelProvider.Factory.createFor(clazz: Class<out androidx.lifecycle.ViewModel>): androidx.lifecycle.ViewModel =
        create(clazz, androidx.lifecycle.viewmodel.CreationExtras.Empty)

    @Test fun `calendar factory returns CalendarViewModel`() {
        val vm = CalendarViewModel.factory(container) { today }.createFor(CalendarViewModel::class.java)
        assertTrue(vm is CalendarViewModel)
    }

    @Test fun `day factory returns DayRecordViewModel`() {
        val vm = DayRecordViewModel.factory(container, today).createFor(DayRecordViewModel::class.java)
        assertTrue(vm is DayRecordViewModel)
    }

    @Test fun `editor factory returns RecordEditorViewModel`() {
        val vm = RecordEditorViewModel.factory(container, today, -1L).createFor(RecordEditorViewModel::class.java)
        assertTrue(vm is RecordEditorViewModel)
    }

    @Test fun `stats factory returns StatsViewModel`() {
        val vm = StatsViewModel.factory(container) { today }.createFor(StatsViewModel::class.java)
        assertTrue(vm is StatsViewModel)
    }

    @Test fun `body factory returns BodyProfileViewModel`() {
        val vm = BodyProfileViewModel.factory(container).createFor(BodyProfileViewModel::class.java)
        assertTrue(vm is BodyProfileViewModel)
    }

    @Test fun `appearance factory returns AppearanceViewModel`() {
        val vm = AppearanceViewModel.factory(container).createFor(AppearanceViewModel::class.java)
        assertTrue(vm is AppearanceViewModel)
    }

    @Test fun `backup factory returns BackupViewModel`() {
        val vm = BackupViewModel.factory(container).createFor(BackupViewModel::class.java)
        assertTrue(vm is BackupViewModel)
    }

    /*
     * 刻意不写“VM 首帧就能交出完整状态”这类断言：
     * viewModelScope 是 Dispatchers.Main.immediate，在 runBlocking 里主线程被阻，
     * stateIn 的上游根本不会被采集，测到的是调度器而不是业务。
     * 首帧内容的验证交给 CalendarBuilderTest / StatsRepositoryTest 这些纯逻辑用例。
     */

    @Test fun `theme mode default is system`() = runBlocking {
        assertEquals(ThemeMode.SYSTEM, container.settings.themeMode.first())
    }
}
