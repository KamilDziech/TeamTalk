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
        ProfileEntity::class,
        DeviceEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TeamTalkDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun clientDao(): ClientDao
    abstract fun callLogDao(): CallLogDao
    abstract fun voiceReportDao(): VoiceReportDao
    abstract fun profileDao(): ProfileDao
    abstract fun deviceDao(): DeviceDao
}
