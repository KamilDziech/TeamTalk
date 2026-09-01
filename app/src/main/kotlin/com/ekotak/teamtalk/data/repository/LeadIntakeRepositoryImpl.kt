package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.LeadIntakeResponseDto
import com.ekotak.teamtalk.data.remote.dto.LeadNoteResponseDto
import com.ekotak.teamtalk.data.remote.dto.buildLeadNoteBody
import com.ekotak.teamtalk.domain.model.LeadIntake
import com.ekotak.teamtalk.domain.repository.LeadIntakeRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import javax.inject.Inject

/**
 * Odczyt i zapis zgłoszenia z leadowni. Bez cache Room: zgłoszenie jest
 * dodatkiem do karty deala, a karta i tak leci z sieci przy każdym wejściu.
 *
 * Oba endpointy potrafią odpowiedzieć 200 z pustym ciałem („deal nie pochodzi
 * z leadowni" — po stronie Nest to `return null`). Konwerter JSON wywróciłby
 * się na takim ciele, więc czytamy `ResponseBody` i parsujemy ręcznie.
 */
class LeadIntakeRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val json: Json,
) : LeadIntakeRepository {

    override suspend fun getLeadIntake(dealId: String): LeadIntake? =
        api.getLeadIntake(dealId)
            .decodeOrNull<LeadIntakeResponseDto>(json)
            ?.toDomain()

    override suspend fun updateNote(dealId: String, note: String?): String? =
        api.updateLeadNote(dealId, buildLeadNoteBody(note))
            .decodeOrNull<LeadNoteResponseDto>(json)
            ?.note
}

/** Puste ciało (albo dosłowne `null`) = brak rekordu, nie błąd parsowania. */
private inline fun <reified T> ResponseBody.decodeOrNull(json: Json): T? {
    val text = use { it.string() }.trim()
    if (text.isEmpty() || text == "null") return null
    return json.decodeFromString<T>(text)
}
