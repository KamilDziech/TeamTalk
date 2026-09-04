package com.ekotak.teamtalk.data.remote.api

import com.ekotak.teamtalk.data.remote.dto.*
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.*

interface TeamTalkApi {

    // ── Auth (board360) ────────────────────────────────────────────────────────
    // Logowanie mobilne board360 zwraca podpisany token sesji (cookie b360_session).
    // Brak refresh/register/logout po stronie API: wylogowanie = wyczyszczenie tokenu
    // lokalnie, konta zakłada panel web board360.

    @POST("api/auth/mobile-login")
    suspend fun mobileLogin(@Body request: MobileLoginRequest): MobileLoginResponseDto

    @GET("api/me")
    suspend fun getMe(): MobileUserDto

    // ── Clients (kartoteka) ────────────────────────────────────────────────────
    // Odczyt wymaga `crm.view`, tworzenie i edycja `deal.manage`, anonimizacja
    // `settings.company`. Scalanie: admin albo właściciel szansy (waliduje API).

    @GET("api/clients")
    suspend fun getClients(@Query("q") q: String? = null): List<ClientResponseDto>

    @GET("api/clients/{id}")
    suspend fun getClientById(@Path("id") id: String): ClientResponseDto

    @POST("api/clients")
    suspend fun createClient(@Body request: CreateClientRequest): ClientResponseDto

    /**
     * Częściowa aktualizacja danych klienta. Ciało budujemy jako `JsonObject`
     * (`buildClientPatch`) — API rozróżnia brak pola od jawnego `null`.
     */
    @PATCH("api/clients/{id}")
    suspend fun updateClient(
        @Path("id") id: String,
        @Body patch: JsonObject,
    ): ClientResponseDto

    /** Scalenie duplikatów (`sourceIds`) w klienta `:id`. Nie da się cofnąć. */
    @POST("api/clients/{id}/merge")
    suspend fun mergeClients(
        @Path("id") id: String,
        @Body request: MergeClientsRequest,
    ): ClientResponseDto

    /** Anonimizacja danych osobowych (RODO) — rekord zostaje, dane znikają. */
    @POST("api/clients/{id}/erase")
    suspend fun eraseClient(@Path("id") id: String): ClientResponseDto

    /** Q&A na podstawie notatek i komunikacji ze wszystkich deali klienta. */
    @POST("api/clients/{id}/assistant")
    suspend fun askClientAssistant(
        @Path("id") id: String,
        @Body request: ClientAssistantRequest,
    ): ClientAssistantReplyDto

    // ── Deals (CRM / lejek sprzedaży) ──────────────────────────────────────────
    // Odczyt wymaga `crm.view`, zmiany `deal.manage` (RBAC egzekwuje API).
    // Uwaga: lista NIE zwraca klienta — dane klienta doklejamy z `api/clients`.

    @GET("api/deals")
    suspend fun getDeals(
        @Query("stage") stage: String? = null,
        @Query("overdue") overdue: String? = null,
    ): List<DealResponseDto>

    @GET("api/deals/{id}")
    suspend fun getDealById(@Path("id") id: String): DealResponseDto

    /** Przejście etapu. 422 z `{message, missing[]}` przy blokadzie walidacyjnej. */
    @POST("api/deals/{id}/stage")
    suspend fun changeDealStage(
        @Path("id") id: String,
        @Body request: ChangeStageRequest,
    ): DealResponseDto

    /**
     * Częściowa aktualizacja karty. Ciało budujemy jako `JsonObject`
     * (`buildDealPatch`), bo API rozróżnia brak pola od jawnego `null`,
     * a wspólny `Json` aplikacji ma `explicitNulls = false`.
     */
    @PATCH("api/deals/{id}")
    suspend fun updateDeal(
        @Path("id") id: String,
        @Body patch: JsonObject,
    ): DealResponseDto

    // ── Kontakty towarzyszące deala (mąż/żona itp.) ────────────────────────────
    // Główny kontakt to `Deal.clientId`; poniższe endpointy zarządzają wyłącznie
    // dodatkowymi. Każdy kontakt jest osobnym rekordem kartoteki — odpięcie go
    // od deala nie usuwa klienta.

