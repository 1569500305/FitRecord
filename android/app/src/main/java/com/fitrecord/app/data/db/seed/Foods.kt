package com.fitrecord.app.data.db.seed

import com.fitrecord.app.data.db.entity.FoodItemEntity

/**
 * 食物等价换算库（规格 4.4）。V1 固定 10 项、不提供搜索。
 * unitLabel 是"数值之后"直接拼接的量词，所以自带单位名（如"碗米饭"）。
 */
object Foods {

    val items: List<FoodItemEntity> = listOf(
        FoodItemEntity(1, "米饭", "🍚", "碗米饭", 232.0),
        FoodItemEntity(2, "香蕉", "🍌", "根香蕉", 106.0),
        FoodItemEntity(3, "苹果", "🍎", "个苹果", 104.0),
        FoodItemEntity(4, "可乐", "🥤", "罐可乐", 139.0),
        FoodItemEntity(5, "巧克力", "🍫", "块巧克力", 55.0),
        FoodItemEntity(6, "鸡腿", "🍗", "个鸡腿", 181.0),
        FoodItemEntity(7, "鸡蛋", "🥚", "个鸡蛋", 72.0),
        FoodItemEntity(8, "吐司", "🍞", "片吐司", 79.0),
        FoodItemEntity(9, "牛奶", "🥛", "盒牛奶", 135.0),
        FoodItemEntity(10, "奶茶", "🧋", "杯奶茶", 350.0),
    )

    fun byName(name: String): FoodItemEntity = items.first { it.name == name }
}
