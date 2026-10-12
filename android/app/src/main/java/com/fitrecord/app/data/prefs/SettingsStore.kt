package com.fitrecord.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fitrecord.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
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

    /**
     * 所有读取的唯一上游。DataStore 在文件损坏（写到一半被杀、0 字节、protobuf 解不开）时
     * 会从 data 流抛 IOException；担心的是"设置坏了 → 启动即崩"，
     * 所以这里兜住并退回空偏好（等价于回到默认主题 + 周一起始）。
     * 非 IO 异常（如取消）必须原样抛出，不能吞。
     */
    private val prefs: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val themeMode: Flow<ThemeMode> = prefs.map { themeOf(it) }

    val firstDayOfWeek: Flow<DayOfWeek> = prefs.map { dayOf(it) }

    val lastExerciseTypeId: Flow<Long?> = prefs.map { it[Keys.LAST_TYPE] }

    /**
     * 三者原子一次 emit。UI 侧（Task 24）应当只订阅这一个：
     * 若 theme 与 firstDayOfWeek 分处两个订阅点，改周起始日时可能出现
     * "主题已切、周视图首日还没切"的中间帧。
     */
    fun snapshot(): Flow<AppSettings> = prefs.map { AppSettings(themeOf(it), dayOf(it), it[Keys.LAST_TYPE]) }

    suspend fun setThemeMode(mode: ThemeMode) = writeSafely { it[Keys.THEME] = mode.name }

    suspend fun setFirstDayOfWeek(day: DayOfWeek) = writeSafely { it[Keys.FIRST_DAY] = day.name }

    suspend fun setLastExerciseTypeId(id: Long) = writeSafely { it[Keys.LAST_TYPE] = id }

    /**
     * 写路径也必须兜 IOException（复审 I-1 残留）：`edit()` 与读走同一条 read 链路，
     * 文件损坏时只兜读不兜写，等于"启动不崩了，但改一次设置崩一次"。
     * 这里选择吐掉而不是上抛：设置是非关键数据（读侧已回落默认值），
     * 最坏结果是下次启动回到默认主题/周起始日；而训练记录走 Room，不经这条路。
     */
    private suspend fun writeSafely(block: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (_: IOException) {
            // 故意不抛：UI 侧（Task 24）不得因为一个偏好写不进去而崩
        }
    }

    /** 测试专用：写入无法解析的脏值，验证回落而不是抛 */
    @androidx.annotation.VisibleForTesting
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
