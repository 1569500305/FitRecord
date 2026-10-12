package com.fitrecord.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room schema v1 —— 逐字对应规格 §4.2。
 * 日期一律存 epochDay: Int（避免字符串排序与时区歧义），颜色存 32 位 ARGB 的 Long 位模式。
 * 刻意放在一个文件：这些实体通过外键互相引用，拆散反而看不清关系（台账 P11）。
 */

@Entity(
    tableName = "exercise_type",
    // 类型名唯一由数据库守住（规格 6.3）；UI 层的 TypeValidator 只是提前给提示
    indices = [Index(value = ["name"], unique = true)],
)
data class ExerciseTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val met: Double,
    val color: Long,
    val isBuiltin: Boolean,
    /** 软删除：被历史记录引用的类型只能归档（规格 P7） */
    val archived: Boolean = false,
    /** 用户改过内置类型的 MET 时置 true，P7 显示"已修改"并允许恢复默认 */
    val metOverridden: Boolean = false,
    val sortOrder: Int = 0,
    /** 逗号分隔的 body_part.id，P5 选类型后自动带出的默认部位 */
    val defaultBodyPartIds: String = "",
)

@Entity(tableName = "body_part")
data class BodyPartEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "training_record",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseTypeId"],
            onDelete = ForeignKey.RESTRICT,   // 有记录引用的类型不得被删（规格 P7 / 6.4）
        ),
    ],
    indices = [Index("date"), Index("exerciseTypeId")],
)
data class TrainingRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LocalDate.toEpochDay().toInt() */
    val date: Int,
    val exerciseTypeId: Long,
    val durationMin: Int,
    /** null = 保存时未填体重（规格 6.1），统计汇总按 null 跳过 */
    val calories: Double?,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "record_body_part",
    primaryKeys = ["recordId", "bodyPartId"],
    foreignKeys = [
        ForeignKey(
            entity = TrainingRecordEntity::class,
            parentColumns = ["id"], childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BodyPartEntity::class,
            parentColumns = ["id"], childColumns = ["bodyPartId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bodyPartId")],
)
data class RecordBodyPartEntity(
    val recordId: Long,
    val bodyPartId: Long,
)

/** 当日级备注（与每条记录各自的 note 区分） */
@Entity(tableName = "daily_note")
data class DailyNoteEntity(
    @PrimaryKey val date: Int,
    val text: String,
    val updatedAt: Long,
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    /** 单行表，永远只有 id = 0 这一行 */
    @PrimaryKey val id: Int = 0,
    val gender: String? = null,
    val age: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double,
    /** 规格 9：V1 只存不显示，为 V1.1 的目标进度环预留 */
    val weeklyGoal: Int = 0,
    val updatedAt: Long,
)

@Entity(tableName = "food_item")
data class FoodItemEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val emoji: String,
    /** 计量单位名，如"碗米饭"，渲染时拼在数值后（规格 4.4） */
    val unitLabel: String,
    val kcalPerUnit: Double,
)

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
