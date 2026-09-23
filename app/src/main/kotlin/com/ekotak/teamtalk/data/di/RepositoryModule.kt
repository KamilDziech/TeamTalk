package com.ekotak.teamtalk.data.di

import com.ekotak.teamtalk.data.repository.*
import com.ekotak.teamtalk.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds each domain repository interface to its data-layer implementation.
 * Implementations are created in Module 6.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindBriefingRepository(impl: BriefingRepositoryImpl): BriefingRepository

    @Binds @Singleton
    abstract fun bindClientRepository(impl: ClientRepositoryImpl): ClientRepository

    @Binds @Singleton
    abstract fun bindAssistantRepository(impl: AssistantRepositoryImpl): AssistantRepository

    @Binds @Singleton
    abstract fun bindCallLogRepository(impl: CallLogRepositoryImpl): CallLogRepository

    @Binds @Singleton
    abstract fun bindVoiceReportRepository(impl: VoiceReportRepositoryImpl): VoiceReportRepository

    @Binds @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds @Singleton
    abstract fun bindDealRepository(impl: DealRepositoryImpl): DealRepository

    @Binds @Singleton
    abstract fun bindCrmDirectoryRepository(
        impl: CrmDirectoryRepositoryImpl,
    ): CrmDirectoryRepository

    @Binds @Singleton
    abstract fun bindLeadIntakeRepository(impl: LeadIntakeRepositoryImpl): LeadIntakeRepository

    @Binds @Singleton
    abstract fun bindKnowledgeArticleRepository(
        impl: KnowledgeArticleRepositoryImpl,
    ): KnowledgeArticleRepository

    @Binds @Singleton
    abstract fun bindDealCommsRepository(impl: DealCommsRepositoryImpl): DealCommsRepository

    @Binds @Singleton
    abstract fun bindDiscussionRepository(impl: DiscussionRepositoryImpl): DiscussionRepository

    @Binds @Singleton
    abstract fun bindMapRepository(impl: MapRepositoryImpl): MapRepository

    @Binds @Singleton
    abstract fun bindServiceRepository(impl: ServiceRepositoryImpl): ServiceRepository

    @Binds @Singleton
    abstract fun bindCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository

    @Binds @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds @Singleton
    abstract fun bindMemberRepository(impl: MemberRepositoryImpl): MemberRepository

    @Binds @Singleton
    abstract fun bindAuditRepository(impl: AuditRepositoryImpl): AuditRepository

    @Binds @Singleton
    abstract fun bindInventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds @Singleton
    abstract fun bindOrderRepository(impl: OrderRepositoryImpl): OrderRepository

    @Binds @Singleton
    abstract fun bindOfferPricingRepository(
        impl: OfferPricingRepositoryImpl,
    ): OfferPricingRepository

    @Binds @Singleton
    abstract fun bindTrainingRepository(impl: TrainingRepositoryImpl): TrainingRepository

    @Binds @Singleton
    abstract fun bindLeaveRepository(impl: LeaveRepositoryImpl): LeaveRepository

    @Binds @Singleton
    abstract fun bindLeadRepository(impl: LeadRepositoryImpl): LeadRepository

    @Binds @Singleton
    abstract fun bindEmailRepository(impl: EmailRepositoryImpl): EmailRepository

    @Binds @Singleton
    abstract fun bindDealDocumentRepository(
        impl: DealDocumentRepositoryImpl,
    ): DealDocumentRepository

    @Binds @Singleton
    abstract fun bindSettlementRepository(
        impl: SettlementRepositoryImpl,
    ): SettlementRepository

    @Binds @Singleton
    abstract fun bindContractRepository(
        impl: ContractRepositoryImpl,
    ): ContractRepository

    @Binds @Singleton
    abstract fun bindInvoiceRepository(
        impl: InvoiceRepositoryImpl,
    ): InvoiceRepository

    @Binds @Singleton
    abstract fun bindMontazRepository(
        impl: MontazRepositoryImpl,
    ): MontazRepository

    /** Moduł Montaż (kafelek „Montaże") — wyjazdy, teczka i protokół odbioru. */
    @Binds @Singleton
    abstract fun bindMontazJobRepository(
        impl: MontazJobRepositoryImpl,
    ): MontazJobRepository
}
