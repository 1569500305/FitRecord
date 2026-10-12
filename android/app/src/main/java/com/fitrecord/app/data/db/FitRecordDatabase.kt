package com.fitrecord.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitrecord.app.data.db.dao.BodyPartDao
import com.fitrecord.app.data.db.dao.FoodDao
import com.fitrecord.app.data.db.dao.MetaDao
import com.fitrecord.app.data.db.dao.NoteDao
import com.fitrecord.app.data.db.dao.ProfileDao
import com.fitrecord.app.data.db.dao.StatsDao
import com.fitrecord.app.data.db.dao.TrainingDao
import com.fitrecord.app.data.db.dao.TypeDao
import com.fitrecord.app.data.db.entity.BodyPartEntity
import com.fitrecord.app.data.db.entity.DailyNoteEntity
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import com.fitrecord.app.data.db.entity.FoodItemEntity
import com.fitrecord.app.data.db.entity.MetaEntity
import com.fitrecord.app.data.db.entity.RecordBodyPartEntity
import com.fitrecord.app.data.db.entity.TrainingRecordEntity
import com.fitrecord.app.data.db.entity.UserProfileEntity

/**
 * Room schema v1。
 * exportSchema = true 且 schemaLocation 已在 build.gradle.kts 配好 ——
 * schemas/ 目录下的 JSON 是迁移测试与"守护 1"的依据，必须纳入版本控制。
 */
@Database(
    entities = [
        ExerciseTypeEntity::class,
        BodyPartEntity::class,
        TrainingRecordEntity::class,
        RecordBodyPartEntity::class,
        DailyNoteEntity::class,
        UserProfileEntity::class,
        FoodItemEntity::class,
        MetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FitRecordDatabase : RoomDatabase() {

    abstract fun typeDao(): TypeDao
    abstract fun bodyPartDao(): BodyPartDao
    abstract fun trainingDao(): TrainingDao
    abstract fun statsDao(): StatsDao
    abstract fun noteDao(): NoteDao
    abstract fun profileDao(): ProfileDao
    abstract fun foodDao(): FoodDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val DB_NAME = "fitrecord.db"

        /** 迁移登记表在 Migrations.kt；V1 为空数组，但链路必须真实存在 */
        val MIGRATIONS: Array<Migration> = Migrations.ALL

        /** 建库时写入创建时间戳。外键约束由 Room 自动开启（PRAGMA foreign_keys = ON），
         *  RESTRICT / CASCADE 是否真生效由 DatabaseSmokeTest 守护。 */
        private val META_STAMP_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL(
                    "INSERT OR REPLACE INTO meta(key, value) VALUES('schema_created_at', " +
                        "strftime('%s','now'))"
                )
            }
        }

        /**
         * name 默认就是生产库名，参数化只为让测试能开一个独立文件走**同一条 builder 链路**
         * （含 addCallback 与 addMigrations），从而验证"重开不丢数据"（评审 I-4）。
         * 不要用 inMemoryDatabaseBuilder 代替：它绕过了 addMigrations，测不到升级行为。
         */
        fun build(context: Context, name: String = DB_NAME): FitRecordDatabase =
            Room.databaseBuilder(context.applicationContext, FitRecordDatabase::class.java, name)
                .addCallback(META_STAMP_CALLBACK)
                .addMigrations(*MIGRATIONS)
                .build()
        // 刻意不调用 fallbackToDestructiveMigration()：升级失败必须显式暴露（全局约束）
    }
}
