package com.ekotak.teamtalk.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ekotak.teamtalk.data.local.dao.*
import com.ekotak.teamtalk.data.local.entity.*

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS client_groups (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                isDefault INTEGER NOT NULL DEFAULT 0,
                createdAt TEXT NOT NULL
            )"""
        )
        db.execSQL("ALTER TABLE clients ADD COLUMN groupId TEXT")
    }
}

@Database(
    entities = [
        UserEntity::class,
        ClientEntity::class,
        ClientGroupEntity::class,
        CallLogEntity::class,
        VoiceReportEntity::class,
        ProfileEntity::class,
        DeviceEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TeamTalkDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun clientDao(): ClientDao
    abstract fun clientGroupDao(): ClientGroupDao
    abstract fun callLogDao(): CallLogDao
    abstract fun voiceReportDao(): VoiceReportDao
    abstract fun profileDao(): ProfileDao
    abstract fun deviceDao(): DeviceDao
}
