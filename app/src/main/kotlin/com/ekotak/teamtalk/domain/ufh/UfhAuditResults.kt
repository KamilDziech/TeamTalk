package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.UfhFloor
import com.ekotak.teamtalk.domain.model.UfhState
import com.ekotak.teamtalk.domain.model.toM2

/**
 * WYNIKI ZAKŁADKI „AUDYT" — składanka liczb, które panel liczy w komponencie
 * `UnderfloorHeatingAuditFields.tsx` i `UfhFloorPlanMarker.tsx`, tu jako czyste
 * funkcje: rura per kondygnacja (po automacie rozdzielaczy), strefy i obciążenie
 * skrzynek, kontrola metrażu, podejścia, pojemność wodna.
 *
 * Kolejność rachunku jak w panelu: automat przydziału → rura → strefy i grupy;
 * podejścia i pojemność biorą już policzone metry (bez drugiego rachunku).
 */

/** Podpis kondygnacji — ten sam w rzucie, w liście podejść i w ostrzeżeniach. */
fun floorTitle(index: Int, name: String): String {
    val nm = name.trim()
    return "Kondygnacja ${index + 1}${if (nm.isNotEmpty()) " — $nm" else ""}"
}

/** Sumy m² kondygnacji z pól metrażu: OP (rozstawy), bez OP, dobiegi i całość. */
data class FloorAreas(val ufh: Double, val none: Double, val lead: Double, val all: Double)

fun floorAreas(floor: UfhFloor): FloorAreas {
    var ufh = 0.0
    for (c in MEASURE_CATS) {
        val field = c.field ?: continue
        if (c.spacingM != null) ufh += floor.area(field).toM2() ?: 0.0
    }
    val none = floor.area(AreaCat.NONE.field!!).toM2() ?: 0.0
    val lead = floor.area(AreaCat.LEAD.field!!).toM2() ?: 0.0
    return FloorAreas(ufh = r1(ufh), none = r1(none), lead = r1(lead), all = r1(ufh + none + lead))
}

/** Obciążenie jednej skrzynki na kondygnacji. */
data class ManifoldLoad(
    val group: ManifoldRoomGroup,
    /** Obwody wychodzące z tej skrzynki (suma stref pomieszczeń grupy). */
    val loops: Int,
    /** Rura z tej skrzynki [mb]. */
    val pipeM: Double,
    val load: LoopLoad,
    /** Co zrobić z nadmiarem pętli (`null` = mieści się). */
    val overMessage: String?,
)

/** Wszystko, co audyt pokazuje dla jednej kondygnacji. */
data class FloorAuditResult(
    val plan: FloorPlanState,
    /** Wynik automatu przydziału (pomieszczenia, id z automatu, moved, overloaded). */
    val auto: AutoManifolds,
    /** Rura OP kondygnacji — pozycje, pętle, dobiegi, ostrzeżenia `noLead`/`impossible`. */
    val pipe: FloorPipe,
    /** Strefy (pętle) pomieszczeń po automacie — klucz `RoomShape.id`. */
    val zones: Map<String, RoomZones>,
    /** Metraż w rozbiciu na rozdzielacze (+ grupa bez przypisania). */
    val split: ManifoldSplit,
    /** Obciążenie każdej skrzynki w kolejności kropek. */
    val manifolds: List<ManifoldLoad>,
    /** Obwody i rura całej kondygnacji z rzutu (podsumowanie pod listą pomieszczeń). */
    val floorLoops: Int,
    val floorPipeM: Double,
    /** Jeden rozdzielacz nie uniesie tylu pętli → trzeba dołożyć drugi. */
    val floorLoopWarning: String?,
    /** Strefy wydłużone ponad 1 : 2,5 (z nazwami pomieszczeń). */
    val ratioWarning: String?,
    /** Skrzynki ponad limit pętli. */
    val overloaded: Int,
    /** Sumy kategorii z obrysów (podpisy pod polami metrażu). */
    val sums: Map<AreaCat, CatSum>,
    /** Metraż wg projektu kontra rzut. */
    val areaCheck: FloorAreaCheck,
    val areaMessage: String?,
    val areas: FloorAreas,
)

/**
 * Rachunek jednej kondygnacji audytu.
 *
 * @param aspect proporcja obrazu dla automatu; `0` = ze skali (tak liczy blok rury
 *   i oferta — rzut w panelu podaje proporcję miniatury, co bywa różne o ułamek)
 */
