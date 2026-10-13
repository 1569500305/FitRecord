package com.fitrecord.app

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.db.dao.BodyPartDao
import com.fitrecord.app.data.db.dao.FoodDao
import com.fitrecord.app.data.db.dao.MetaDao
import com.fitrecord.app.data.db.dao.NoteDao
import com.fitrecord.app.data.db.dao.ProfileDao
import com.fitrecord.app.data.db.dao.StatsDao
import com.fitrecord.app.data.db.dao.TrainingDao
import com.fitrecord.app.data.db.dao.TypeDao
import com.fitrecord.app.data.prefs.SettingsStore
import com.fitrecord.app.data.repo.BackupRepository
import com.fitrecord.app.data.repo.SeedRepository
import com.fitrecord.app.data.repo.StatsRepository
import com.fitrecord.app.data.repo.TrainingRepository
import com.fitrecord.app.ui.theme.ExercisePalette
import com.fitrecord.app.ui.theme.toRgbLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 手写依赖容器（不引 Hilt，见技术选型）。
 * 暴露 database 为 internal 是台账 P9 裁决：BackupRepository 需要 db.withTransaction。
 */
class AppContainer(
    private val appContext: Context,
    overrideDb: FitRecordDatabase? = null,
) {
    internal val database: FitRecordDatabase =
        overrideDb ?: FitRecordDatabase.build(appContext)

    val typeDao: TypeDao get() = database.typeDao()
    val bodyPartDao: BodyPartDao get() = database.bodyPartDao()
    val trainingDao: TrainingDao get() = database.trainingDao()
    val statsDao: StatsDao get() = database.statsDao()
    val noteDao: NoteDao get() = database.noteDao()
    val profileDao: ProfileDao get() = database.profileDao()
    val foodDao: FoodDao get() = database.foodDao()
    val metaDao: MetaDao get() = database.metaDao()

    /** 应用级后台作用域（种子写入、导入等） */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val seedRepo by lazy { SeedRepository(typeDao, foodDao, bodyPartDao) }

    /** 主题模式 / 周起始日 / 最近使用类型（Task 10） */
    val settings: SettingsStore by lazy { SettingsStore.from(appContext) }

    /** 记录读写与统计聚合（Task 8 / Task 11） */
    val trainingRepo by lazy {
        TrainingRepository(trainingDao, typeDao, profileDao, noteDao, foodDao)
    }
    val statsRepo by lazy { StatsRepository(statsDao, foodDao) }

    /**
     * 备份导入导出（Task 23）。db.withTransaction 需持有 database（台账 P9 已把 database 提为 internal）。
     * 取色调色板由组合根注入，让 BackupRepository 停留在 data 层、不反向依赖 ui（评审前瞻）。
     */
    val backupRepo by lazy {
        BackupRepository(
            db = database,
            trainingDao = trainingDao,
            typeDao = typeDao,
            profileDao = profileDao,
            noteDao = noteDao,
            bodyPartDao = bodyPartDao,
            metaDao = metaDao,
            colorForNewType = { id -> ExercisePalette.colorForId(id).toRgbLong() },
        )
    }

    /**
     * 幂等：表非空则直接返回（Task 6）。
     * 必须吃掉异常：这是后台作用域上的 fire-and-forget，抛出会变成
     * 未捕获异常，污染同一进程内后续的单元测试（实测会触发
     * kotlinx-coroutines-test 的 UncaughtExceptionsBeforeTest）。
     */
    fun seedNow() = appScope.launch {
        try {
            seedRepo.ensureSeeded()
        } catch (t: Throwable) {
            markFatal(t)
        }
    }

    // ---------- 降级状态（规格 6.9 / 6.10，台账 P5 一次建好）----------

    private val _readOnly = MutableStateFlow(false)

    /** true 时所有写操作被禁用，UI 顶部显示红色横幅 */
    val readOnly: StateFlow<Boolean> = _readOnly.asStateFlow()

    private val _fatal = MutableStateFlow<FatalState>(FatalState.Ok)

    /** 数据库彻底打不开时整屏切到恢复页 */
    val fatal: StateFlow<FatalState> = _fatal.asStateFlow()

    sealed interface FatalState {
        data object Ok : FatalState
        data class MigrationFailed(val reason: String?) : FatalState
        data class Corrupted(val reason: String?) : FatalState
    }

    @VisibleForTesting
    fun markReadOnlyForTest(t: Throwable) {
        _readOnly.value = true
        _fatal.value = FatalState.MigrationFailed(t.message)
    }

    /** 恢复页删库重启前调用：复位降级态，避免重启瞬间仍闪现只读横幅 */
    fun resetFatalForRecreate() {
        _readOnly.value = false
        _fatal.value = FatalState.Ok
    }

    fun markFatal(t: Throwable) {
        _readOnly.value = true
        // 沿异常链收集消息：Room 常把 SQLiteException 包在 IOException/IllegalStateException 里，
        // 只看顶层 message 会漏判。
        val chain = generateSequence(t) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        // 不只是 NOTADB：典型损坏是 "database disk image is malformed"（SQLITE_CORRUPT），
        // 漏了它就只会挂红色横幅、永远进不了恢复页（规格 6.10）。
        val corrupted = CORRUPT_MARKERS.any { chain.contains(it, ignoreCase = true) }
        _fatal.value = if (corrupted) {
            FatalState.Corrupted(t.message)
        } else {
            FatalState.MigrationFailed(t.message)
        }
    }

    private companion object {
        val CORRUPT_MARKERS = listOf(
            "SQLITE_NOTADB", "file is not a database", "malformed",
            "SQLITE_CORRUPT", "file is too short",
        )
    }
}
