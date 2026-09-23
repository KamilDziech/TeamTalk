package com.ekotak.teamtalk.domain.model

/**
 * Moduł Cele — modele domenowe. Odpowiadają kształtowi odpowiedzi panelu,
 * bo liczenie zostaje po stronie serwera: telefon dostaje gotową realizację,
 * tempo i status, a sam odpowiada wyłącznie za rysowanie i kolejkę zapisów.
 */

enum class GoalScope(val wire: String) {
    PERSONAL("personal"),
    TEAM("team"),
    COMPANY("company"),
    ;

    companion object {
        fun of(wire: String?): GoalScope =
            entries.firstOrNull { it.wire == wire } ?: PERSONAL
    }
}

/** Jednostka miernika — decyduje o formatowaniu liczby na karcie. */
enum class GoalUnit(val wire: String) {
    PLN("pln"),
    SZT("szt"),
    PCT("pct"),
    PKT("pkt"),
    ;

    companion object {
        fun of(wire: String?): GoalUnit = entries.firstOrNull { it.wire == wire } ?: SZT
    }
}

/**
 * Status celu — realizacja ZESTAWIONA Z TEMPEM okresu, nie goły procent.
 * Liczy go serwer; telefon dobiera do niego kolor i etykietę.
 */
enum class GoalStatus(val wire: String, val label: String) {
    DONE("done", "osiągnięty"),
    OK("ok", "na kursie"),
    WARN("warn", "za tempem"),
    BAD("bad", "zagrożony"),
    ;

    companion object {
        fun of(wire: String?): GoalStatus = entries.firstOrNull { it.wire == wire } ?: OK
    }
}

data class GoalMetric(
    val code: String,
    val label: String,
    val unit: GoalUnit,
    val source: String,
    val direction: String,
    val scopes: List<GoalScope>,
    val hint: String,
) {
    /** Cel, którego żaden moduł nie policzy — wartość podaje człowiek. */
    val isManual: Boolean get() = code == "manual"
}

data class GoalDepartment(val key: String, val label: String)

/** Pozycja regulaminu punktowego — nagroda za osiągnięty cel. */
data class GoalRule(
    val id: String,
    val code: String,
    val name: String,
    val category: String,
    val points: Int,
)

data class GoalPerson(
    val id: String,
    val name: String,
    val department: String,
)

data class GoalMember(
    val id: String,
    val name: String,
    val department: String,
    /** Udział osoby w mierniku celu działu, w jednostce tego celu. */
    val contribution: Double,
)

data class Goal(
    val id: String,
    val scope: GoalScope,
    val ownerUserId: String?,
    val ownerName: String?,
    val teamKey: String?,
    val metric: String,
    val metricLabel: String,
    val unit: GoalUnit,
    val source: String,
    val name: String,
    val target: Double,
    val value: Double,
    val pct: Double,
    /** Ile procent powinno być zrobione na dziś — kreska tempa na pasku. */
    val pacePct: Double,
    val status: GoalStatus,
    val direction: String,
    val periodKey: String,
    val warnAtPct: Int,
    /** `active` | `closed` — po zamknięciu wynik idzie z migawki, nie z danych. */
    val lifecycle: String,
    val closedAt: String?,
    val lastCheckinAt: String?,
    /** Nagroda za osiągnięcie; `null` także wtedy, gdy nie mamy prawa jej widzieć. */
    val ruleId: String? = null,
    val ruleLabel: String? = null,
    /** Zapis tego celu czeka w kolejce na wysyłkę (ustawia repozytorium). */
    val pending: Boolean = false,
) {
    val isClosed: Boolean get() = lifecycle == "closed"
    val isManual: Boolean get() = metric == "manual"
    val isLocal: Boolean get() = id.startsWith("local:")
}

data class GoalHistoryRow(
    val id: String,
    val periodKey: String,
    val name: String,
    val unit: GoalUnit,
    val target: Double,
    val value: Double,
    val pct: Double,
)

data class GoalTrend(
    val goalId: String,
    val target: Double,
    val points: List<GoalTrendPoint>,
)

data class GoalTrendPoint(val at: String, val value: Double)

/** Zakładka „Osobiste". */
data class PersonalGoals(
    val periodKey: String,
    val person: GoalPerson?,
    val canManage: Boolean,
    /** Czyje cele wolno otworzyć: ja + podwładni (zarząd: wszyscy). */
    val managed: List<GoalPerson>,
    val items: List<Goal>,
    val history: List<GoalHistoryRow>,
)

/** Zakładka „Zespołu". `detailed = false` ⇒ serwer nie dał imiennych wyników. */
data class TeamGoals(
    val periodKey: String,
    val teamKey: String,
    val teamLabel: String,
    val detailed: Boolean,
    val canManage: Boolean,
    val items: List<Goal>,
    val members: List<GoalMember>,
    val personalGoals: List<Goal>,
    val history: List<GoalHistoryRow>,
)

/** Zakładka „Firmy". */
data class CompanyGoals(
    val periodKey: String,
    val canManage: Boolean,
    val items: List<Goal>,
    val leadMetricLabel: String?,
    val byDepartment: List<GoalDepartmentValue>,
    val history: List<GoalHistoryRow>,
)

data class GoalDepartmentValue(val key: String, val label: String, val value: Double)

data class GoalCatalog(
    val metrics: List<GoalMetric> = emptyList(),
    val departments: List<GoalDepartment> = emptyList(),
    val rules: List<GoalRule> = emptyList(),
)

/**
 * Cel do zapisania. Pola `null` pomijamy w ciele żądania, więc przy łatce
 * znaczą „nie ruszaj" — tak samo czyta to serwer.
 */
data class GoalDraft(
    val scope: GoalScope,
    val ownerUserId: String? = null,
    val teamKey: String? = null,
    val metric: String,
    val name: String = "",
    val target: Double,
    val direction: String? = null,
    val periodKey: String,
    val warnAtPct: Int? = null,
    /** Nagroda z regulaminu punktowego — tylko dla celów osobistych. */
    val ruleId: String? = null,
)
