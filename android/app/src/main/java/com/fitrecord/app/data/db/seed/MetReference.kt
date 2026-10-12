package com.fitrecord.app.data.db.seed

/**
 * P8b「常见运动 MET 参考表」数据源（规格 4.5）：用户自定义类型填 MET 时对照用。
 * items 必须按 MET 升序 —— 弹层按强度分组展示，顺序即视觉顺序。
 */
data class MetRef(val name: String, val met: Double, val intensity: String)

object MetReference {

    const val LIGHT = "低强度 (<3)"
    const val MODERATE = "中等 (3–6)"
    const val HIGH = "较高 (6–9)"
    const val VERY_HIGH = "极高 (>9)"

    /** 分组展示顺序（不是字典序） */
    private val intensityOrder = listOf(LIGHT, MODERATE, HIGH, VERY_HIGH)

    val items: List<MetRef> = listOf(
        MetRef("拉伸放松", 2.0, LIGHT),
        MetRef("瑜伽", 2.5, LIGHT),
        MetRef("散步 3.2km/h", 2.5, LIGHT),
        MetRef("太极", 3.0, MODERATE),
        MetRef("普拉提", 3.0, MODERATE),
        MetRef("家务轻体力", 3.5, MODERATE),
        MetRef("骑行 16km/h 休闲", 4.0, MODERATE),
        MetRef("快走", 4.3, MODERATE),
        MetRef("椭圆机", 5.0, MODERATE),
        MetRef("自由力量 中等", 5.0, MODERATE),
        MetRef("羽毛球 休闲", 5.5, MODERATE),
        MetRef("舞蹈", 5.5, MODERATE),
        MetRef("游泳 休闲", 5.8, MODERATE),
        MetRef("乒乓球", 5.8, MODERATE),
        MetRef("篮球 休闲", 6.5, HIGH),
        MetRef("动感单车 中等", 6.8, HIGH),
        MetRef("划船机 中等", 7.0, HIGH),
        MetRef("游泳 自由泳中等", 8.0, HIGH),
        MetRef("攀岩", 8.0, HIGH),
        MetRef("HIIT", 8.0, HIGH),
        MetRef("骑行 20km/h", 8.0, HIGH),
        MetRef("跑步机 8km/h", 8.3, HIGH),
        MetRef("爬楼梯", 8.8, HIGH),
        MetRef("划船机 剧烈", 10.0, VERY_HIGH),
        MetRef("足球 比赛", 10.0, VERY_HIGH),
        MetRef("跑步 9.7km/h", 10.0, VERY_HIGH),
        MetRef("波比跳", 10.0, VERY_HIGH),
        MetRef("跳绳 中速", 11.0, VERY_HIGH),
        MetRef("跑步 11.3km/h", 11.0, VERY_HIGH),
        MetRef("篮球 比赛", 11.0, VERY_HIGH),
        MetRef("战绳", 12.3, VERY_HIGH),
        MetRef("冲刺跑", 15.0, VERY_HIGH),
    ).sortedBy { it.met }

    fun groupByIntensity(): List<Pair<String, List<MetRef>>> =
        items.groupBy { it.intensity }
            .let { grouped -> intensityOrder.mapNotNull { key -> grouped[key]?.let { key to it } } }
}
