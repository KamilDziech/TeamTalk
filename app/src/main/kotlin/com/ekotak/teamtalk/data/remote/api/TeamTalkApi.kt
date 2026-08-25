package com.ekotak.teamtalk.data.remote.api

import com.ekotak.teamtalk.data.remote.dto.*
import okhttp3.MultipartBody
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

    // ── Clients (read-only) ─────────────────────────────────────────────────────

    @GET("api/clients")
    suspend fun getClients(@Query("q") q: String? = null): List<ClientResponseDto>

    @GET("api/clients/{id}")
    suspend fun getClientById(@Path("id") id: String): ClientResponseDto

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

    @POST("api/tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): TaskResponseDto
}
