package com.fitrecord.app

import android.app.Application

class FitRecordApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 建库本身是懒的（首次访问 DAO 才真正打开），因此这里不会抛迁移异常；
        // 迁移/损坏的捕获点在各仓库的首次查询处（Task 24 接 markFatal）。
        container = AppContainer(this)
    }
}
