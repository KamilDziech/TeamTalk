package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.ProfileDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.UpdateProfileRequest
import com.ekotak.teamtalk.data.remote.dto.UpsertProfileRequest
import com.ekotak.teamtalk.domain.model.Profile
import com.ekotak.teamtalk.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val profileDao: ProfileDao,
) : ProfileRepository {

    override fun getProfiles(idEq: String?, isAdminEq: Boolean?): Flow<List<Profile>> = channelFlow {
        val localFlow = when {
            isAdminEq == true -> profileDao.observeAdmins()
            idEq != null      -> profileDao.observeById(idEq).map { e -> listOfNotNull(e) }
            else              -> profileDao.observeAll()
        }

        launch { localFlow.map { it.map { e -> e.toDomain() } }.collect(::send) }

        try {
            val fresh = api.getProfiles(idEq = idEq, isAdminEq = isAdminEq)
            profileDao.upsertAll(fresh.map { it.toEntity() })
        } catch (_: Exception) {}
    }

    override suspend fun getProfileById(id: String): Profile {
        return try {
            val dto = api.getProfileById(id)
            profileDao.upsert(dto.toEntity())
            dto.toDomain()
        } catch (e: Exception) {
            profileDao.getById(id)?.toDomain() ?: throw e
        }
    }

    override suspend fun updateProfile(id: String, displayName: String?, isAdmin: Boolean?): Profile {
        val dto = api.updateProfile(id, UpdateProfileRequest(displayName, isAdmin))
        profileDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun upsertProfile(id: String?, displayName: String?): Profile {
        val dto = api.upsertProfile(UpsertProfileRequest(id, displayName))
        profileDao.upsert(dto.toEntity())
        return dto.toDomain()
    }
}
