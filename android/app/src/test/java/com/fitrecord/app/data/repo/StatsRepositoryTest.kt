package com.fitrecord.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.db.entity.UserProfileEntity
import com.fitrecord.app.domain.PeriodType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 统计聚合口径测试（规格 4.6）。这里钉的是"数字怎么算"，不是"数字好不好看"：
 * 周/月边界、平均分的分母是周期天数还是打卡天数、多部位是否各计一次、
 * 趋势桶数（周 8 / 月 6）—— 这些一旦漂了，用户看到的图就会静默说谎。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StatsRepositoryTest {

    private lateinit var db: FitRecordDatabase
    private lateinit var training: TrainingRepository
    private lateinit var stats: StatsRepository

    private val weekAnchor = LocalDate.of(2026, 10, 12)   // 周一
    private val monthAnchor = LocalDate.of(2026, 10, 12)

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FitRecordDatabase::class.java
        ).build()
        SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao()).ensureSeeded()
        db.profileDao().upsert(UserProfileEntity(weightKg = 70.0, updatedAt = 0))
        training = TrainingRepository(
            db.trainingDao(), db.typeDao(), db.profileDao(), db.noteDao(), db.foodDao(),
        )
        stats = StatsRepository(db.statsDao(), db.foodDao())
    }

    /** 10-12 划船45 · 10-13 划船30 · 10-14 跑步60+瑜伽15（同日两段）· 09-30 划船20（越界项）*/
    private suspend fun seedRecords() {
        training.add(weekAnchor, 1, 45, listOf(2, 5), "")
        training.add(weekAnchor.plusDays(1), 1, 30, listOf(2, 5), "")
        training.add(weekAnchor.plusDays(2), 4, 60, listOf(8), "")
        training.add(weekAnchor.plusDays(2), 6, 15, listOf(5), "")
        training.add(LocalDate.of(2026, 9, 30), 1, 20, listOf(2), "")
    }

    @Test fun `week period spans monday to sunday and labels both ends`() {
        val p = stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)
        assertEquals(7, p.range.last - p.range.first + 1)
        assertEquals(weekAnchor.toEpochDay().toInt(), p.range.first)
        assertEquals("2026-10-12 ~ 10-18", p.label)
    }

    @Test fun `month period covers the whole calendar month`() {
        val p = stats.periodOf(PeriodType.MONTH, monthAnchor, DayOfWeek.MONDAY)
        assertEquals(YearMonth.of(2026, 10).atDay(1).toEpochDay().toInt(), p.range.first)
        assertEquals(YearMonth.of(2026, 10).atEndOfMonth().toEpochDay().toInt(), p.range.last)
        assertEquals("2026年10月", p.label)
    }

    @Test fun `week start honors the configured first day of week`() {
        val sunday = stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 10, 11).toEpochDay().toInt(), sunday.range.first)
        assertEquals(DayOfWeek.SUNDAY, sunday.firstDay)
    }

    @Test fun `summary counts segments minutes and trained days`() = runBlocking {
        seedRecords()
        val ui = stats.observeStats(stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)).first()
        assertEquals(4, ui.summary.segments)
        assertEquals(150, ui.summary.minutes)
        assertEquals(3, ui.summary.trainedDays)              // 12 / 13 / 14 三天
        assertEquals(21, ui.summary.avgMinPerDay)            // 150 / 7 周期天数（含 0 的天）
        assertEquals(38, ui.summary.avgMinPerSegment)        // 150 / 4 段
        assertEquals(0, ui.summary.uncalculated)
        assertNotNull(ui.summary.calories)
    }

    @Test fun `records outside the period are excluded`() = runBlocking {
        seedRecords()
        val week = stats.observeStats(
            stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)
        ).first()
        val septEpoch = LocalDate.of(2026, 9, 30).toEpochDay().toInt()
        assertTrue("周统计混进了 9 月的记录", week.perDay.none { it.date == septEpoch })
        assertEquals(4, week.summary.segments)

        // 9-30 落在 10-12 那一周之外，但落在 9 月里
        val september = stats.observeStats(
            stats.periodOf(PeriodType.MONTH, LocalDate.of(2026, 9, 15), DayOfWeek.MONDAY)
        ).first()
        assertEquals(1, september.summary.segments)
        assertEquals(20, september.summary.minutes)
    }

    @Test fun `heatmap has one bucket per day of the period`() = runBlocking {
        seedRecords()
        val week = stats.observeStats(
            stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)
        ).first()
        assertEquals(7, week.heat.size)
        assertEquals(listOf(1, 1, 2, 0, 0, 0, 0), week.heat)   // 10-14 有两段
    }

    @Test fun `type share is duration weighted and sums to about 100 percent`() = runBlocking {
        seedRecords()
        val ui = stats.observeStats(
            stats.periodOf(PeriodType.MONTH, monthAnchor, DayOfWeek.MONDAY)
        ).first()
        assertEquals("划船机", ui.byType.first().name)
        assertEquals(75, ui.byType.first().minutes)            // 10 月内 45 + 30
        assertEquals(3, ui.byType.size)                        // 未超过 Top 6，不应出现"其他"
        assertTrue("占比之和=${ui.byType.map { it.percent }.sum()}", ui.byType.map { it.percent }.sum() in 99f..101f)
    }

    @Test fun `more than six types collapses the tail into 其他`() = runBlocking {
        // 内置共 8 类，用 7 类造出第 7 名以后被并入"其他"的场景
        val types = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L)
        types.forEachIndexed { i, typeId -> training.add(weekAnchor.plusDays(i.toLong()), typeId, 10 + i, listOf(1), "") }
        val ui = stats.observeStats(
            stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)
        ).first()
        assertEquals(7, ui.byType.size)                       // Top 6 + “其他”共 7 片
        assertEquals("其他", ui.byType.last().name)
        assertEquals(1, ui.byType.last().segments)             // 第 7 名被单独归入其他
        assertTrue(ui.byType.map { it.percent }.sum() in 99f..101f)
    }

    @Test fun `radar counts segments per body part and always returns 8 axes`() = runBlocking {
        seedRecords()
        val ui = stats.observeStats(
            stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)
        ).first()
        assertEquals(8, ui.byPart.size)                        // 无数据的轴补 0
        assertEquals(2, ui.byPart[2L])                         // 背：10-12 与 10-13 两条划船机
        assertEquals(3, ui.byPart[5L])                         // 核心：两条划船机 + 一条瑜伽（多部位各计 1）
        assertEquals(1, ui.byPart[8L])                         // 全身有氧：跑步机
        assertEquals(0, ui.byPart[1L])                         // 胸：没练过
    }

    @Test fun `trend has 8 weekly buckets and 6 monthly buckets`() = runBlocking {
        seedRecords()
        val week = stats.observeStats(
            stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)
        ).first()
        assertEquals(8, week.trend.size)
        assertEquals(150, week.trend.last().minutes)           // 最后一桶就是本周
        assertEquals(0, week.trend.first().minutes)            // 7 周前无记录

        val month = stats.observeStats(
            stats.periodOf(PeriodType.MONTH, monthAnchor, DayOfWeek.MONDAY)
        ).first()
        assertEquals(6, month.trend.size)
        assertEquals(150, month.trend.last().minutes)
        assertEquals(20, month.trend[month.trend.lastIndex - 1].minutes)   // 上一桶是 9 月
    }

    @Test fun `empty period yields zeroed summary and empty food text`() = runBlocking {
        val ui = stats.observeStats(
            stats.periodOf(PeriodType.WEEK, LocalDate.of(2026, 1, 5), DayOfWeek.MONDAY)
        ).first()
        assertEquals(0, ui.summary.segments)
        assertEquals(0, ui.summary.minutes)
        assertNull(ui.summary.calories)
        assertEquals("", ui.foodText)
        assertEquals(7, ui.heat.size)
        assertTrue(ui.heat.all { it == 0 })
        assertTrue(ui.byType.isEmpty())
    }

    @Test fun `records without weight leave calories null but still count`() = runBlocking {
        db.profileDao().delete()
        training.add(weekAnchor, 1, 45, listOf(2), "")
        val ui = stats.observeStats(
            stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)
        ).first()
        assertEquals(1, ui.summary.segments)
        assertEquals(45, ui.summary.minutes)
        assertNull(ui.summary.calories)
        assertEquals(1, ui.summary.uncalculated)               // 规格 4.6：要注明未计算条数
        assertEquals("", ui.foodText)
    }

    @Test fun `food text is derived from the period total`() = runBlocking {
        seedRecords()
        val ui = stats.observeStats(
            stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)
        ).first()
        assertTrue("实际文案：${ui.foodText}", ui.foodText.startsWith("≈ "))
    }

    @Test fun `shifting a period never mutates its firstDay`() {
        val p = stats.periodOf(PeriodType.MONTH, monthAnchor, DayOfWeek.SUNDAY)
        val shifted = p.shifted(-1)
        assertEquals("2026年9月", shifted.label)
        assertEquals(DayOfWeek.SUNDAY, shifted.firstDay)
        assertEquals(p.firstDay, shifted.firstDay)
    }
}