fun floorAudit(floor: UfhFloor, pipeSystem: String?, aspect: Double = 0.0): FloorAuditResult {
    val plan = floor.planState()
    val loopMaxM = ufhLoopMaxM(pipeSystem)
    val manifoldMax = ufhManifoldMax(pipeSystem)
    val auto = autoManifolds(plan.rooms, plan.marks, plan.scale, aspect, loopMaxM, manifoldMax)
    val shown = auto.rooms
    val pipe = floorPipe(shown, plan.marks, plan.scale, floor.areaNumbers(), loopMaxM)
    val zones = planZones(shown, plan.marks, plan.scale, loopMaxM)
    val split = splitByManifold(shown, plan.marks, plan.scale)
    val loopsPer = split.groups.map { zonesTotal(zones, it.rooms) }
    val manifolds = split.groups.mapIndexed { i, g ->
        val loops = loopsPer[i]
        ManifoldLoad(
            group = g,
            loops = loops,
            pipeM = zonesPipeTotal(zones, g.rooms),
            load = loopLoad(loops, manifoldMax),
            overMessage = manifoldOverMessage(
                loops = loops,
                manifolds = split.groups.size,
                freeElsewhere = freeLoopsElsewhere(loopsPer, i, manifoldMax),
                max = manifoldMax,
            ),
        )
    }
    val floorLoops = zonesTotal(zones, shown)
    val check = floorAreaCheck(floor.projectM2.toM2(), planFloorM2(plan.rooms, plan.scale))
    return FloorAuditResult(
        plan = plan,
        auto = auto,
        pipe = pipe,
        zones = zones,
        split = split,
        manifolds = manifolds,
        floorLoops = floorLoops,
        floorPipeM = zonesPipeTotal(zones, shown),
        floorLoopWarning = floorLoopMessage(floorLoops, plan.marks.size, manifoldMax),
        ratioWarning = zonesRatioMessage(zones, shown),
        overloaded = manifolds.count { it.load.level == LoopLevel.OVER },
        sums = catSums(plan.rooms, plan.scale),
        areaCheck = check,
        areaMessage = floorAreaMessage(check),
        areas = floorAreas(floor),
    )
}

/** Całość audytu — sumy budynku pod kondygnacjami. */
data class UfhAuditSummary(
    val floors: List<FloorAuditResult>,
    /** Rura OP całego budynku (suma kondygnacji). */
    val totalPipe: PipeSum,
    /** Powierzchnia OP wszystkich kondygnacji [m²] (z pól metrażu). */
    val totalM2: Double,
    /** Podejścia źródło → rozdzielacze; `ok = false` + `problem`, gdy nie da się policzyć. */
    val supply: SupplyPlan,
    /** Czym prowadzimy podejście (nazwa, kod kartoteki). */
    val supplyPipe: SupplyPipeInfo,
    val water: WaterVolume,
    /** Dawka inhibitora — tylko przy „Inhibitor biobójczy = tak" i policzonej pojemności. */
    val biocide: ChemDose?,
    /** Zbiorczy komunikat o rozjeździe metrażu ponad 3%. */
    val areaToFix: String?,
)

fun ufhAuditSummary(state: UfhState): UfhAuditSummary {
    val floors = state.floors.map { floorAudit(it, state.pipeSystem) }
    val totalPipe = sumPipe(floors.map { it.pipe })
    var m2 = 0.0
    for (f in floors) m2 += f.areas.ufh
    val supply = supplyPlan(
        state.floors.mapIndexed { i, f ->
            val plan = floors[i].plan
            SupplyFloor(
                label = floorTitle(i, f.name),
                marks = plan.marks,
                scale = plan.scale,
                boxType = f.boxType,
                heatSource = plan.heatSource,
            )
        },
    )
    val water = waterVolume(state.pipeSystem, totalPipe.total, state.install.leadInPipeMm, supply.total)
    return UfhAuditSummary(
        floors = floors,
        totalPipe = totalPipe,
        totalM2 = jsRound(m2 * 10) / 10,
        supply = supply,
        supplyPipe = supplyPipeInfo(state.install.leadInPipeMm, state.pipeSystem),
        water = water,
        biocide = if (state.install.biocide.trim() == BIOCIDE_YES) biocideDose(water.total) else null,
        areaToFix = floorsOverMessage(floors.map { it.areaCheck }),
    )
}
