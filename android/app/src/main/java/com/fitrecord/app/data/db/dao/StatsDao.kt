package com.fitrecord.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.fitrecord.app.data.db.PerDayRow
import com.fitrecord.app.data.db.PerPartRow
import com.fitrecord.app.data.db.PerTypeRow
import kotlinx.coroutines.flow.Flow

/**
 * 统计聚合全部在 SQL 层完成（规格 4.6），上层不在内存遍历全表。
 * 区间参数一律是 epochDay 的 Int，闭区间 [f, t]。
 */
@Dao
interface StatsDao {

    @Query("SELECT COUNT(*) FROM training_record WHERE date BETWEEN :f AND :t")
    suspend fun segments(f: Int, t: Int): Int

    @Query("SELECT COALESCE(SUM(durationMin), 0) FROM training_record WHERE date BETWEEN :f AND :t")
    suspend fun minutes(f: Int, t: Int): Int

    /** 全为 null（未填体重）时返回 null，UI 据此显示"待完善体重" */
    @Query("SELECT SUM(calories) FROM training_record WHERE date BETWEEN :f AND :t")
    suspend fun calories(f: Int, t: Int): Double?

    /*
     * 刻意没有 `trainedDays(f,t) = COUNT(DISTINCT date)`：打卡天数由已聚合的
     * perDay 行数派生（评审 I-2①），两者对 GROUP BY date 严格等价，
     * 再查一次既浪费又引入与柱状图不同的快照。
     */

    /** 规格 4.6：汇总口径要注明"含 n 条未计算记录" */
    @Query("SELECT COUNT(*) FROM training_record WHERE calories IS NULL AND date BETWEEN :f AND :t")
    suspend fun uncalculated(f: Int, t: Int): Int

    @Query(
        """SELECT date, COUNT(*) AS segments, SUM(durationMin) AS minutes
           FROM training_record WHERE date BETWEEN :f AND :t
           GROUP BY date ORDER BY date"""
    )
    suspend fun perDay(f: Int, t: Int): List<PerDayRow>

    /** 日历页随写入自动刷新的版本（规格 4.6 / Task 14 的 GridKey 驱动） */
    @Query(
        """SELECT date, COUNT(*) AS segments, SUM(durationMin) AS minutes
           FROM training_record WHERE date BETWEEN :f AND :t
           GROUP BY date ORDER BY date"""
    )
    fun observeRangeRaw(f: Int, t: Int): Flow<List<PerDayRow>>

    /**
     * 排序追加 typeId 做稳定 tiebreaker（评审 M-3）：
     * 两个类型分钟数与段数都相等时（同一默认 60 分钟、各 1 段很常见），
     * 没有全序保证则 Top 6 / "其他" 的切分边界与图例顺序会随写入抖动。
     */
    @Query(
        """SELECT t.id AS typeId, t.name AS name, t.color AS color,
                  SUM(r.durationMin) AS minutes, COUNT(*) AS segments
           FROM training_record r JOIN exercise_type t ON t.id = r.exerciseTypeId
           WHERE r.date BETWEEN :f AND :t
           GROUP BY t.id ORDER BY minutes DESC, segments DESC, typeId ASC"""
    )
    suspend fun perType(f: Int, t: Int): List<PerTypeRow>

    /** 雷达图数据源：一条记录多部位则各计 1 段（规格 4.6） */
    @Query(
        """SELECT bp.id AS partId, bp.name AS name, COUNT(*) AS segments
           FROM record_body_part rb
           JOIN training_record r ON r.id = rb.recordId
           JOIN body_part bp ON bp.id = rb.bodyPartId
           WHERE r.date BETWEEN :f AND :t
           GROUP BY bp.id ORDER BY segments DESC"""
    )
    suspend fun perPart(f: Int, t: Int): List<PerPartRow>

    /** 日历分页器的最早可翻月份依据（规格 P1） */
    @Query("SELECT MIN(date) FROM training_record")
    suspend fun oldestDay(): Int?

    // ---------- 全生命周期总账（P3 页脚）----------

    @Query("SELECT COUNT(DISTINCT date) FROM training_record")
    suspend fun lifetimeDays(): Int

    @Query("SELECT COUNT(*) FROM training_record")
    suspend fun lifetimeSegments(): Int

    @Query("SELECT COALESCE(SUM(durationMin), 0) FROM training_record")
    suspend fun lifetimeMinutes(): Int
}
