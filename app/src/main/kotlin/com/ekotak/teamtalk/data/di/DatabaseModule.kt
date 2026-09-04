package com.ekotak.teamtalk.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ekotak.teamtalk.data.local.dao.*
import com.ekotak.teamtalk.data.local.database.MIGRATION_6_7
import com.ekotak.teamtalk.data.local.database.MIGRATION_7_8
import com.ekotak.teamtalk.data.local.database.MIGRATION_8_9
import com.ekotak.teamtalk.data.local.database.MIGRATION_9_10
import com.ekotak.teamtalk.data.local.database.TeamTalkDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Baza jest cache'em, więc podniesienie wersji zwykle kasuje ją zamiast
     * pisać migrację. UWAGA: od wersji 6 leży tu też kolejka niewysłanych zmian
     * zadań (`task_mutations`) — tego skasować nie wolno bez zastanowienia, bo
     * to jedyna kopia decyzji zrobionych offline. Dlatego wersja 7 (cache mapy)
     * dochodzi migracją, a kasowanie zostaje wyłącznie jako ostatnia deska
     * ratunku dla ścieżek, których nie obsłużyliśmy.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TeamTalkDatabase =
        Room.databaseBuilder(context, TeamTalkDatabase::class.java, "teamtalk.db")
            .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("session") }
        )

    // DAOs — scoped to Singleton because the database is Singleton
    @Provides fun provideUserDao(db: TeamTalkDatabase): UserDao                   = db.userDao()
    @Provides fun provideClientDao(db: TeamTalkDatabase): ClientDao               = db.clientDao()
    @Provides fun provideCallLogDao(db: TeamTalkDatabase): CallLogDao             = db.callLogDao()
    @Provides fun provideVoiceReportDao(db: TeamTalkDatabase): VoiceReportDao     = db.voiceReportDao()
    @Provides fun provideDeviceDao(db: TeamTalkDatabase): DeviceDao               = db.deviceDao()
    @Provides fun provideTaskDao(db: TeamTalkDatabase): TaskDao                   = db.taskDao()
    @Provides fun provideTaskMutationDao(db: TeamTalkDatabase): TaskMutationDao   = db.taskMutationDao()
    @Provides fun provideMapPointDao(db: TeamTalkDatabase): MapPointDao           = db.mapPointDao()
    @Provides fun provideServiceDao(db: TeamTalkDatabase): ServiceDao             = db.serviceDao()
    @Provides fun provideServiceMutationDao(db: TeamTalkDatabase): ServiceMutationDao = db.serviceMutationDao()
    @Provides fun provideCalendarDao(db: TeamTalkDatabase): CalendarDao               = db.calendarDao()
    @Provides fun provideCalendarMutationDao(db: TeamTalkDatabase): CalendarMutationDao = db.calendarMutationDao()
}
