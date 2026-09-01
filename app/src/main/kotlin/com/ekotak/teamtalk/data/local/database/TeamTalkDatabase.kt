package com.ekotak.teamtalk.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ekotak.teamtalk.data.local.dao.*
import com.ekotak.teamtalk.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        ClientEntity::class,
        CallLogEntity::class,
        VoiceReportEntity::class,
        DeviceEntity::class,
        TaskEntity::class,
        TaskMutationEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TeamTalkDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun clientDao(): ClientDao
    abstract fun callLogDao(): CallLogDao
    abstract fun voiceReportDao(): VoiceReportDao
    abstract fun deviceDao(): DeviceDao
    abstract fun taskDao(): TaskDao
    abstract fun taskMutationDao(): TaskMutationDao
}
