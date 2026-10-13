package com.fitrecord.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.AppContainer
import com.fitrecord.app.data.db.FitRecordDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 降级状态机（规格 6.9 / 6.10）。
 * 以代码里的 [AppContainer.FatalState] 为准 —— 计划文本的 FatalReason 枚举未采用（台账 T5-9 deferred）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class FatalStateTest {

    @Test fun `read only flag and fatal state are set together`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        container.markReadOnlyForTest(IllegalStateException("migration failed"))
        assertTrue(container.readOnly.value)
        assertEquals(AppContainer.FatalState.MigrationFailed("migration failed"), container.fatal.value)
    }

    @Test fun `reset clears both downgrade flags before recreate`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        container.markReadOnlyForTest(IllegalStateException("boom"))
        container.resetFatalForRecreate()
        assertFalse(container.readOnly.value)
        assertEquals(AppContainer.FatalState.Ok, container.fatal.value)
    }

    /** SQLITE_NOTADB 消息必须落到 Corrupted 而不是 MigrationFailed —— 决定整屏切恢复页还是只挂横幅 */
    @Test fun `notadb is classified as corrupted not migration failure`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        container.markFatal(IllegalStateException("file is not a database (SQLITE_NOTADB)"))
        assertTrue(container.fatal.value is AppContainer.FatalState.Corrupted)
    }

    @Test fun `recreate deletes the database file`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getDatabasePath(FitRecordDatabase.DB_NAME).also { it.parentFile?.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3))
        ctx.deleteDatabase(FitRecordDatabase.DB_NAME)
        assertFalse(ctx.getDatabasePath(FitRecordDatabase.DB_NAME).exists())
    }
}
