package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.MemberDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toTeamMemberEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.repository.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Książka zespołu z cache Room. Pobranie jest miękkie: gdy `/tasks/members`
 * nie odpowie, zostaje to, co już mamy — filtr osoby ma się otworzyć w aucie
 * tak samo jak przy biurku.
 */
@Singleton
class MemberRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: MemberDao,
) : MemberRepository {

    override fun observe(): Flow<List<TaskMember>> =
        dao.observeMembers().map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh() {
        val members = runCatching { api.getTaskMembers() }.getOrNull() ?: return
        dao.replaceMembers(members.map { it.toTeamMemberEntity() })
    }
}
