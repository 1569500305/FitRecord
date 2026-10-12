package com.fitrecord.app.data.db.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 种子数据守护测试：这些数值是卡路里与食物换算的输入，改错一个用户看到的数字就错了 */
class SeedDataTest {

    @Test fun `8 builtin exercises in spec order`() {
        assertEquals(8, BuiltinExercises.items.size)
        assertEquals(
            listOf("划船机", "椭圆机", "动感单车", "跑步机", "自由力量", "瑜伽", "快走", "游泳"),
            BuiltinExercises.items.map { it.name },
        )
    }

    @Test fun `builtin met values match spec section 4_5`() {
        assertEquals(7.0, BuiltinExercises.byName("划船机").met, 1e-9)
        assertEquals(5.0, BuiltinExercises.byName("椭圆机").met, 1e-9)
        assertEquals(6.8, BuiltinExercises.byName("动感单车").met, 1e-9)
        assertEquals(8.3, BuiltinExercises.byName("跑步机").met, 1e-9)
        assertEquals(5.0, BuiltinExercises.byName("自由力量").met, 1e-9)
        assertEquals(2.5, BuiltinExercises.byName("瑜伽").met, 1e-9)
        assertEquals(4.3, BuiltinExercises.byName("快走").met, 1e-9)
        assertEquals(5.8, BuiltinExercises.byName("游泳").met, 1e-9)
    }

    /** colorIndex 必须等于 ExercisePalette 下标，否则日历点与饼图会串色 */
    @Test fun `builtin color indices are zero to seven with no duplicate`() {
        assertEquals((0..7).toList(), BuiltinExercises.items.map { it.colorIndex })
    }

    @Test fun `every builtin default body part id exists in BodyParts`() {
        val validIds = BodyParts.items.map { it.first }.toSet()
        for (item in BuiltinExercises.items) {
            val ids = BodyParts.parseIds(item.defaultBodyPartIds)
            assertTrue("${item.name} 的默认部位 id 越界: $ids", ids.isNotEmpty() && validIds.containsAll(ids))
        }
    }

    @Test fun `8 body parts with ids 1 to 8 and 全身有氧 last`() {
        assertEquals(8, BodyParts.items.size)
        assertEquals((1L..8L).toList(), BodyParts.items.map { it.first })
        assertEquals("全身有氧", BodyParts.items.last().second)
        assertEquals(8, BodyParts.items.map { it.second }.distinct().size)
    }

    @Test fun `food library has 10 entries with spec kcal values`() {
        assertEquals(10, Foods.items.size)
        assertEquals(232.0, Foods.byName("米饭").kcalPerUnit, 1e-9)
        assertEquals(106.0, Foods.byName("香蕉").kcalPerUnit, 1e-9)
        assertEquals(55.0, Foods.byName("巧克力").kcalPerUnit, 1e-9)
        assertEquals(350.0, Foods.byName("奶茶").kcalPerUnit, 1e-9)
        assertTrue(Foods.items.all { it.emoji.isNotBlank() })
        assertTrue(Foods.items.all { it.unitLabel.isNotBlank() })
        assertTrue(Foods.items.all { it.kcalPerUnit > 0 })
    }

    @Test fun `met reference has 32 unique entries sorted ascending by met`() {
        assertEquals(32, MetReference.items.size)
        assertEquals(32, MetReference.items.map { it.name }.distinct().size)
        assertTrue(
            "必须按 MET 升序，P8b 弹层按此顺序分组展示",
            MetReference.items.zipWithNext().all { (a, b) -> a.met <= b.met },
        )
    }

    @Test fun `met reference groups in intensity order not alphabetical`() {
        val groups = MetReference.groupByIntensity().map { it.first }
        assertEquals(
            listOf(MetReference.LIGHT, MetReference.MODERATE, MetReference.HIGH, MetReference.VERY_HIGH),
            groups,
        )
        // 每个强度组都非空，否则 P8b 会渲染出空标题
        assertTrue(MetReference.groupByIntensity().all { it.second.isNotEmpty() })
    }

    @Test fun `met reference covers the builtin exercises it was seeded from`() {
        // 参考表里应能找到内置项，用户改 MET 时才有对照
        for (name in listOf("划船机", "椭圆机", "瑜伽", "游泳")) {
            assertTrue("参考表缺 $name", MetReference.items.any { it.name.startsWith(name) })
        }
    }
}
