package com.ekotak.teamtalk.domain.model

/**
 * Dział w filtrze osób — lustro `web/src/app/app/tasks/members.ts`
 * (ustalenie 2026-09-04).
 *
 * Cztery kubełki, każdy człowiek DOKŁADNIE w jednym: sumy działów się nie
 * dublują, a lista osób ma stały kształt niezależnie od ekranu. Kolejność
 * enuma = kolejność w UI.
 */
enum class Department(val label: String) {
    BIURO("Biuro"),
    SERWIS("Serwis"),
    MONTAZ("Montaż"),
    POZOSTALI("Pozostali"),
}

/**
 * Dział osoby — PIERWSZY pasujący warunek:
 *  1. Serwis — funkcja `serwis` (ADR-0013: serwisant to FUNKCJA, nie szczebel;
 *     prawie każdy serwisant ma obok tego rolę `biuro`, więc Serwis musi iść
 *     przed Biurem, inaczej dział Serwis byłby pusty),
 *  2. Montaż — rola `montaz` (główna albo dodatkowa),
 *  3. Biuro — rola `biuro`, a także `zarzad` i `admin` (zarząd siedzi w biurze),
 *  4. Pozostali — reszta: stażyści i osoby bez roli.
 */
fun departmentOf(member: TaskMember): Department {
    val roles = (listOf(member.role) + member.additionalRoles).filterNotNull()
    return when {
        "serwis" in member.functions -> Department.SERWIS
        "montaz" in roles -> Department.MONTAZ
        "biuro" in roles || "zarzad" in roles || "admin" in roles -> Department.BIURO
        else -> Department.POZOSTALI
    }
}

/** Osoby posortowane jak w panelu: Biuro → Serwis → Montaż → Pozostali, w dziale alfabetycznie. */
fun sortMembersByDepartment(members: List<TaskMember>): List<TaskMember> =
    members.sortedWith(compareBy({ departmentOf(it).ordinal }, { it.displayName.lowercase() }))

/**
 * Osoby rozbite na działy — komplet czterech kluczy, także pustych. Puste działy
 * zostają widoczne w UI, żeby lista nie zmieniała kształtu przy zmianie danych.
 */
fun groupMembersByDepartment(members: List<TaskMember>): Map<Department, List<TaskMember>> {
    val grouped = sortMembersByDepartment(members).groupBy { departmentOf(it) }
    return Department.entries.associateWith { grouped[it].orEmpty() }
}
