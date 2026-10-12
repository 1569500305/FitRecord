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
 * **幂等**：只在对应表为空时写入，因此重复调用、导入后重启、崩溃重试都安全。
 */
class SeedRepository(
    private val typeDao: TypeDao,
    private val foodDao: FoodDao,
    private val bodyPartDao: BodyPartDao,
) {

    suspend fun ensureSeeded() {
        if (typeDao.all().isEmpty()) {
            BuiltinExercises.items.forEachIndexed { index, item ->
                typeDao.insert(
                    ExerciseTypeEntity(
                        name = item.name,
                        met = item.met,
                        color = ExercisePalette.colorForId(item.colorIndex.toLong()).toRgbLong(),
                        isBuiltin = true,
                        sortOrder = index,
                        defaultBodyPartIds = item.defaultBodyPartIds,
                    )
                )
            }
        }
        if (bodyPartDao.all().isEmpty()) {
            bodyPartDao.insertAll(BodyParts.items.map { (id, name) -> BodyPartEntity(id, name, id.toInt()) })
        }
        if (foodDao.all().isEmpty()) {
            foodDao.insertAll(Foods.items)
        }
    }
}
