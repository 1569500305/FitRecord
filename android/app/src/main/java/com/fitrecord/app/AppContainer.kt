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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手写依赖容器（不引 Hilt，见技术选型）。
 * 暴露 database 为 internal 是台账 P9 裁决：BackupRepository 需要 db.withTransaction。
 */
class AppContainer(
    appContext: Context,
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

    fun markFatal(t: Throwable) {
        val message = t.message.orEmpty()
        _readOnly.value = true
        _fatal.value = if (message.contains("SQLITE_NOTADB") || message.contains("file is not a database")) {
            FatalState.Corrupted(message)
        } else {
            FatalState.MigrationFailed(message)
        }
    }
}
