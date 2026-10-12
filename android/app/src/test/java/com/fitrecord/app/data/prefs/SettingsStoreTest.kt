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

    private fun storeFor(name: String): SettingsStore {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file: File = context.preferencesDataStoreFile(name)
        file.delete()
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.IO),
            produceFile = { file },
        )
        return SettingsStore(ds)
    }

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