    @GET("api/deals/{id}/contacts")
    suspend fun getDealCompanions(@Path("id") id: String): List<ClientResponseDto>

    /** Dopina istniejącego klienta; odpowiedź to pełna lista po zmianie. */
    @POST("api/deals/{id}/contacts")
    suspend fun addDealCompanion(
        @Path("id") id: String,
        @Body request: DealContactRequest,
    ): List<ClientResponseDto>

    /** Zamiana głównego kontaktu z towarzyszącym. Odpowiedź: `{ok: true}`. */
    @PATCH("api/deals/{id}/contacts/primary")
    suspend fun setPrimaryDealContact(
        @Path("id") id: String,
        @Body request: DealContactRequest,
    ): JsonObject

    /** Odpięcie kontaktu towarzyszącego (204 — bez ciała odpowiedzi). */
    @DELETE("api/deals/{id}/contacts/{clientId}")
    suspend fun removeDealCompanion(
        @Path("id") id: String,
        @Path("clientId") clientId: String,
    )

    /** Q&A ograniczone do komunikacji w TYM dealu (grounding na jednej karcie). */
    @POST("api/deals/{id}/assistant")
    suspend fun askDealAssistant(
        @Path("id") id: String,
        @Body request: ClientAssistantRequest,
    ): ClientAssistantReplyDto

    /** Migawki instalacji karty deala — wybór klienta per etap, z dziedziczeniem. */
    @GET("api/deals/{id}/installations")
    suspend fun getDealInstallations(@Path("id") id: String): DealInstallationsDto

    /**
     * Nadpisanie migawki jednego etapu. Ciało niesie PEŁNY wybór po zmianie —
     * API nie scala list, tylko podmienia. Wymaga `deal.manage`.
     */
    @PUT("api/deals/{id}/installations/{stage}")
    suspend fun setDealInstallations(
        @Path("id") id: String,
        @Path("stage") stage: String,
        @Body request: SetInstallationsRequest,
    ): DealInstallationsDto

    // ── Artykuł wiedzy per instalacja (zakładka „LEAD") ────────────────────────
    // Odczyt: zalogowany. Generowanie: `deal.manage`, a dodatkowo bramka etapu
    // i danych budynku — niespełniona wraca jako 422 z wyjaśnieniem po polsku.

    @GET("api/deals/{dealId}/knowledge-articles")
    suspend fun getKnowledgeArticles(@Path("dealId") dealId: String): List<KnowledgeArticleDto>

    @GET("api/deals/{dealId}/knowledge-articles/gate")
    suspend fun getKnowledgeArticleGate(@Path("dealId") dealId: String): ArticleGateDto

    @POST("api/deals/{dealId}/knowledge-articles/generate")
    suspend fun generateKnowledgeArticle(
        @Path("dealId") dealId: String,
        @Body request: GenerateArticleRequest,
    ): KnowledgeArticleDto

    /**
     * Wiadomość wychodząca na wątku WhatsApp deala. Poza oknem 24h od ostatniej
     * wiadomości klienta API odrzuca treść free-form (422) — to reguła WhatsApp
     * Business, nie nasza walidacja.
     */
    @POST("api/deals/{id}/whatsapp")
    suspend fun sendDealWhatsapp(
        @Path("id") id: String,
        @Body request: SendWhatsappRequest,
    ): ResponseBody

    // ── Leadownia (zakładka „LEAD" karty deala) ───────────────────────────────
    // Zgłoszenie z publicznej leadowni cennikinstalacji.pl. Deal spoza leadowni
    // nie ma rekordu — API odpowiada wtedy 200 z PUSTYM ciałem, którego konwerter
    // JSON nie umie zamienić na obiekt. Dlatego oba endpointy zwracają surowe
    // `ResponseBody`, a parsowanie (i decyzję „puste = brak zgłoszenia") robi
    // `LeadIntakeRepositoryImpl`. Odczyt wymaga `crm.view`, zapis `deal.manage`.

    @GET("api/intake/deal/{dealId}/lead")
    suspend fun getLeadIntake(@Path("dealId") dealId: String): ResponseBody

    @PATCH("api/intake/deal/{dealId}/lead/note")
    suspend fun updateLeadNote(
        @Path("dealId") dealId: String,
        @Body body: JsonObject,
    ): ResponseBody

