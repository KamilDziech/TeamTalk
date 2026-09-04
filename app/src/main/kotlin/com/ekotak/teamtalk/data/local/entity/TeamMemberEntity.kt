package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Osoba z `GET /api/tasks/members` — WSPÓLNA książka zespołu, nie własność
 * jednego modułu.
 *
 * Zaczęła jako `calendar_members` (arkusz wydarzenia bez zasięgu pokazywałby
 * uczestników jako identyfikatory). Od 2026-09-04 czyta z niej też filtr osoby
 * w Mapie, bo drzewo działów potrzebuje ROLI i FUNKCJI, a te dwa moduły mają
 * działać bez zasięgu — stąd tabela `team_members` i migracja 10 → 11.
 */
@Entity(tableName = "team_members")
data class TeamMemberEntity(
    @PrimaryKey val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    /**
     * Rola (szczebel) i funkcje (co robi) — z nich liczy się dział w filtrze
     * osoby. Puste = wiersz sprzed migracji 10 → 11; do najbliższej
     * synchronizacji taka osoba wpada do „Pozostali".
     */
    val role: String?,
    val additionalRoles: List<String> = emptyList(),
    val functions: List<String> = emptyList(),
)
