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
import com.ekotak.teamtalk.data.local.database.MIGRATION_10_11
import com.ekotak.teamtalk.data.local.database.MIGRATION_11_12
import com.ekotak.teamtalk.data.local.database.MIGRATION_12_13
import com.ekotak.teamtalk.data.local.database.MIGRATION_13_14
import com.ekotak.teamtalk.data.local.database.MIGRATION_14_15
import com.ekotak.teamtalk.data.local.database.MIGRATION_15_16
import com.ekotak.teamtalk.data.local.database.MIGRATION_16_17
import com.ekotak.teamtalk.data.local.database.MIGRATION_17_18
import com.ekotak.teamtalk.data.local.database.MIGRATION_18_19
import com.ekotak.teamtalk.data.local.database.MIGRATION_19_20
import com.ekotak.teamtalk.data.local.database.MIGRATION_20_21
import com.ekotak.teamtalk.data.local.database.MIGRATION_21_22
import com.ekotak.teamtalk.data.local.database.MIGRATION_22_23
import com.ekotak.teamtalk.data.local.database.MIGRATION_23_24
import com.ekotak.teamtalk.data.local.database.MIGRATION_24_25
import com.ekotak.teamtalk.data.local.database.MIGRATION_25_26
import com.ekotak.teamtalk.data.local.database.MIGRATION_26_27
import com.ekotak.teamtalk.data.local.database.MIGRATION_27_28
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
            .addMigrations(
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
                MIGRATION_18_19,
                MIGRATION_19_20,
                MIGRATION_20_21,
                MIGRATION_21_22,
                MIGRATION_22_23,
                MIGRATION_23_24,
                MIGRATION_24_25,
                MIGRATION_25_26,
                MIGRATION_26_27,
                MIGRATION_27_28,
            )
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
    @Provides fun provideProjectDao(db: TeamTalkDatabase): ProjectDao             = db.projectDao()
    @Provides fun provideProjectMutationDao(db: TeamTalkDatabase): ProjectMutationDao = db.projectMutationDao()
    @Provides fun provideCalendarDao(db: TeamTalkDatabase): CalendarDao               = db.calendarDao()
    @Provides fun provideCalendarMutationDao(db: TeamTalkDatabase): CalendarMutationDao = db.calendarMutationDao()
    @Provides fun provideMemberDao(db: TeamTalkDatabase): MemberDao                   = db.memberDao()
    @Provides fun provideInventoryDao(db: TeamTalkDatabase): InventoryDao             = db.inventoryDao()
    @Provides fun provideAuditDao(db: TeamTalkDatabase): AuditDao                     = db.auditDao()
    @Provides fun provideOrderDao(db: TeamTalkDatabase): OrderDao                     = db.orderDao()
    @Provides fun provideLeaveDao(db: TeamTalkDatabase): LeaveDao                     = db.leaveDao()
    @Provides fun provideEmailDao(db: TeamTalkDatabase): EmailDao                     = db.emailDao()
    @Provides fun provideDocumentDao(db: TeamTalkDatabase): DocumentDao               = db.documentDao()
    @Provides fun provideSettlementDao(db: TeamTalkDatabase): SettlementDao           = db.settlementDao()
    @Provides fun provideDealDao(db: TeamTalkDatabase): DealDao                       = db.dealDao()
    @Provides fun provideContractDao(db: TeamTalkDatabase): ContractDao               = db.contractDao()
    @Provides fun provideInvoiceDao(db: TeamTalkDatabase): InvoiceDao                 = db.invoiceDao()
}
