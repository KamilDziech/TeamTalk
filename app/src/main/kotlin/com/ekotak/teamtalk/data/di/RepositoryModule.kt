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
    abstract fun bindClientRepository(impl: ClientRepositoryImpl): ClientRepository

    @Binds @Singleton
    abstract fun bindCallLogRepository(impl: CallLogRepositoryImpl): CallLogRepository

    @Binds @Singleton
    abstract fun bindVoiceReportRepository(impl: VoiceReportRepositoryImpl): VoiceReportRepository

    @Binds @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository
}
