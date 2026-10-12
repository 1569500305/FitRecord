package com.fitrecord.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.DayOfWeek

/**
 * 设置持久化测试（规格 D8 / D9 / P10）。
 * 每个用例用独立文件，避免"同一文件多个 DataStore 实例"的运行时崩溃。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsStoreTest {

    /** 一个用例一套 (scope, file, store)；scope 保留是为了能"关掉再重开同一个文件" */
    private class Harness(val file: File, val scope: CoroutineScope, val settings: SettingsStore)

    private fun harness(name: String, wipe: Boolean = true): Harness {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.preferencesDataStoreFile(name)
        if (wipe) file.delete()
        val scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.IO)
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return Harness(file, scope, SettingsStore(ds))
    }

    private fun storeFor(name: String): SettingsStore = harness(name).settings

    @Test fun `defaults are follow-system, monday, and no last type`() = runBlocking {
        val s = storeFor("t_defaults").snapshot().first()
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertEquals(DayOfWeek.MONDAY, s.firstDayOfWeek)
        assertEquals(null, s.lastExerciseTypeId)
    }

    @Test fun `theme mode round trips`() = runBlocking {
        val s = storeFor("t_theme")
        s.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, s.themeMode.first())
        s.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, s.themeMode.first())
    }

    @Test fun `unknown stored theme string falls back to SYSTEM instead of crashing`() = runBlocking {
        val s = storeFor("t_badtheme")
        s.setRawString("theme_mode", "PIXEL")
        assertEquals(ThemeMode.SYSTEM, s.themeMode.first())
        assertEquals(ThemeMode.SYSTEM, s.snapshot().first().themeMode)
    }

    @Test fun `unknown stored day string falls back to MONDAY`() = runBlocking {
        val s = storeFor("t_badday")
        s.setRawString("first_day_of_week", "FUNDAY")
        assertEquals(DayOfWeek.MONDAY, s.firstDayOfWeek.first())
    }

    /**
     * DataStore 1.1.1 没有公开 close()，同一文件也不允许第二个活动实例，
     * 所以无法用"关掉再重开"验证跨进程持久化。这里退一步：
     * 断言写入确实落到了磁盘文件（而不是只在内存里），
     * 并把"读回正确值"的语义交给上面的 round-trip 用例。
     */
    @Test fun `writes hit the backing file on disk`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.preferencesDataStoreFile("t_disk")
        file.delete()
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.IO),
            produceFile = { file },
        )
        val s = SettingsStore(ds)

        s.setFirstDayOfWeek(DayOfWeek.SUNDAY)
        s.setLastExerciseTypeId(7L)

        assertTrue("设置未落盘：$file 不存在", file.exists())
        assertTrue("设置文件为空，写入只在内存里", file.length() > 0)
        assertEquals(DayOfWeek.SUNDAY, s.firstDayOfWeek.first())
    }

    /**
     * 评审 I-4：真正的"重启后读得回"守护。DataStore 把实例注册表挂在文件上，
     * 但它的生命周期跟着传入的 scope 走 —— 取消那个 scope 后，同一个 File
     * 可以由新 scope 重新打开，等价于进程重启。
     */
    @Test fun `settings survive a closed and reopened store on the same file`() = runBlocking {
        val first = harness("t_reopen")
        first.settings.setThemeMode(ThemeMode.DARK)
        first.settings.setFirstDayOfWeek(DayOfWeek.SUNDAY)
        first.settings.setLastExerciseTypeId(11L)
        first.scope.cancel()
        first.scope.coroutineContext[Job]!!.join()   // 等旧 store 真的释放完文件再重开

        val secondScope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.IO)
        val secondDs: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = secondScope,
            produceFile = { first.file },
        )
        val reopened = SettingsStore(secondDs).snapshot().first()

        assertEquals("重启后主题丢了", ThemeMode.DARK, reopened.themeMode)
        assertEquals("重启后周起始日丢了", DayOfWeek.SUNDAY, reopened.firstDayOfWeek)
        assertEquals(11L, reopened.lastExerciseTypeId)
        secondScope.cancel()
    }

    /**
     * 评审 I-1：设置文件损坏（写到一半被杀、乱码字节）时，读取必须退回默认值而不是抛。
     * 否则 Task 24 把 snapshot() 接进 MainActivity 后，就是"设置坏了 → 启动即崩"。
     */
    @Test fun `corrupt settings file falls back to defaults instead of throwing`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.preferencesDataStoreFile("t_corrupt")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x7F, 0x2A, 0x2A, 0x2A))

        val scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.IO)
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val snapshot = SettingsStore(ds).snapshot().first()

        assertEquals(ThemeMode.SYSTEM, snapshot.themeMode)
        assertEquals(DayOfWeek.MONDAY, snapshot.firstDayOfWeek)
        assertEquals(null, snapshot.lastExerciseTypeId)
        scope.cancel()
    }

    @Test fun `last exercise type round trips`() = runBlocking {
        val s = storeFor("t_lasttype")
        s.setLastExerciseTypeId(42L)
        assertEquals(42L, s.lastExerciseTypeId.first())
    }

    @Test fun `snapshot emits again after a write`() = runBlocking {
        val s = storeFor("t_reemit")
        assertEquals(ThemeMode.SYSTEM, s.snapshot().first().themeMode)
        s.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, s.snapshot().first().themeMode)
    }
}
