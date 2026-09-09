package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.InvoiceDao
import com.ekotak.teamtalk.data.local.entity.DealInvoicesEntity
import com.ekotak.teamtalk.data.local.entity.DealMontazeEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.DealInvoicesDto
import com.ekotak.teamtalk.data.remote.dto.InstallationDto
import com.ekotak.teamtalk.data.remote.dto.KsefInvoiceDto
import com.ekotak.teamtalk.domain.model.DealInvoices
import com.ekotak.teamtalk.domain.model.DealMontaz
import com.ekotak.teamtalk.domain.model.InvoiceMatch
import com.ekotak.teamtalk.domain.model.KsefInvoice
import com.ekotak.teamtalk.domain.model.MontazStatus
import com.ekotak.teamtalk.domain.repository.InvoiceRepository
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zakładka „Faktura" — dwa odczyty pod dwoma uprawnieniami, jeden cache.
 *
 * Faktury i montaże pobieramy OSOBNO i osobno je zapisujemy, bo osobno bywają
 * niedostępne: handlowiec bez `ksef.view` ma dostać montaże i rachunek z umowy,
 * a nie pustą zakładkę z komunikatem o braku dostępu do wszystkiego.
 *
 * Odmowę (403) odróżniamy od braku sieci. Brak sieci = pokazujemy ostatnie
 * pobranie i mówimy, że jest z telefonu; odmowa = mówimy wprost, że tych
 * dokumentów ta sesja nie ogląda. Wrzucenie obu przypadków do jednego worka
 * kończy się tym, że człowiek szuka zasięgu zamiast poprosić o uprawnienie.
 */
@Singleton
class InvoiceRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: InvoiceDao,
    private val json: Json,
) : InvoiceRepository {

    override suspend fun getInvoices(dealId: String): DealInvoices {
        var brakDostepu = false
        var blad: String? = null
        var zSieci = false

        val fresh = try {
            val dto = api.getDealInvoices(dealId)
            dao.upsertInvoices(
                DealInvoicesEntity(
                    dealId = dealId,
                    payload = json.encodeToString(DealInvoicesDto.serializer(), dto),
                    syncedAt = System.currentTimeMillis(),
                ),
            )
            zSieci = true
            dto
        } catch (_: IOException) {
            null
        } catch (e: Exception) {
            // Serwer odpowiedział, więc to nie jest widok „z telefonu".
            zSieci = true
            val kod = (e as? HttpException)?.code()
            // 403 = brak `ksef.view`. Cache czyścimy, bo prawo mogło zostać
            // odebrane, a wtedy stara lista faktur na ekranie byłaby wyciekiem
            // danych, których ta sesja już nie powinna widzieć.
            if (kod == 403) {
                brakDostepu = true
                dao.upsertInvoices(
                    DealInvoicesEntity(
                        dealId = dealId,
                        payload = json.encodeToString(DealInvoicesDto.serializer(), DealInvoicesDto()),
                        syncedAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                blad = when (kod) {
                    404 -> "Board360 nie zna jeszcze tej trasy — faktur deala nie ma skąd wziąć."
                    null -> "Nie udało się odczytać faktur."
                    else -> "Serwer odrzucił odczyt faktur (kod $kod)."
                }
            }
            null
        }

        val cached = fresh ?: dao.getInvoices(dealId)?.let { row ->
            runCatching { json.decodeFromString(DealInvoicesDto.serializer(), row.payload) }.getOrNull()
        }

        val montaze = pobierzMontaze(dealId) { zSieci = zSieci || it }

        return DealInvoices(
            nabywca = cached?.buyer?.label?.takeIf { it.isNotBlank() },
            nabywcaNip = cached?.buyer?.nip?.takeIf { it.isNotBlank() },
            faktury = cached?.invoices.orEmpty().map { it.toDomain() },
            montaze = montaze,
            brakDostepu = brakDostepu,
            bladFaktur = blad,
            fromCache = !zSieci,
        )
    }

    /**
     * Montaże deala. Rezerwacje terminu odsiewamy tak samo jak panel
     * (`listDealInstallations`): zaklepane okno bez obsady to jeszcze nie
     * montaż, a zarządza nim zakładka „Oferta".
     */
    private suspend fun pobierzMontaze(dealId: String, onFresh: (Boolean) -> Unit): List<DealMontaz> {
        val fresh = try {
            val rows = api.getInstallations(dealId)
            dao.upsertMontaze(
                DealMontazeEntity(
                    dealId = dealId,
                    payload = json.encodeToString(
                        ListSerializer(InstallationDto.serializer()),
                        rows,
                    ),
                    syncedAt = System.currentTimeMillis(),
                ),
            )
            onFresh(true)
            rows
        } catch (_: Exception) {
            // Brak sieci ALBO brak `installation.view` — w obu przypadkach
            // zostaje ostatnie pobranie. Montaże to podgląd, nie decyzja, więc
            // rozdzielanie tych dwóch przypadków niczego by tu nie zmieniło.
            dao.getMontaze(dealId)?.let { row ->
                runCatching {
                    json.decodeFromString(
                        ListSerializer(InstallationDto.serializer()),
                        row.payload,
                    )
                }.getOrNull()
            }.orEmpty()
        }

        return fresh
            .map { it.toDomain() }
            .filter { it.status != MontazStatus.RESERVED }
            .sortedBy { it.termin.orEmpty() }
    }

    private fun KsefInvoiceDto.toDomain() = KsefInvoice(
        id = id,
        numerKsef = ksefNumber,
        numer = invoiceNumber,
        dataWystawienia = issueDate,
        wystawca = issuerName,
        nabywca = buyerName,
        nabywcaNip = buyerNip,
        netto = netAmount,
        vat = vatAmount,
        brutto = grossAmount,
        waluta = currency,
        dopasowanie = InvoiceMatch.fromWire(match),
    )

    private fun InstallationDto.toDomain() = DealMontaz(
        id = id,
        termin = scheduledAt,
        status = MontazStatus.fromWire(status),
        trudnosc = difficulty,
        notatka = teamNote,
    )
}
