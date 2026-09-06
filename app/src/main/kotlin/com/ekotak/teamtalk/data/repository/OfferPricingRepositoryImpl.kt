package com.ekotak.teamtalk.data.repository

import android.content.Context
import com.ekotak.teamtalk.data.local.dao.InventoryDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.FinancialSchemeDto
import com.ekotak.teamtalk.domain.model.Product
import com.ekotak.teamtalk.domain.repository.OfferPricingRepository
import com.ekotak.teamtalk.domain.ufh.DEFAULT_MARKUP_PCT
import com.ekotak.teamtalk.domain.ufh.MaterialDefaults
import com.ekotak.teamtalk.domain.ufh.OfferPricing
import com.ekotak.teamtalk.domain.ufh.PointRate
import com.ekotak.teamtalk.domain.ufh.SavedMarkup
import com.ekotak.teamtalk.domain.ufh.buildCatalog
import com.ekotak.teamtalk.domain.ufh.cabinetBrandOf
import com.ekotak.teamtalk.domain.ufh.hasPriceFormula
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dane cennikowe oferty — sieć z odwrotem do ostatniego pobrania.
 *
 * Kartoteka Magazynu leży już w Room (moduł Magazyn), więc bez zasięgu bierzemy
 * ją stamtąd. Ustawienia firmowe i zestawy Warunków finansowych to kilka
 * kilobajtów tekstu — trzymamy je w preferencjach, bo osobna tabela w bazie nie
 * dałaby nic poza migracją.
 *
 * Odmowa uprawnień (`financial.terms.view`) NIE jest brakiem sieci: wtedy
 * zestawy są puste, oferta pokazuje zakres, a kwoty wychodzą zerowe z wypisanymi
 * brakami — dokładnie jak w panelu u osoby bez dostępu do finansów.
 */
