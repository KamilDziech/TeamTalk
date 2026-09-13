package com.ekotak.teamtalk.domain.montaz

import com.ekotak.teamtalk.domain.model.Audit
import com.ekotak.teamtalk.domain.model.UFH_AUDIT_KIND
import com.ekotak.teamtalk.domain.model.UfhFloor
import com.ekotak.teamtalk.domain.ufh.FloorPipe
import com.ekotak.teamtalk.domain.ufh.PipeSum
import com.ekotak.teamtalk.domain.ufh.TechSection
import com.ekotak.teamtalk.domain.ufh.auditUsable
import com.ekotak.teamtalk.domain.ufh.floorManifolds
import com.ekotak.teamtalk.domain.ufh.sumPipe
import com.ekotak.teamtalk.domain.ufh.technicalSections
import com.ekotak.teamtalk.domain.ufh.ufhFloorPipes
import com.ekotak.teamtalk.domain.ufh.ufhQuoteInput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * CO EKIPA MA WYKONAĆ — rysunki, pętle i parametry dla zakresu montażu. Port
 * `web/src/app/app/crm/montaz-robota.ts`.
 *
 * Zakładka niczego tu nie liczy od nowa: bierze te same rachunki, co audyt
 * i wycena (`domain/ufh`), i zawęża je do węzłów objętych TYM montażem. Dzięki
 * temu ekipa podłogówki nie ogląda parametrów pompy ciepła, a liczby zgadzają
 * się z ofertą co do metra.
 */

/** Kondygnacja w karcie montażu: rysunek + to, co się na nim wykonuje. */
data class MontazFloor(
    val name: String,
    /** Rzut, do którego odnoszą się kropki rozdzielaczy; `null` = brak rysunku. */
    val planDocId: String?,
    val system: String,
    val manifolds: Int,
    val pipe: FloorPipe,
)

/** Jedna instalacja objęta montażem — rysunki, pętle i specyfikacja. */
data class MontazRobota(
    /** Węzeł-właściciel formularza audytu (`formData.categoryId`). */
    val categoryId: String,
    val floors: List<MontazFloor>,
    /** Specyfikacja techniczna w układzie konfiguratora (ta sama, co w ofercie). */
    val tech: List<TechSection>,
    val totals: PipeSum,
    /** Audyt bez metrażu i pomiaru z rzutu — nie ma czego rysować ani liczyć. */
    val pusty: Boolean,
)

/**
 * Robota dla zakresu montażu. Bierzemy audyty ogrzewania podłogowego, których
 * węzeł-właściciel wchodzi w [nodeIds] — a gdy zakres jest pusty (montaż sprzed
 * podziału na zakresy), wszystkie: lepiej pokazać całość niż nic.
 */
fun montazRobota(audits: List<Audit>, nodeIds: List<String>): List<MontazRobota> {
    val wanted = nodeIds.toSet()
    return audits
        .filter { it.formKind == UFH_AUDIT_KIND && it.installationForm != null }
        .mapNotNull { audit ->
            val categoryId = audit.categoryId ?: return@mapNotNull null
            if (wanted.isNotEmpty() && categoryId !in wanted) return@mapNotNull null
            val state = audit.installationForm ?: return@mapNotNull null
            val input = ufhQuoteInput(state)
            val pipes = ufhFloorPipes(state)
            MontazRobota(
                categoryId = categoryId,
                floors = state.floors.mapIndexed { i, f ->
                    MontazFloor(
                        name = f.name.ifBlank { "Kondygnacja ${i + 1}" },
                        planDocId = f.planDocId(),
                        system = f.system,
                        manifolds = floorManifolds(f),
                        pipe = pipes[i],
                    )
                },
                tech = technicalSections(state, input),
                totals = sumPipe(pipes),
                pusty = !auditUsable(input),
            )
        }
}

/**
 * Id rzutu kondygnacji. Panel trzyma je jako pole stanu kondygnacji, a telefon
 * przenosi całą warstwę rzutu hurtem (`UfhFloor.planJson`) — więc wyjmujemy je
 * stamtąd. Pusty string traktujemy jak brak: tak samo robi karta w panelu.
 */
fun UfhFloor.planDocId(): String? {
    val raw = planJson ?: return null
    val o = runCatching { lenient.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
    return (o["planDocId"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.ifBlank { null }
}

private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }
