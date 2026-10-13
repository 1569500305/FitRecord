package com.fitrecord.app

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Manifest 契约（把 DoD 第 8 条"零网络/零外部存储"固定成代码）。
 *
 * 黑名单写法而非"只允许这些权限"：androidx.core 会向合并清单注入一个 signature 级自定义
 * 权限（DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION），白名单会因它假失败（台账 T2 note）。
 * minSdk 由 Step4 的 aapt2 badging 兜（Robolectric 读不到 minSdkVersion，它是合并前的 gradle 配置）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ManifestContractTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun permissions(): List<String> =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toList()

    @Test fun `no internet or network-state permission is declared`() {
        val perms = permissions()
        assertFalse("发现网络权限：$perms", perms.contains(android.Manifest.permission.INTERNET))
        assertFalse(perms.contains(android.Manifest.permission.ACCESS_NETWORK_STATE))
    }

    @Test fun `no external storage or media permission`() {
        val perms = permissions()
        assertTrue(perms.none { it.contains("WRITE_EXTERNAL_STORAGE") })
        assertTrue(perms.none { it.contains("READ_EXTERNAL_STORAGE") })
        assertTrue(perms.none { it.startsWith("android.permission.READ_MEDIA") })
    }

    @Test fun `target sdk is modern enough`() {
        val info = context.packageManager.getPackageInfo(context.packageName, 0).applicationInfo!!
        assertTrue(info.targetSdkVersion >= 34)
    }

    /** 规格 D11：不做系统级自动备份，避免旧库把新库覆盖回去 */
    @Test fun `allowBackup is disabled`() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
        assertFalse((info.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) != 0)
    }
}
