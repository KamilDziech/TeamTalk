package com.ekotak.teamtalk.data.remote.api

import com.ekotak.teamtalk.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface TeamTalkApi {

    // ── Auth ─────────────────────────────────────────────────────────────────

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponseDto

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponseDto

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): RefreshResponseDto

    @POST("api/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    @GET("api/auth/me")
    suspend fun getMe(): UserResponseDto

    // ── Clients ───────────────────────────────────────────────────────────────

    @GET("api/clients")
    suspend fun getClients(
        @Query("phone_eq") phoneEq: String? = null,
        @Query("id_in")    idIn: String? = null,
        @Query("order")    order: String? = null,
        @Query("limit")    limit: Int? = null,
    ): List<ClientResponseDto>

    @GET("api/clients/{id}")
    suspend fun getClientById(@Path("id") id: String): ClientResponseDto

    @POST("api/clients")
    suspend fun createClient(@Body request: CreateClientRequest): ClientResponseDto

    @PUT("api/clients/{id}")
    suspend fun updateClient(
        @Path("id") id: String,
        @Body request: UpdateClientRequest,
    ): ClientResponseDto

    @DELETE("api/clients/{id}")
    suspend fun deleteClient(@Path("id") id: String): Response<Unit>

    // ── Call Logs ─────────────────────────────────────────────────────────────

    @GET("api/call-logs")
    suspend fun getCallLogs(
        @Query("status_eq")       statusEq: String? = null,
        @Query("status_neq")      statusNeq: String? = null,
        @Query("type_neq")        typeNeq: String? = null,
        @Query("type_neq2")       typeNeq2: String? = null,
        @Query("client_id_eq")    clientIdEq: String? = null,
        @Query("caller_phone_eq") callerPhoneEq: String? = null,
        @Query("status_in")       statusIn: String? = null,
        @Query("timestamp_gte")   timestampGte: String? = null,
        @Query("timestamp_lte")   timestampLte: String? = null,
        @Query("embed")           embed: String? = null,
        @Query("limit")           limit: Int? = null,
    ): List<CallLogResponseDto>

    @POST("api/call-logs")
    suspend fun createCallLog(@Body request: CreateCallLogRequest): CallLogResponseDto

    @PUT("api/call-logs/{id}")
    suspend fun updateCallLog(
        @Path("id") id: String,
        @Body request: UpdateCallLogRequest,
    ): CallLogResponseDto

    @POST("api/call-logs/rpc/append-unique-recipient")
    suspend fun appendUniqueRecipient(@Body request: AppendRecipientRequest): Response<Unit>

    // ── Voice Reports ─────────────────────────────────────────────────────────

    @GET("api/voice-reports")
    suspend fun getVoiceReports(
        @Query("call_log_id_in") callLogIdIn: String? = null,
        @Query("call_log_id_eq") callLogIdEq: String? = null,
    ): List<VoiceReportResponseDto>

    @POST("api/voice-reports")
    suspend fun createVoiceReport(@Body request: CreateVoiceReportRequest): VoiceReportResponseDto

    // ── Profiles ──────────────────────────────────────────────────────────────

    @GET("api/profiles")
    suspend fun getProfiles(
        @Query("id_eq")       idEq: String? = null,
        @Query("is_admin_eq") isAdminEq: Boolean? = null,
    ): List<ProfileResponseDto>

    @GET("api/profiles/{id}")
    suspend fun getProfileById(@Path("id") id: String): ProfileResponseDto

    @PUT("api/profiles/{id}")
    suspend fun updateProfile(
        @Path("id") id: String,
        @Body request: UpdateProfileRequest,
    ): ProfileResponseDto

    @POST("api/profiles")
    suspend fun upsertProfile(@Body request: UpsertProfileRequest): ProfileResponseDto

    // ── Devices ───────────────────────────────────────────────────────────────

    @GET("api/devices")
    suspend fun getDevices(
        @Query("push_token_neq") pushTokenNeq: String? = null,
    ): List<DeviceResponseDto>

    @POST("api/devices")
    suspend fun upsertDevice(@Body request: UpsertDeviceRequest): DeviceResponseDto

    @PUT("api/devices/last-active")
    suspend fun updateDeviceLastActive(@Body request: UpdateLastActiveRequest): Response<Unit>

    // ── Storage ───────────────────────────────────────────────────────────────

    @Multipart
    @POST("api/storage/voice-reports")
    suspend fun uploadAudio(@Part file: MultipartBody.Part): StorageUploadResponseDto

    // ── Functions ─────────────────────────────────────────────────────────────

    @Multipart
    @POST("api/functions/transcribe-audio")
    suspend fun transcribeAudio(@Part file: MultipartBody.Part): TranscriptionResponseDto
}
