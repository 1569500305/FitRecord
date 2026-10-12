package com.fitrecord.app.data.repo

import com.fitrecord.app.data.db.dao.BodyPartDao
import com.fitrecord.app.data.db.dao.FoodDao
import com.fitrecord.app.data.db.dao.TypeDao
import com.fitrecord.app.data.db.entity.BodyPartEntity
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import com.fitrecord.app.data.db.seed.BodyParts
import com.fitrecord.app.data.db.seed.BuiltinExercises
import com.fitrecord.app.data.db.seed.Foods
import com.fitrecord.app.ui.theme.ExercisePalette
import com.fitrecord.app.ui.theme.toRgbLong

/**
 * 首次建库时写入内置数据。
 *
 * 策略是**无条件全量写入 + 冲突忽略**，不是"表空才写"（评审 I-5）：
 *  - 幂等：重复调用不产生重复行；
 *  - 可自愈：上次写到一半失败（进程被杀、磁盘满），下次启动会补齐缺的类型；
 *  - 不覆盖用户改动：exercise_type 靠 name 唯一索引 IGNORE 跳过已存在行，
 *    用户改过的 MET 不会被启动打回。
 */
class SeedRepository(
    private val typeDao: TypeDao,
    private val foodDao: FoodDao,
    private val bodyPartDao: BodyPartDao,
) {

    suspend fun ensureSeeded() {
        typeDao.insertSeedAll(
            BuiltinExercises.items.mapIndexed { index, item ->
                ExerciseTypeEntity(
                    name = item.name,
                    met = item.met,
                    // 内置类型按下标显式取色，不做轮询：调色板将来加色时颜色不会静默漂移（评审 M-2）
                    color = ExercisePalette.colors[item.colorIndex].toRgbLong(),
                    isBuiltin = true,
                    sortOrder = index,
                    defaultBodyPartIds = item.defaultBodyPartIds,
                )
            }
        )
        // 部位与食物固定主键 + REPLACE，天然幂等且可自愈；V1 这两张表不可用户编辑
        bodyPartDao.insertAll(
            BodyParts.items.map { (id, name) -> BodyPartEntity(id, name, id.toInt()) }
        )
        foodDao.insertAll(Foods.items)
    }
}
