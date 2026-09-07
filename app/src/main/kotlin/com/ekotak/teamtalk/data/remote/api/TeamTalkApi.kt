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

    // ── Pliki deala (zakładka „Pliki") ────────────────────────────────────────
    // Odczyt i pobranie: `crm.view`; wgranie, usunięcie, zmiana sekcji i zapis
    // przygotowania rzutu: `deal.manage`. Treść trzyma MinIO po stronie API,
    // tutaj lecą metadane i strumienie.

    @GET("api/deals/{id}/documents")
    suspend fun getDealDocuments(@Path("id") dealId: String): List<DealDocumentDto>

    /**
     * Wgranie pliku (pole `file`, sekcja w polu `category`). Limit board360:
     * 25 MB. Bez `category` serwer wybiera sekcję sam po nazwie pliku — panel
     * nazywa to „automatycznie (wykryj sekcję)".
     */
    @Multipart
    @POST("api/deals/{id}/documents")
    suspend fun uploadDealDocument(
        @Path("id") dealId: String,
        @Part file: MultipartBody.Part,
        @Part("category") category: okhttp3.RequestBody? = null,
    ): DealDocumentDto

    @PATCH("api/documents/{id}")
    suspend fun setDocumentCategory(
        @Path("id") id: String,
        @Body body: JsonObject,
    ): DealDocumentDto

    /**
     * Przygotowanie rzutu (skala + obrysy pomieszczeń). Ciało jako `JsonObject`,
     * bo `{"planData": null}` KASUJE przygotowanie i musi dojść jako jawny
     * `null`, a nie jako brak pola.
     */
    @PATCH("api/documents/{id}/plan-data")
    suspend fun setDocumentPlanData(
        @Path("id") id: String,
        @Body body: JsonObject,
    ): DealDocumentDto

    /** Treść pliku. `@Streaming` — rzut z aparatu potrafi mieć kilkanaście MB. */
    @Streaming
    @GET("api/documents/{id}")
    suspend fun downloadDocument(@Path("id") id: String): ResponseBody

    /** Czy plik jest PDF-em i ile ma stron — pasek miniatur pyta o to najpierw. */
    @GET("api/documents/{id}/preview")
    suspend fun getDocumentPreview(@Path("id") id: String): DocumentPreviewDto

    /**
     * Strona PDF-a jako JPEG. Telefon woli renderować strony u siebie
     * (`PdfRenderer`, działa bez zasięgu), ale gdy PDF-a nie ma jeszcze
     * w pamięci podręcznej, jedna strona z serwera jest tańsza niż całość.
     */
    @Streaming
    @GET("api/documents/{id}/preview/{page}")
    suspend fun getDocumentPage(
        @Path("id") id: String,
        @Path("page") page: Int,
    ): ResponseBody

    @DELETE("api/documents/{id}")
    suspend fun deleteDocument(@Path("id") id: String)

    // ── Audyty deala (zakładka „Audyt") ───────────────────────────────────────
    // Jeden endpoint obsługuje dwie rzeczy naraz: audyty Heizlast i formularz
    // audytu instalacji — rozróżnia je `formData.kind`. Odczyt wymaga
    // `crm.view`, zapis `deal.manage`. Zapis formularza dla deala z podpisaną
    // umową API odrzuca (409), dopóki ciało nie niesie `zmianaOferty: true`.

    @GET("api/deals/{id}/audits")
    suspend fun getDealAudits(@Path("id") id: String): List<AuditDto>

    /**
     * Ciało jako `JsonObject` z tego samego powodu co przy `PATCH` deala:
     * formularz audytu musi umieć wysłać jawnego `null`-a, żeby skasować
     * odpowiedź na pytanie warunkowe, które przestało być widoczne.
     */
    @POST("api/deals/{id}/audits")
    suspend fun createDealAudit(
        @Path("id") id: String,
        @Body body: JsonObject,
    ): AuditDto

    @PATCH("api/audits/{id}")
    suspend fun updateAudit(
        @Path("id") id: String,
        @Body body: JsonObject,
    ): AuditDto

    /**
     * Umowy deala — telefon czyta je WYŁĄCZNIE po to, żeby wiedzieć, czy
     * oferta jest już zamknięta podpisem (wtedy audyt jest do odczytu).
     * Zarządzanie umowami zostaje w panelu.
     */
    @GET("api/deals/{id}/contracts")
    suspend fun getDealContracts(@Path("id") id: String): List<ContractSummaryDto>

    // ── Zamówienia deala (zakładka „Zamówienie") ──────────────────────────────
    // Oferty czyta każdy z `crm.view`; zamówienia — także do odczytu — wymagają
    // `order.manage`, bo panel trzyma je pod jednym uprawnieniem z zapisem.
    // Zakładka radzi sobie z odmową każdego z tych odczytów osobno.

    /** Oferty deala — z wygranych zakłada się zamówienie. */
    @GET("api/deals/{id}/offers")
    suspend fun getDealOffers(@Path("id") id: String): List<OfferDto>

    @GET("api/deals/{id}/orders")
    suspend fun getDealOrders(@Path("id") id: String): List<OrderDto>

    /** Zamówienie z wygranej oferty (FR-14). Treść pozycji przepisuje serwer. */
    @POST("api/deals/{id}/orders")
    suspend fun createDealOrder(
        @Path("id") id: String,
        @Body request: OrderCreateRequest,
    ): OrderDto

    /**
     * Ptaszek „zamówione" / „odebrane" przy pozycji. Odpowiedzią jest CAŁE
     * zamówienie z przeliczonym statusem nagłówka, nie sama pozycja.
     */
    @PATCH("api/orders/{id}/items/{itemId}")
    suspend fun updateOrderItem(
        @Path("id") orderId: String,
        @Path("itemId") itemId: String,
        @Body request: OrderItemPatchRequest,
    ): OrderDto

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

    // ── Cennik zakładki „Oferta" ──────────────────────────────────────────────
    // Kwoty oferty składamy na telefonie z tych samych trzech źródeł, co panel:
    // kartoteki Magazynu (`getProducts`), ustawień firmowych i zestawów Warunków
    // finansowych. Rachunek jest w `domain/ufh` — tu idą surowe dane.

    /**
     * Ustawienie firmowe: producent szafek (`ufh.cabinetBrand`), materiały
     * domyślne technologii (`tech.defaults.underfloor`) i narzut węzła
     * (`price.markup.<categoryId>`). Odczyt ma każda zalogowana osoba; nieznany
     * klucz API odrzuca (400), więc trzymamy się whitelisty panelu.
     */
    @GET("api/organization/settings/{key}")
    suspend fun getOrgSetting(@Path("key") key: String): OrgSettingDto

    /**
     * Zestawy Warunków finansowych — cennik montażowy („Montaż", „Biuro")
     * i kwotowy („Koszty z ręki"). Wymaga `financial.terms.view`; odmowa znaczy
     * ofertę bez kwot, a nie brak zakładki.
     */
    @GET("api/financial-terms/schemes")
    suspend fun getFinancialSchemes(): List<FinancialSchemeDto>

    // ── Rozliczenia deala (zakładka „Rozliczenie") ────────────────────────────
    // Odczyt migawek jest tak samo szeroki jak odczyt zestawów
    // (`financial.terms.view`); zatwierdzanie i cofanie wymaga
    // `financial.terms.manage` — odmowa (403) to nie brak sieci, więc leci dalej.

    @GET("api/financial-terms/settlements/{dealId}")
    suspend fun getDealSettlements(@Path("dealId") dealId: String): List<DealSettlementDto>

    /**
     * Zatwierdzenie zamraża wynik policzony przez telefon. Ciało jako
     * `JsonObject`, bo `breakdown` to migawka rozbicia o kształcie ustalanym
     * przez wzór punktowy, a nie stała struktura DTO.
     */
    @PUT("api/financial-terms/settlements/{dealId}/{categoryId}")
    suspend fun approveDealSettlement(
        @Path("dealId") dealId: String,
        @Path("categoryId") categoryId: String,
        @Body body: JsonObject,
    ): DealSettlementDto

    @DELETE("api/financial-terms/settlements/{dealId}/{categoryId}")
    suspend fun revokeDealSettlement(
        @Path("dealId") dealId: String,
        @Path("categoryId") categoryId: String,
    )

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
     * Zdjęcie profilowe członka zespołu — to samo źródło, z którego korzysta
     * panel (`/api/users/:id/avatar`, kontroler `TeamAvatarController`).
     * Odczyt wymaga tylko zalogowania. Brak zdjęcia = 404, nie pusta odpowiedź.
     */
    @GET("api/users/{id}/avatar")
    @Streaming
    suspend fun getUserAvatar(@Path("id") id: String): ResponseBody

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

    /**
     * Lista projektów. Domyślnie aktywne bez szablonów — tyle wystarczy krokowi
     * „kogo dotyczy" w kreatorze zadania. Moduł Projekty woła ją z `status = null`,
     * żeby dostać także pomysły z Poczekalni i projekty przed decyzją.
     */
    @GET("api/projects")
    suspend fun getProjects(
        @Query("status") status: String? = "active",
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

    /** Pojedyncze zlecenie — wejście w kartę z powiadomienia o SLA. */
    @GET("api/service-jobs/{id}")
    suspend fun getServiceJob(@Path("id") id: String): ServiceJobResponseDto

    /** Nowe zgłoszenie / przegląd. Wymaga `service.manage`. */
    @POST("api/service-jobs")
    suspend fun createServiceJob(@Body body: ServiceJobCreateDto): ServiceJobResponseDto

    /**
     * Zmiana zlecenia. Ciało budujemy jako `JsonObject` (`buildServiceJobPatch`)
     * — API rozróżnia brak pola od jawnego `null` (np. zdjęcie serwisanta).
     */
    @PATCH("api/service-jobs/{id}")
    suspend fun updateServiceJob(
        @Path("id") id: String,
        @Body patch: JsonObject,
    ): ServiceJobResponseDto

    /** Serwisanci — przypisanie zlecenia i filtr osoby. */
    @GET("api/technicians")
    suspend fun getTechnicians(): List<TechnicianDto>

    /** Karty przeglądów gwarancyjnych (Panasonic). */
    @GET("api/warranty-cards")
    suspend fun getWarrantyCards(): List<WarrantyCardDto>

    /** Nowa karta gwarancyjna. */
    @POST("api/warranty-cards")
    suspend fun createWarrantyCard(@Body body: WarrantyCardCreateDto): WarrantyCardDto

    /** Zmiana pól karty (producent, status, notatka, numery seryjne). */
    @PATCH("api/warranty-cards/{id}")
    suspend fun updateWarrantyCard(
        @Path("id") id: String,
        @Body patch: JsonObject,
    ): WarrantyCardDto

    /**
     * Zapis pozycji harmonogramu (rok 1..5) — trasa jest upsertem po `ordinal`,
     * więc odpowiada całą kartą z przeliczonymi licznikami.
     */
    @PUT("api/warranty-cards/{id}/inspections")
    suspend fun upsertWarrantyInspection(
        @Path("id") id: String,
        @Body body: JsonObject,
    ): WarrantyCardDto

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

    // ── Email (hub Komunikacja → poczta) ──────────────────────────────────────
    // Odczyt chodzi pod `crm.view`, wysyłka i zmiany pod `deal.manage` — tak
    // samo jak w panelu. To, co widać w skrzynce, NIE zależy jednak od tych
    // uprawnień, tylko od pary (skrzynka, widok):
    //
    //  • `accountId` wybiera skrzynkę z `GET /api/email/accounts`; brak =
    //    pierwsza z listy, czyli firmowa,
    //  • `scope` to widok skrzynki firmowej: `mine` (wycinek opiekuna —
    //    domyślny dla KAŻDEGO) albo `all` (cała skrzynka). `all` bez
    //    uprawnienia `email.view_all` kończy się kodem 403, więc telefon
    //    pokazuje przełącznik dopiero przy `canViewAll = true`.
    //
    // Wątek spoza wycinka oddaje 404, a nie 403 — sama odpowiedź nie ma
    // zdradzać, że w cudzej skrzynce leży wątek o tym identyfikatorze.

    @GET("api/email/accounts")
    suspend fun getEmailAccounts(): List<EmailAccountDto>

    @GET("api/email/folders")
    suspend fun getEmailFolders(
        @Query("accountId") accountId: String? = null,
        @Query("scope") scope: String? = null,
    ): List<EmailFolderCountDto>

    @GET("api/email/labels")
    suspend fun getEmailLabels(): List<EmailLabelDto>

    @GET("api/email/threads")
    suspend fun getEmailThreads(
        @Query("accountId") accountId: String? = null,
        @Query("scope") scope: String? = null,
        @Query("folder") folder: String? = null,
        @Query("q") q: String? = null,
    ): List<EmailThreadDto>

    /** Korespondencja jednego deala — ze wszystkich folderów i obu skrzynek. */
    @GET("api/email/threads")
    suspend fun getEmailThreadsForDeal(@Query("dealId") dealId: String): List<EmailThreadDto>

    /** Otwarcie wątku oznacza go na serwerze jako przeczytany. */
    @GET("api/email/threads/{id}")
    suspend fun getEmailThread(@Path("id") id: String): EmailThreadDetailDto

    /**
     * Zmiana wątku: gwiazdka, przeczytane, folder, etykiety, dowiązanie do
     * deala. Ciało jako `JsonObject` (`buildEmailThreadPatch`) — `dealId: null`
     * ODPINA wątek od karty, a data class z `explicitNulls = false` nie umie
     * takiego nulla wysłać.
     */
    @PATCH("api/email/threads/{id}")
    suspend fun patchEmailThread(
        @Path("id") id: String,
        @Body body: JsonObject,
    )

    /** Do kosza, a gdy wątek już w koszu — trwale. */
    @DELETE("api/email/threads/{id}")
    suspend fun deleteEmailThread(@Path("id") id: String)

    @POST("api/email/messages")
    suspend fun sendEmail(@Body body: EmailSendDto): EmailMessageDto

    @POST("api/email/drafts")
    suspend fun saveEmailDraft(@Body body: EmailSendDto): EmailMessageDto

    @Multipart
    @POST("api/email/messages/{id}/attachments")
    suspend fun uploadEmailAttachment(
        @Path("id") messageId: String,
        @Part file: MultipartBody.Part,
    ): EmailAttachmentDto

    @GET("api/email/attachments/{id}")
    suspend fun downloadEmailAttachment(@Path("id") id: String): ResponseBody

    @GET("api/email/deal-options")
    suspend fun getEmailDealOptions(@Query("q") q: String? = null): List<EmailDealOptionDto>

    // ── Urlop (moduł HR → zakładka „Urlop") ───────────────────────────────────
    // Własny urlop chodzi pod `hr.view` — ma je każdy pracownik. Kartoteki
    // kadrowe siedzą za `hr.manage` i telefon ich NIE dotyka (ustalenie
    // 2026-09-06): stąd brak `PATCH /hr/employees/{id}` w tym interfejsie.
    //
    // Dwie odpowiedzi błędu, które ekran obsługuje po swojemu:
    //  • 409 — okres nachodzi na inny wniosek tej samej osoby,
    //  • 422 — rodzaj urlopu niedostępny przy tej umowie (poza umową o pracę
    //    zostaje sam „bezpłatny").

    @GET("api/hr/me")
    suspend fun getHrDashboard(): HrDashboardDto

    @POST("api/hr/leave")
    suspend fun createLeaveRequest(@Body body: LeaveCreateDto): LeaveRequestDto

    /** Edycja własnego wniosku — cofa go do akceptacji. Ciało jak przy tworzeniu. */
    @PATCH("api/hr/leave/{id}")
    suspend fun updateLeaveRequest(
        @Path("id") id: String,
        @Body body: LeaveCreateDto,
    ): LeaveRequestDto

    @POST("api/hr/leave/{id}/cancel")
    suspend fun cancelLeaveRequest(@Path("id") id: String): LeaveRequestDto

    /**
     * Skrzynka zwierzchnika. Trasa dopisana do board360 pod `hr.view`, bo
     * `GET /hr/overview` wymaga `hr.manage`, którego koordynator — zwierzchnik
     * montażu i serwisu — nie ma. Zwraca WYŁĄCZNIE wnioski podwładnych
     * pytającego oraz te, w których zastępuje nieobecnego zwierzchnika.
     */
    @GET("api/hr/leave/inbox")
    suspend fun getLeaveInbox(): LeaveInboxDto

    @POST("api/hr/leave/{id}/decision")
    suspend fun decideLeaveRequest(
        @Path("id") id: String,
        @Body body: LeaveDecisionDto,
    ): LeaveRequestDto

    /**
     * Nieobecności zespołu — tło kalendarza urlopowego i oś czasu. Druga trasa
     * dopisana do board360: sam fakt nieobecności (osoba, zakres, status), bez
     * rodzaju urlopu i powodu, więc może chodzić pod `hr.view`. Przez
     * `hr/overview` podać się tego nie da — tam idą pełne dane kadrowe.
     */
    @GET("api/hr/absences")
    suspend fun getLeaveAbsences(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<LeaveAbsenceDto>

    /** Liczniki i wnioski całego zespołu — tylko dla kadr (`hr.manage`). */
    @GET("api/hr/overview")
    suspend fun getHrOverview(): HrOverviewDto

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

    // ── Magazyn (kafelek „Magazyn", etap E1 — odczyt) ────────────────────────────

    /**
     * Cała kartoteka magazynu. Trasa nie ma dziś ani filtrów, ani stronicowania,
     * ani znacznika zmiany — bierzemy komplet i trzymamy go w Room. Dopisanie
     * `?updatedSince=` po stronie board360 zamieni to na dociąganie różnicy
     * (patrz `design/mockups/modul-magazyn.html`, sekcja API).
     */
    @GET("api/products")
    suspend fun getProducts(): List<ProductDto>

    /**
     * Rezerwacje materiału pod klientów — z pokryciem policzonym przez API.
     * Bez `status` API oddaje SAME AKTYWNE; `status=all` dokłada historię
     * (wydane, zwolnione), której potrzebuje karta deala. `dealId` zawęża do
     * jednego deala — tak czyta tę listę zakładka „Zamówienie".
     */
    @GET("api/inventory/reservations")
    suspend fun getInventoryReservations(
        @Query("status") status: String? = null,
        @Query("dealId") dealId: String? = null,
    ): List<ReservationDto>

    /**
     * Zapotrzebowanie zakupowe. `status=open` = pozycje w obiegu
     * (`to_order` + `ordered`) — tyle wystarczy licznikom „w drodze"
     * i „do zamówienia" przy stanie pozycji; `all` = także propozycje
     * i pozycje przyjęte, po których karta deala poznaje, że brak jest już
     * załatwiony. `dealId` zawęża do zakupów pod jednego deala.
     */
    @GET("api/inventory/orders")
    suspend fun getInventoryOrders(
        @Query("status") status: String = "open",
        @Query("dealId") dealId: String? = null,
    ): List<PurchaseOrderItemDto>

    /**
     * Braki rezerwacji na listę zakupową magazynu — ta sama droga, co „ZAMÓW"
     * w Produktach panelu. Wymaga `inventory.manage`.
     */
    @POST("api/inventory/orders")
    suspend fun createPurchaseOrderItem(
        @Body request: PurchaseOrderCreateRequest,
    ): PurchaseOrderItemDto

    /** Korekta linii rezerwacji: „Wydane" / „Zwolnij" / „Przywróć". */
    @PATCH("api/inventory/reservations/{id}")
    suspend fun updateReservation(
        @Path("id") id: String,
        @Body request: ReservationPatchRequest,
    ): ReservationDto

    // ── Projekty (moduł „Projekt" board360) ─────────────────────────────────────

    /** Karta projektu jednym strzałem: kamienie, zadania i zespół. */
    @GET("api/projects/{id}")
    suspend fun getProject(@Path("id") id: String): ProjectDetailDto

    /**
     * Domknięcie zadania z podaniem czasu („ile zajęło?"). Pominięcie godzin jest
     * dozwolone — wtedy rozliczenie liczy zadanie po estymacie.
     */
    @POST("api/projects/tasks/{taskId}/close")
    suspend fun closeProjectTask(
        @Path("taskId") taskId: String,
        @Body body: TaskCloseDto,
    ): ProjectTaskDto

    /** Pomysł do Poczekalni — najkrótsza droga z telefonu do modułu. */
    @POST("api/projects")
    suspend fun createIdea(@Body body: IdeaCreateDto): ProjectDto

    /**
     * Projekty przypięte do deala — zakładka „Harmonogram" karty. Osobna trasa,
     * a nie filtr po `GET /api/projects`: board360 nie przyjmuje `dealId` jako
     * parametru listy, a archiwalne projekty deala mają tu wrócić razem z
     * aktywnymi (`ProjectsController.byDeal`).
     */
    @GET("api/projects/by-deal/{dealId}")
    suspend fun getDealProjects(@Path("dealId") dealId: String): List<ProjectDto>

    /** Nowy projekt pod dealem — odpowiednik „+ Projekt" z panelu. */
    @POST("api/projects")
    suspend fun createDealProject(@Body body: DealProjectCreateDto): ProjectDto

    // ── Szkolenia (kafelek „Szkolenia" = zakładka HR → Szkolenia w panelu) ──────
    // Trasy pracownika stoją za `training.view`, które ma KAŻDA rola — serwisant
    // i montaż też, więc token mobilny wystarcza i po stronie board360 nic nie
    // trzeba było dopisywać. Zarządzanie katalogiem (`training.manage`) zostaje
    // w panelu, patrz `design/mockups/modul-szkolenia.html`.

    /** Szkolenia przypisane zalogowanemu, ze stanem wykonania i ważnością. */
    @GET("api/training/my")
    suspend fun getMyTrainings(): List<MyAssignmentDto>

    /**
     * Materiał lekcji + pytania BEZ klucza odpowiedzi. Gdy lekcja nie jest
     * zalogowanemu przypisana, API oddaje 403 — to stan ekranu („szkolenie
     * niedostępne"), a nie awaria.
     */
    @GET("api/training/lessons/{id}/play")
    suspend fun getPlayableLesson(@Path("id") id: String): PlayableLessonDto

    /** Odpowiedzi do oceny; wynik i próg liczy serwer. */
    @POST("api/training/assignments/{id}/submit")
    suspend fun submitTraining(
        @Path("id") assignmentId: String,
        @Body request: SubmitTrainingRequest,
    ): GradeResultDto

    /** Certyfikat PDF — wyłącznie dla zaliczonego szkolenia. */
    @Streaming
    @GET("api/training/lessons/{id}/certificate")
    suspend fun downloadTrainingCertificate(@Path("id") id: String): ResponseBody

    /**
     * „Moje poziomy" — wymogi i stan zalogowanego w domenach umiejętności.
     * Trasa spoza modułu szkoleń (`skill-catalog`), dostępna każdemu
     * zalogowanemu, i jako jedyna tutaj pakuje odpowiedź w `{data:...}`.
     */
    @GET("api/domain-skills/me")
    suspend fun getMySkills(): MySkillsEnvelopeDto
}
