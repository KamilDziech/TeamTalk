package com.ekotak.teamtalk.domain.repository

/**
 * Wysyłka wiadomości na wątku deala (WhatsApp, FR-22). Osobno od
 * `DealRepository`, bo to inny moduł API i inna reguła dostępu — a jedyny
 * dzisiejszy wywołujący (wysyłka artykułu wiedzy klientowi) nie potrzebuje
 * całej skrzynki, tylko jednego POST-a.
 */
interface DealMessageRepository {
    /**
     * Wiadomość wychodząca o treści `body`. API egzekwuje okno 24h od ostatniej
     * wiadomości klienta — poza oknem odpowiada 422 z wyjaśnieniem, którego
     * ekran nie tłumaczy, tylko pokazuje.
     */
    suspend fun sendWhatsApp(dealId: String, body: String)
}
