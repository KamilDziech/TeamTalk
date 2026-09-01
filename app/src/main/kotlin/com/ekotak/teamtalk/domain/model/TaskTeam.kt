package com.ekotak.teamtalk.domain.model

/**
 * Kafelek kroku „jaki zespół" w kreatorze zadania.
 *
 * Lustro słownika board360 (`api/src/modules/iam/domain/user-function.ts`,
 * ADR-0013 „Rozdzielenie ROLI i FUNKCJI"): dwanaście pozycji = dziesięć funkcji
 * z `USER_FUNCTIONS` plus dwie spoza słownika — [MOJE] (zalogowany użytkownik)
 * i [MONTAZ], bo montaż jest ROLĄ (szczeblem), a nie funkcją.
 *
 * Kolejność jak w makiecie: najpierw pozycje z listy zamawiającego, potem reszta
 * słownika. Zmiana słownika po stronie board360 wymaga ręcznej aktualizacji tego
 * pliku — kod się nie współdzieli, więc lustro trzeba pilnować.
 */
enum class TaskTeam(
    /** Etykieta na kafelku — zgodna z `FUNCTION_LABEL` w `web/src/lib/team.ts`. */
    val label: String,
    /** Podpis pod etykietą; ma odróżniać kafelki, nie tłumaczyć nazwy. */
    val hint: String,
    /** Funkcja z `USER_FUNCTIONS`, po której filtrujemy osoby. */
    val function: String?,
    /** Rola board360 — używana, gdy kafelek nie ma odpowiednika wśród funkcji. */
    val role: String?,
) {
    MOJE("Moje", "dla mnie", null, null),
    SERWIS("Serwis", "zgłoszenia", "serwis", null),
    INZYNIER("Inżynier Opiekun", "sprzedaż i opieka", "inzynier", null),
    KOORDYNATOR("Koordynator", "harmonogram", "koordynator", null),
    ZAOPATRZENIE("Zaopatrzenie", "magazyn, towar", "zaopatrzenie", null),
    KSIEGOWOSC("Księgowość", "faktury", "ksiegowosc", null),
    BOW("BOW", "obsługa wstępna", "bow", null),
    MONTAZ("Monter", "ekipy montażowe", null, "montaz"),
    TECHNOLOG("Technolog", "dobór technologii", "technolog", null),
    MARKETING("Marketing", "pozyskanie", "marketing", null),
    DOTACJE("Dotacje", "programy", "dotacje", null),
    PROJEKT("Projekt", "projektowanie", "projekt", null),
}

/**
 * Osoby pasujące do kafelka.
 *
 * [MOJE] zwraca wyłącznie zalogowanego użytkownika; kafelek funkcyjny — osoby
 * mające tę funkcję; [MONTAZ] — osoby z rolą `montaz`, główną albo dodatkową.
 * Ta sama osoba może pełnić kilka funkcji, więc wraca pod kilkoma kafelkami.
 */
fun TaskTeam.membersFrom(members: List<TaskMember>, selfId: String?): List<TaskMember> = when {
    this == TaskTeam.MOJE -> members.filter { it.id == selfId }
    function != null -> members.filter { function in it.functions }
    role != null -> members.filter { role == it.role || role in it.additionalRoles }
    else -> emptyList()
}
