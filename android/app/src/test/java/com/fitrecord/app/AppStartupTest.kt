package com.fitrecord.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 启动冒烟测试：真机报"装完就闪退"，而设备未连接拿不到 logcat，
 * 所以在 JVM 上真实创建并 resume MainActivity —— 任何 onCreate/组合期的崩溃都会在这里抛出。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppStartupTest {

    @Test fun `main activity can be created and resumed without crashing`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertTrue("Activity 未创建成功", activity != null)
            }
        }
    }
}
