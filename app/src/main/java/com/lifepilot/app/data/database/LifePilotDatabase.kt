package com.lifepilot.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        TaskEntity::class,
        ReminderConfigEntity::class,
        RecurrenceRuleEntity::class,
        OccurrenceEntity::class,
        ReminderLogEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        DayPlanEntity::class,
    ],
    version = LifePilotDatabase.VERSION,
    exportSchema = true,
)
abstract class LifePilotDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun taskDao(): TaskDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun reminderLogDao(): ReminderLogDao
    abstract fun habitDao(): HabitDao
    abstract fun dayPlanDao(): DayPlanDao

    companion object {
        const val VERSION = 1
        const val NAME = "lifepilot.db"

        /**
         * Versioned migrations. Every schema change must bump [VERSION], add a Migration here and
         * commit the exported schema in app/schemas. There is deliberately NO destructive
         * fallback: user data must never be silently wiped by an upgrade.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        val BUILT_IN_CATEGORIES = listOf(
            CategoryEntity(1, "Work", 0, "work", true, 0),
            CategoryEntity(2, "Learning", 1, "learning", true, 1),
            CategoryEntity(3, "Health & Fitness", 2, "health", true, 2),
            CategoryEntity(4, "Personal", 3, "personal", true, 3),
            CategoryEntity(5, "Family", 4, "family", true, 4),
            CategoryEntity(6, "Finance", 5, "finance", true, 5),
            CategoryEntity(7, "Other", 6, "other", true, 6),
        )
        const val OTHER_CATEGORY_ID = 7L

        fun seedCategories(db: SupportSQLiteDatabase) {
            BUILT_IN_CATEGORIES.forEach {
                db.execSQL(
                    "INSERT OR IGNORE INTO categories (id, name, colorIndex, iconKey, isBuiltIn, sortOrder) VALUES (?, ?, ?, ?, 1, ?)",
                    arrayOf<Any>(it.id, it.name, it.colorIndex, it.iconKey, it.sortOrder),
                )
            }
        }

        private val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) = seedCategories(db)
        }

        fun build(context: Context, name: String? = NAME): LifePilotDatabase {
            val builder = if (name == null) Room.inMemoryDatabaseBuilder(context, LifePilotDatabase::class.java)
            else Room.databaseBuilder(context, LifePilotDatabase::class.java, name)
            return builder
                .addMigrations(*MIGRATIONS)
                .addCallback(seedCallback)
                .build()
        }
    }
}
