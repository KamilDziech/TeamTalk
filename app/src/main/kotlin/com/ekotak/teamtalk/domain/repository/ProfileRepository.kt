package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    /** Stream of profiles, backed by local cache. */
    fun getProfiles(
        idEq: String? = null,
        isAdminEq: Boolean? = null,
    ): Flow<List<Profile>>

    suspend fun getProfileById(id: String): Profile

    suspend fun updateProfile(
        id: String,
        displayName: String? = null,
        isAdmin: Boolean? = null,
    ): Profile

    /** Creates the profile or updates display_name if it already exists. */
    suspend fun upsertProfile(id: String? = null, displayName: String? = null): Profile
}
