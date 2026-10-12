package com.fitrecord.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fitrecord.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private const val FILE_NAME = "fitrecord_settings"

private val Context.fitRecordDataStore: DataStore<Preferences> by preferencesDataStore(name = FILE_NAME)

/** 设置快照，供 MainActivity 一次性订阅以驱动主题与周起始日 */
data class AppSettings(
    val themeMode: ThemeMode,
    val firstDayOfWeek: DayOfWeek,
    val lastExerciseTypeId: Long?,
)

/**
 * 主题模式、周起始日、最近使用类型的持久化（规格 D8 / D9 / P10）。
 *
 * 构造参直接收 DataStore 而不是 Context：测试因此能为每个用例注入独立文件，
 * 避免 preferencesDataStore 属性委托"同一文件多个实例即崩溃"的限制。
 * 所有读取都对脏值容错回落默认 —— 设置读不出来不应该让 App 起不来。
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FIRST_DAY = stringPreferencesKey("first_day_of_week")
        val LAST_TYPE = longPreferencesKey("last_exercise_type_id")
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { themeOf(it) }

    val firstDayOfWeek: Flow<DayOfWeek> = dataStore.data.map { dayOf(it) }

    val lastExerciseTypeId: Flow<Long?> = dataStore.data.map { it[Keys.LAST_TYPE] }

    fun snapshot(): Flow<AppSettings> = dataStore.data.map {
        AppSettings(themeOf(it), dayOf(it), it[Keys.LAST_TYPE])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setFirstDayOfWeek(day: DayOfWeek) {
        dataStore.edit { it[Keys.FIRST_DAY] = day.name }
    }

    suspend fun setLastExerciseTypeId(id: Long) {
        dataStore.edit { it[Keys.LAST_TYPE] = id }
    }

    /** 测试专用：写入无法解析的脏值，验证回落而不是抛 */
    suspend fun setRawString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    /*
     * 注：本类故意不提供 close()。DataStore 1.1.1 的 PreferenceDataStore 不是 Closeable，
     * 也没有公开关闭入口；而同一文件只允许一个活动实例，所以无法用
     * "关掉再重开同一文件"验证跨进程持久化。落盘断言留在测试侧（它自己持有 File）。
     */

    private fun themeOf(p: Preferences): ThemeMode =
        p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM

    private fun dayOf(p: Preferences): DayOfWeek =
        p[Keys.FIRST_DAY]?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() } ?: DayOfWeek.MONDAY

    companion object {
        /** 生产入口：使用 App 唯一的那个 DataStore 实例 */
        fun from(context: Context): SettingsStore =
            SettingsStore(context.applicationContext.fitRecordDataStore)
    }
}
