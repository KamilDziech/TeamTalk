package com.ekotak.teamtalk.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ekotak.teamtalk.data.local.dao.*
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
     * Baza jest cache'em, więc podniesienie wersji kasuje ją zamiast pisać
     * migrację. UWAGA przy kolejnej zmianie schematu: od wersji 6 leży tu też
     * kolejka niewysłanych zmian zadań (`task_mutations`) — tego skasować nie
     * wolno bez zastanowienia, bo to jedyna kopia decyzji zrobionych offline.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TeamTalkDatabase =
        Room.databaseBuilder(context, TeamTalkDatabase::class.java, "teamtalk.db")
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
}