    // ── Dane pochodne lejka (do kartoteki) ─────────────────────────────────────

    /** Instalacje bieżące per deal: dealId → lista id kategorii głównych. */
    @GET("api/deals/installations/current")
    suspend fun getCurrentInstallations(): Map<String, List<String>>

    /** Wszystkie kontakty towarzyszące w organizacji (deale wspólne). */
    @GET("api/deals/contacts")
    suspend fun getDealContacts(): List<DealContactLinkDto>

    /** Wartości (brutto) deali: dealId → kwota. */
    @GET("api/offers/deal-values")
    suspend fun getDealValues(): Map<String, Double>

    /** Katalog technologii — kategorie główne dają nazwy instalacji. */
    @GET("api/categories")
    suspend fun getCategories(): List<CategoryDto>

    // ── Call logs ─────────────────────────────────────────────────────────────

    @POST("api/call-logs")
    suspend fun createCallLogs(@Body request: List<CreateCallLogRequest>): List<CallLogResponseDto>

    @GET("api/call-logs")
    suspend fun getCallLogs(
        @Query("since") since: String? = null,
        @Query("limit") limit: Int? = null,
    ): List<CallLogResponseDto>

    // ── Voice reports ───────────────────────────────────────────────────────────

    @GET("api/voice-reports")
    suspend fun getVoiceReports(
        @Query("since") since: String? = null,
        @Query("limit") limit: Int? = null,
    ): List<VoiceReportResponseDto>

    @POST("api/voice-reports")
    suspend fun createVoiceReport(@Body request: CreateVoiceReportRequest): VoiceReportResponseDto

    @Multipart
    @POST("api/voice-reports/{id}/recording")
    suspend fun uploadRecording(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
    ): VoiceReportResponseDto

    // ── Devices ─────────────────────────────────────────────────────────────────

    @POST("api/devices")
    suspend fun upsertDevice(@Body request: UpsertDeviceRequest): DeviceResponseDto

    // ── Tasks ─────────────────────────────────────────────────────────────────────

    @GET("api/tasks/members")
    suspend fun getTaskMembers(): List<TaskMemberDto>

    /**
     * Zadania zespołu. Filtry wykonawcy i statusu robimy lokalnie na pobranej
     * liście (przełączanie chipów bez okrążenia po sieci), więc bez parametrów
     * — z serwera bierzemy całość raz i trzymamy w cache Room.
     */
    @GET("api/tasks")
    suspend fun getTasks(
        @Query("status") status: String? = null,
        @Query("assignee") assignee: String? = null,
    ): List<TaskResponseDto>

    @POST("api/tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): TaskResponseDto

    /**
     * Jedno zadanie po id — karta otwiera się także z powiadomienia i z
     * odnośnika w dyskusji, więc nie da się jej złożyć z pozycji listy.
     */
    @GET("api/tasks/{id}")
    suspend fun getTask(@Path("id") id: String): TaskResponseDto

    /**
     * Zmiana pól zadania. Ciało jako `JsonObject` (`buildTaskPatch`), bo API
     * rozróżnia brak pola od jawnego `null`.
     */
    @PATCH("api/tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: String,
        @Body patch: JsonObject,
    ): TaskResponseDto

    /** Usunięcie zadania (menu karty). Odpowiedź bez ciała — 204. */
    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String)

    /** Zadania jednego deala — zakładka „Zadania" karty klienta (wchodzi w E2). */
    @GET("api/deals/{id}/tasks")
    suspend fun getDealTasks(@Path("id") dealId: String): List<TaskResponseDto>

    /** Zadanie pod dealem — tak wiąże się zadanie z klientem (Task nie ma `clientId`). */
    @POST("api/deals/{id}/tasks")
    suspend fun createDealTask(
        @Path("id") dealId: String,
        @Body request: CreateTaskRequest,
    ): TaskResponseDto

    /** Zadanie w projekcie. Wymaga uprawnienia `projects.manage`. */
    @POST("api/projects/{id}/tasks")
    suspend fun createProjectTask(
        @Path("id") projectId: String,
        @Body request: CreateTaskRequest,
    ): TaskResponseDto

