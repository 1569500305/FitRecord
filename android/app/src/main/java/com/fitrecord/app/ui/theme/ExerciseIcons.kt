package com.fitrecord.app.ui.theme

/**
 * 运动类型名 → 日历格图标（emoji）。
 *
 * 选 emoji 而非打包矢量图标（用户拍板）：零新增资源、跨机型一致、随文本自动缩放。
 * 未命中映射的（多为自定义类型）返回 null，[DayCellView] 据此回退为原来的彩色圆点，
 * 因此"图标显示"对内置类型生效、对自定义类型不丢信息。
 *
 * 匹配用"包含"而非全等：用户把"跑步机"改成"跑步机(快)"之类仍能命中主图标。
 */
object ExerciseIcons {

    // 顺序即优先级：先匹配靠前的同义词
    private val table: List<Pair<List<String>, String>> = listOf(
        listOf("划船") to "🚣",
        listOf("游泳", "泳") to "🏊",
        listOf("骑行", "单车", "骑车", "自行车") to "🚴",
        // 椭圆机是往复踏步，不用台风符号
        listOf("椭圆") to "🔁",
        listOf("跑步", "慢跑", "长跑", "跑") to "🏃",
        listOf("快走", "步行", "走路", "走") to "🚶",
        listOf("深蹲") to "🦵",
        // 俯卧撑是撑起身体；哑铃用手臂发力，杠铃和器械用举铁的人
        listOf("俯卧撑") to "🤸",
        listOf("哑铃") to "💪",
        listOf("力量", "举重", "器械", "增肌") to "🏋️",
        listOf("瑜伽", "拉伸", "普拉提") to "🧘",
        listOf("攀岩", "爬墙") to "🧗",
        listOf("跳绳") to "🪢",
        listOf("篮球") to "🏀",
        listOf("足球") to "⚽",
        listOf("羽毛球", "乒乓球", "网球", "球") to "🏸",
        listOf("舞", "跳操", "有氧操") to "💃",
    )

    /** 命中则返回 emoji，否则 null（触发调用方回退圆点） */
    fun emoji(typeName: String?): String? {
        if (typeName.isNullOrBlank()) return null
        val name = typeName.trim()
        return table.firstOrNull { (keys, _) -> keys.any { name.contains(it) } }?.second
    }
}
