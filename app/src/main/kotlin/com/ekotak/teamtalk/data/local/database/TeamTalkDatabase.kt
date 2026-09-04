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
        MapPointEntity::class,
        CalendarEntity::class,
        CalendarEventEntity::class,
        CalendarMemberEntity::class,
        CalendarMutationEntity::class,
        CalendarBusyEntity::class,
    ],
    version = 10,
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
    abstract fun mapPointDao(): MapPointDao
    abstract fun calendarDao(): CalendarDao
    abstract fun calendarMutationDao(): CalendarMutationDao
}
