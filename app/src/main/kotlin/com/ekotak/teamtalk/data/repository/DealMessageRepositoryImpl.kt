package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.SendWhatsappRequest
import com.ekotak.teamtalk.domain.repository.DealMessageRepository
import javax.inject.Inject

class DealMessageRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
) : DealMessageRepository {

    /**
     * Odpowiedź (zapisana wiadomość) zamykamy bez czytania — wywołującego
     * interesuje wyłącznie to, czy wysyłka przeszła, a skrzynki deala telefon
     * na razie nie pokazuje.
     */
    override suspend fun sendWhatsApp(dealId: String, body: String) {
        api.sendDealWhatsapp(dealId, SendWhatsappRequest(body)).close()
    }
}
