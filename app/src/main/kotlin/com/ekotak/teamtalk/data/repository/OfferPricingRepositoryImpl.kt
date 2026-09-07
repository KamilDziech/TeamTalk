package com.ekotak.teamtalk.data.repository

import android.content.Context
import com.ekotak.teamtalk.data.local.dao.InventoryDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
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
import com.ekotak.teamtalk.domain.ufh.isCostScheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * ją stamtąd. Ustawienia firmowe to kilka kilobajtów tekstu — trzymamy je
 * w preferencjach, bo osobna tabela w bazie nie dałaby nic poza migracją.
 * Zestawy Warunków finansowych idą przez [FinancialSchemesStore] — ten sam
 * cennik czyta zakładka „Rozliczenie", a dwa cache rozjechałyby się pierwszego
 * dnia, w którym zarząd poprawi stawkę.
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
    private val schemesStore: FinancialSchemesStore,
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
        val schemes = schemesStore.schemes()

        // Zestawy wpięte pod węzeł i jego PRZODKÓW — „Montaż" wisi zwykle pod
        // korzeniem technologii, a formularz audytu bywa dziedziczony niżej.
        val rates = ArrayList<PointRate>()
        val costs = ArrayList<PointRate>()
        for (id in categoryIdPath) {
            for (s in schemes) {
                if (s.categoryId != id) continue
                // Nazwę zestawu każda pozycja niesie już ze sklepu cennika.
                val target = if (isCostScheme(s.name)) costs else rates
                target += s.items
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
        const val MARKUP_MIN_PCT = 10
        const val MARKUP_MAX_PCT = 100
    }
}