    /** Lista projektów do kroku „kogo dotyczy". Domyślnie tylko aktywne, bez szablonów. */
    @GET("api/projects")
    suspend fun getProjects(
        @Query("status") status: String = "active",
        @Query("templates") templates: String = "0",
    ): List<ProjectDto>

    // ── Komentarze zadania i Komunikator wewnętrzny ───────────────────────────
    // Wywołanie kogoś przez @ w komentarzu wciąga zadanie do jego skrzynki, a
    // odpowiedź ze skrzynki wraca jako komentarz pod zadaniem — to jeden wątek,
    // nie dwa (ustalenia 2026-09-01, `docs/tasks/wywolanie-w-komentarzu.md`).

    @GET("api/tasks/{id}/comments")
    suspend fun getTaskComments(@Path("id") taskId: String): List<TaskCommentDto>

    @POST("api/tasks/{id}/comments")
    suspend fun addTaskComment(
        @Path("id") taskId: String,
        @Body request: AddCommentRequest,
    ): TaskCommentDto

    // ── Załączniki karty zadania ──────────────────────────────────────────────

    @GET("api/tasks/{id}/attachments")
    suspend fun getTaskAttachments(@Path("id") taskId: String): List<TaskAttachmentDto>

    /** Wgranie pliku (pole `file`). Limit po stronie board360: 25 MB. */
    @Multipart
    @POST("api/tasks/{id}/attachments")
    suspend fun uploadTaskAttachment(
        @Path("id") taskId: String,
        @Part file: MultipartBody.Part,
    ): TaskAttachmentDto

    /**
     * Treść pliku. `@Streaming`, bo załącznikiem bywa zdjęcie z montażu —
     * nie ma powodu trzymać go w pamięci w całości.
     */
    @Streaming
    @GET("api/task-attachments/{id}")
    suspend fun downloadTaskAttachment(@Path("id") id: String): ResponseBody

    @DELETE("api/task-attachments/{id}")
    suspend fun deleteTaskAttachment(@Path("id") id: String)

    // ── Serwis i przeglądy (moduł Mapa: widoki „Serwisy" i „Przeglądy") ──────────

    /**
     * Zlecenia serwisowe. Bez filtrów — mapa i tak potrzebuje kompletu, żeby
     * policzyć chipy statusów, a filtrowanie robimy lokalnie na cache.
     */
    @GET("api/service-jobs")
    suspend fun getServiceJobs(): List<ServiceJobResponseDto>

    /** Serwisanci — filtr osoby w widokach serwisowych. */
    @GET("api/technicians")
    suspend fun getTechnicians(): List<TechnicianDto>

    /** Karty przeglądów gwarancyjnych (Panasonic). */
    @GET("api/warranty-cards")
    suspend fun getWarrantyCards(): List<WarrantyCardDto>

    /**
     * Współrzędne kart ze snapshotu geokodera. Osobna trasa, bo karta trzyma
     * adres jako wolny tekst — snapshot jest wspólny z panelem.
     */
    @GET("api/warranty-cards/geo")
    suspend fun getWarrantyCardsGeo(): List<WarrantyGeoDto>

    /** Podpowiedzi miejscowości do filtra „lokalizacja" na mapie. */
    @GET("api/geo/suggest")
    suspend fun suggestPlaces(@Query("q") query: String): List<PlaceSuggestionDto>

    // ── Kalendarz ─────────────────────────────────────────────────────────────
    // Odczyt i zapis pod jednym uprawnieniem `calendar.view` — o tym, czy wolno
    // pisać, decyduje poziom dostępu do KALENDARZA (`effectiveLevel`), a nie rola.
    // Serie rozwija serwer: lista zwraca gotowe wystąpienia z `recurrenceGroupId`.

    @GET("api/calendars")
    suspend fun getCalendars(): List<CalendarDto>

    @POST("api/calendars")
    suspend fun createCalendar(@Body body: CalendarCreateDto): CalendarDto

    /** Nazwa, kolor, opis. Ciało jako `JsonObject` — API rozróżnia brak pola od `null`. */
    @PATCH("api/calendars/{id}")
    suspend fun updateCalendar(
        @Path("id") id: String,
        @Body patch: JsonObject,
    ): CalendarDto

