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
        ServiceJobEntity::class,
        WarrantyCardEntity::class,
        ServiceClientEntity::class,
        ServiceTechnicianEntity::class,
        ServiceMutationEntity::class,
        CalendarEntity::class,
        CalendarEventEntity::class,
        TeamMemberEntity::class,
        CalendarMutationEntity::class,
        CalendarBusyEntity::class,
        ProductEntity::class,
        ReservationEntity::class,
        PurchaseOrderEntity::class,
        AuditEntity::class,
        CatalogCategoryEntity::class,
        AuditInstallationsEntity::class,
        AuditMutationEntity::class,
        ProjectEntity::class,
        ProjectMilestoneEntity::class,
        ProjectTaskEntity::class,
        ProjectMutationEntity::class,
        DealOrderEntity::class,
        DealOfferEntity::class,
        OrderMutationEntity::class,
        LeaveRequestEntity::class,
        LeaveAbsenceEntity::class,
        LeaveBalanceEntity::class,
        LeaveMutationEntity::class,
        EmailAccountEntity::class,
        EmailFolderCountEntity::class,
        EmailThreadEntity::class,
        EmailMessageEntity::class,
        EmailAttachmentEntity::class,
        EmailLabelEntity::class,
        EmailMutationEntity::class,
        DealDocumentEntity::class,
        DocumentMutationEntity::class,
        DealSettlementEntity::class,
        SettlementMutationEntity::class,
    ],
    version = 20,
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
    abstract fun serviceDao(): ServiceDao
    abstract fun serviceMutationDao(): ServiceMutationDao
    abstract fun calendarDao(): CalendarDao
    abstract fun calendarMutationDao(): CalendarMutationDao
    abstract fun memberDao(): MemberDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun auditDao(): AuditDao
    abstract fun projectDao(): ProjectDao
    abstract fun projectMutationDao(): ProjectMutationDao
    abstract fun orderDao(): OrderDao
    abstract fun leaveDao(): LeaveDao
    abstract fun emailDao(): EmailDao
    abstract fun documentDao(): DocumentDao
    abstract fun settlementDao(): SettlementDao
}
