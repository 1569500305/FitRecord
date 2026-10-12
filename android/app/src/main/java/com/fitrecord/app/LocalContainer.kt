package com.fitrecord.app

import androidx.compose.runtime.compositionLocalOf

/** ViewModel 工厂从这里取容器，屏幕不直接摸数据库 */
val LocalContainer = compositionLocalOf<AppContainer> {
    error("LocalContainer 未提供：FitRecordApp.setContent 必须包一层 CompositionLocalProvider")
}