    /** 204 — bez ciała odpowiedzi. */
    @POST("api/calendars/{id}/archive")
    suspend fun archiveCalendar(@Path("id") id: String)

    @POST("api/calendars/{id}/restore")
    suspend fun restoreCalendar(@Path("id") id: String)

    /**
     * Wydarzenia zakresu. Filtry (warstwy, osoba) robimy lokalnie na cache —
     * z serwera bierzemy komplet zakresu raz, żeby przełączanie warstw
     * nie kosztowało okrążenia po sieci.
     */
    @GET("api/calendar/events")
    suspend fun getCalendarEvents(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("assignee") assignee: String? = null,
        @Query("calendarIds") calendarIds: String? = null,
    ): List<CalendarEventDto>

    /** `allowConflict=true` wymusza zapis mimo zajętego zasobu (odpowiedź 409). */
    @POST("api/calendar/events")
    suspend fun createCalendarEvent(
        @Body body: CalendarEventCreateDto,
        @Query("allowConflict") allowConflict: String? = null,
    ): CalendarEventDto

    @PATCH("api/calendar/events/{id}")
    suspend fun updateCalendarEvent(
        @Path("id") id: String,
        @Body patch: JsonObject,
        @Query("scope") scope: String = "this",
        @Query("allowConflict") allowConflict: String? = null,
    ): CalendarEventDto

    /** 204 — bez ciała odpowiedzi. */
    @DELETE("api/calendar/events/{id}")
    suspend fun deleteCalendarEvent(
        @Path("id") id: String,
        @Query("scope") scope: String = "this",
    )

    /** Odpowiedź uczestnika. 204 — stan wydarzenia dokładamy w cache sami. */
    @POST("api/calendar/events/{id}/rsvp")
    suspend fun setCalendarRsvp(
        @Path("id") id: String,
        @Body body: RsvpRequest,
    )

    /** Zajętość osób (bez treści wydarzeń) — ekran „Znajdź termin". */
    @GET("api/calendar/events/freebusy")
    suspend fun getFreeBusy(
        @Query("userIds") userIds: String,
        @Query("from") from: String,
        @Query("to") to: String,
    ): List<FreeBusyUserDto>

    /** Czy mogę zaplanować MIMO prywatnej zajętości (`calendar.override_busy`). */
    @GET("api/calendar/private-link")
    suspend fun getPrivateLinkState(): PrivateLinkStateDto

    /**
     * Szare pola „Zajęte" z PRYWATNYCH kalendarzy zespołu (podpięty adres iCal).
     * Z serwera przychodzi wyłącznie osoba i przedział czasu — żadnych tytułów
     * ani opisów, bo board360 w ogóle ich nie zapisuje. Podpina się w panelu;
     * telefon tylko pokazuje zajętość i respektuje jej blokadę (409).
     */
    @GET("api/calendar/events/private-busy")
    suspend fun getPrivateBusy(
        @Query("from") from: String,
        @Query("to") to: String,
    ): List<PrivateBusyDto>

    /** Nakładki operacyjne: montaże, serwis, flota, urlopy… Tylko do podglądu. */
    @GET("api/calendar/events/overlays")
    suspend fun getCalendarOverlays(
        @Query("from") from: String,
        @Query("to") to: String,
    ): List<CalendarOverlayDto>

    /** Skrzynka: dyskusje, w których bierzemy udział (wywołani albo pisaliśmy). */
    @GET("api/discussions")
    suspend fun getDiscussions(): List<DiscussionSummaryDto>

    /** Sam licznik — tyle wystarczy plakietce i robotnikowi powiadomień. */
    @GET("api/discussions/unread-count")
    suspend fun getDiscussionsUnreadCount(): UnreadCountDto

    @GET("api/discussions/{taskId}")
    suspend fun getDiscussionThread(@Path("taskId") taskId: String): DiscussionThreadDto

    @POST("api/discussions/{taskId}/read")
    suspend fun markDiscussionRead(@Path("taskId") taskId: String)

    @POST("api/discussions/{taskId}/comments")
    suspend fun addDiscussionComment(
        @Path("taskId") taskId: String,
        @Body request: AddCommentRequest,
    ): DiscussionCommentDto
}
