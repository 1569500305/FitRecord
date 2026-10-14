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
 *  - 不覆盖用户改动：三张表均用 IGNORE，已存在行直接跳过；
 *  - 不删行：绝不用 REPLACE，否则 CASCADE 会清空历史部位关联。
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
                    archived = item.archived,
                    sortOrder = index,
                    defaultBodyPartIds = item.defaultBodyPartIds,
                    recordBySets = item.recordBySets,
                )
            }
        )
        // 部位与食物一律用 IGNORE 而不是 REPLACE：REPLACE 会先隐式 DELETE 再插入，
        // 而 record_body_part 对 body_part.id 有 ON DELETE CASCADE，
        // 用 REPLACE 会导致每次冷启动级联清空用户全部部位关联（复审 Critical）。
        bodyPartDao.insertSeedAll(
            BodyParts.items.map { (id, name) -> BodyPartEntity(id, name, id.toInt()) }
        )
        foodDao.insertSeedAll(Foods.items)
    }
}