@Singleton
class OfferPricingRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val api: TeamTalkApi,
    private val inventoryDao: InventoryDao,
    private val json: Json,
) : OfferPricingRepository {

    private val prefs = context.getSharedPreferences("offer_pricing", Context.MODE_PRIVATE)

    override suspend fun getPricing(
        categoryId: String,
        categoryName: String,
        categoryIdPath: List<String>,
    ): OfferPricing? = withContext(Dispatchers.IO) {
        // Formuła ceny jest rozpisana wyłącznie dla ogrzewania podłogowego — dla
        // innych instalacji tabela ma pokazać zakres bez kwot, a nie zmyślone ceny.
        if (!hasPriceFormula(categoryName)) return@withContext null

        val products = loadProducts()
        val installProducts = products.filter { it.installation == UNDERFLOOR }
        val defaults = parseDefaults(setting(KEY_TECH_DEFAULTS))
        val brand = cabinetBrandOf(setting(KEY_CABINET_BRAND))
        val markup = parseSavedMarkup(setting("$KEY_MARKUP_PREFIX$categoryId"))
        val schemes = loadSchemes()

        // Zestawy wpięte pod węzeł i jego PRZODKÓW — „Montaż" wisi zwykle pod
        // korzeniem technologii, a formularz audytu bywa dziedziczony niżej.
        val rates = ArrayList<PointRate>()
        val costs = ArrayList<PointRate>()
        for (id in categoryIdPath) {
            for (s in schemes) {
                if (s.categoryId != id) continue
                val target = if (isCostScheme(s.name)) costs else rates
                for (it in s.items) {
                    target += PointRate(
                        id = it.id,
                        name = it.name,
                        unit = it.unit,
                        points = it.points,
                        scheme = s.name,
                    )
                }
            }
        }

        OfferPricing(
            catalog = buildCatalog(products, installProducts, defaults, brand, rates, costs),
            markup = markup,
        )
    }

    /** Kartoteka: świeża z API, a bez zasięgu z cache modułu Magazyn. */
    private suspend fun loadProducts(): List<Product> = try {
        val now = System.currentTimeMillis()
        api.getProducts().map { it.toEntity(now).toDomain() }
    } catch (_: IOException) {
        inventoryDao.getProducts().map { it.toDomain() }
    } catch (_: Exception) {
        inventoryDao.getProducts().map { it.toDomain() }
    }

    /**
     * Ustawienie firmowe. Brak sieci → ostatnia znana wartość; odmowa serwera →
     * brak wpisu (wartości fabryczne), tak samo jak w panelu.
     */
    private suspend fun setting(key: String): String? = try {
        val value = api.getOrgSetting(key).value
        prefs.edit().putString(key, value ?: "").apply()
        value
    } catch (_: IOException) {
        prefs.getString(key, null)?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    private suspend fun loadSchemes(): List<FinancialSchemeDto> = try {
        val fresh = api.getFinancialSchemes()
        prefs.edit().putString(KEY_SCHEMES, json.encodeToString(SCHEMES, fresh)).apply()
        fresh
    } catch (_: IOException) {
        decodeSchemes(prefs.getString(KEY_SCHEMES, null))
    } catch (_: Exception) {
        // 403 = brak `financial.terms.view`. Cennika nie ma, ale zakres oferty
        // pokazujemy dalej — braki wypisze podsumowanie.
        emptyList()
    }

    private fun decodeSchemes(raw: String?): List<FinancialSchemeDto> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching { json.decodeFromString(SCHEMES, raw) }.getOrDefault(emptyList())
    }

    /**
     * Czy zestaw to boks kosztów własnych (kwoty w zł), a nie cennik punktowy —
     * rozpoznajemy po nazwie, 1:1 ze `scheme-kind.ts`.
     */
    private fun isCostScheme(name: String): Boolean {
        val n = name.trim()
        return COST_SCHEME_FULL.containsMatchIn(n) || COST_SCHEME_SHORT.matches(n)
    }

    /** Materiały domyślne technologii: `slot → kod kartoteki`. */
    private fun parseDefaults(raw: String?): MaterialDefaults {
        if (raw.isNullOrBlank()) return emptyMap()
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return emptyMap()
        val out = HashMap<String, String>()
        for ((k, v) in obj) {
            val s = (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            if (!s.isNullOrEmpty()) out[k] = s
        }
        return out
    }

    /**
     * Narzut węzła. Cokolwiek dziwnego w JSON-ie (ręczna edycja, stary format) →
     * wartości fabryczne, nigdy wyjątek: oferta ma się otworzyć zawsze.
     */
    private fun parseSavedMarkup(raw: String?): SavedMarkup {
        if (raw.isNullOrBlank()) return SavedMarkup(DEFAULT_MARKUP_PCT, emptyMap())
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return SavedMarkup(DEFAULT_MARKUP_PCT, emptyMap())
        val base = (obj["base"] as? JsonPrimitive)?.doubleOrNull
        val rows = HashMap<String, Double>()
        (obj["rows"] as? JsonObject)?.forEach { (key, value) ->
            (value as? JsonPrimitive)?.doubleOrNull?.let { rows[key] = it }
        }
        return SavedMarkup(clampBase(base), rows)
    }

    /** Suwak trzymamy w zakresie 10–100 %, tak jak karta formuły w panelu. */
    private fun clampBase(pct: Double?): Int {
        if (pct == null || !pct.isFinite()) return DEFAULT_MARKUP_PCT
        return min(MARKUP_MAX_PCT, max(MARKUP_MIN_PCT, pct.roundToInt()))
    }

    private companion object {
        const val UNDERFLOOR = "underfloor"
        const val KEY_CABINET_BRAND = "ufh.cabinetBrand"
        const val KEY_TECH_DEFAULTS = "tech.defaults.underfloor"
        const val KEY_MARKUP_PREFIX = "price.markup."
        const val KEY_SCHEMES = "financial.schemes"
        const val MARKUP_MIN_PCT = 10
        const val MARKUP_MAX_PCT = 100
        val SCHEMES = ListSerializer(FinancialSchemeDto.serializer())
        val COST_SCHEME_FULL = Regex("koszty\\s+z\\s+r[ęe]ki", RegexOption.IGNORE_CASE)
        val COST_SCHEME_SHORT = Regex("^koszty$", RegexOption.IGNORE_CASE)
    }
}
