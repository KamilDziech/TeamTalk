package com.ekotak.teamtalk.presentation.crm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

/**
 * Błędy CRM po polsku. Blokady walidacyjne board360 wracają jako 422 z ciałem
 * `{message, missing[]}` — bez rozpakowania `missing` użytkownik dostałby samo
 * „nie udało się", a to właśnie ta lista mówi, czego brakuje na karcie.
 */
private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun crmErrorMessage(e: Throwable, fallback: String): String = when (e) {
    is HttpException -> when (e.code()) {
        401 -> "Sesja wygasła — zaloguj się ponownie"
        403 -> "Brak uprawnień do CRM"
        404 -> "Rekord nie istnieje lub nie należy do Twojej organizacji"
        422 -> unprocessableMessage(e) ?: "Operacja odrzucona przez serwer"
        in 500..599 -> "Błąd serwera (${e.code()}) — spróbuj ponownie"
        else -> "$fallback (kod ${e.code()})"
    }
    is java.io.IOException -> "Brak połączenia z serwerem"
    else -> e.message ?: fallback
}

/** Ciało 422: `{"message": "...", "missing": ["..."]}` albo samo `{"message": "..."}`. */
private fun unprocessableMessage(e: HttpException): String? {
    val raw = try {
        e.response()?.errorBody()?.string()
    } catch (_: Exception) {
        null
    } ?: return null

    val body = try {
        errorJson.parseToJsonElement(raw) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return null

    val message = (body["message"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val missing = (body["missing"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        .orEmpty()

    return when {
        missing.isNotEmpty() ->
            (message ?: "Brakuje danych do zmiany etapu") +
                "\nUzupełnij w panelu: " + missing.joinToString(", ")
        message != null -> message
        else -> null
    }
}
