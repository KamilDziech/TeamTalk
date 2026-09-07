package com.ekotak.teamtalk.data.repository

import android.content.Context
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.FinancialSchemeDto
import com.ekotak.teamtalk.domain.ufh.PointRate
import com.ekotak.teamtalk.domain.ufh.PointScheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zestawy punktowe Warunków finansowych — jedno miejsce pobrania i jeden cache
 * dla obu czytelników: zakładki „Oferta" (kwoty dla klienta) i „Rozliczenie"
 * (punkty ekipy). Dwa osobne cache tego samego cennika rozjechałyby się
 * pierwszego dnia, w którym zarząd poprawi stawkę.
 *
 * Cennik to kilka kilobajtów tekstu, więc leży w preferencjach — osobna tabela
 * w bazie nie dałaby nic poza migracją.
 *
 * Odmowa uprawnień (`financial.terms.view`) NIE jest brakiem sieci: wtedy lista
 * jest pusta, a rachunek wychodzi bez stawek, z wypisanymi brakami — dokładnie
 * jak w panelu u osoby bez dostępu do finansów.
 */
@Singleton
class FinancialSchemesStore @Inject constructor(
    @ApplicationContext context: Context,
    private val api: TeamTalkApi,
    private val json: Json,
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Świeże z API; bez zasięgu ostatnie pobranie, przy odmowie pusto. */
    suspend fun schemes(): List<PointScheme> = withContext(Dispatchers.IO) {
        load().map { dto ->
            PointScheme(
                id = dto.id,
                categoryId = dto.categoryId,
                name = dto.name,
                items = dto.items.map {
                    PointRate(
                        id = it.id,
                        name = it.name,
                        unit = it.unit,
                        points = it.points,
                        scheme = dto.name,
                    )
                },
            )
        }
    }

    private suspend fun load(): List<FinancialSchemeDto> = try {
        val fresh = api.getFinancialSchemes()
        prefs.edit().putString(KEY_SCHEMES, json.encodeToString(SCHEMES, fresh)).apply()
        fresh
    } catch (_: IOException) {
        decode(prefs.getString(KEY_SCHEMES, null))
    } catch (_: Exception) {
        emptyList()
    }

    private fun decode(raw: String?): List<FinancialSchemeDto> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching { json.decodeFromString(SCHEMES, raw) }.getOrDefault(emptyList())
    }

    private companion object {
        /** Ten sam plik i klucz, co miała „Oferta" — cache przeżywa aktualizację. */
        const val PREFS = "offer_pricing"
        const val KEY_SCHEMES = "financial.schemes"
        val SCHEMES = ListSerializer(FinancialSchemeDto.serializer())
    }
}
