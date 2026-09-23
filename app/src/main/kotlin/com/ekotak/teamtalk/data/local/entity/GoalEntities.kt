package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache i kolejka modułu Cele.
 *
 * Cel ogląda się tam, gdzie akurat jest człowiek — w busie przed klientem,
 * na budowie, wieczorem w domu — więc pełny offline z kolejką (decyzja
 * 2026-09-23), jak w Zadaniach, Serwisie i Urlopie.
 *
 * Migawkę trzymamy W CAŁOŚCI jako JSON odpowiedzi serwera, po jednym wierszu
 * na widok (zakładka + okres + dział/osoba). Powód: realizację, tempo i status
 * liczy WYŁĄCZNIE serwer — rozbicie na kolumny kusiłoby telefon do liczenia po
 * swojemu, a wtedy „70%" znaczyłoby na obu ekranach co innego. Przy okazji
 * dopisanie miernika po stronie panelu nie wymaga migracji bazy telefonu.
 */

/**
 * Migawka jednego widoku modułu — dokładnie to, co oddała trasa.
 *
 * [key] składa się z zakładki i jej parametrów (`personal:2026-Q3:<userId>`,
 * `team:2026-Q3:biuro`, `company:2026-Q3`), więc przełączanie okresu i działu
 * działa bez sieci na wszystkim, co już raz było otwarte.
 */
@Entity(tableName = "goal_views")
data class GoalViewEntity(
    @PrimaryKey val key: String,
    /** Surowa odpowiedź serwera (PersonalGoalsDto / TeamGoalsDto / CompanyGoalsDto). */
    val payload: String,
    val syncedAt: Long,
)

/**
 * Przebieg celu do wykresu — osobno od migawki widoku, bo dociąga się go
 * dopiero przy wejściu w kartę i tylko dla celu wiodącego.
 */
@Entity(tableName = "goal_trends")
data class GoalTrendEntity(
    @PrimaryKey val goalId: String,
    val payload: String,
    val syncedAt: Long,
)

/** Katalog mierników i działów — jeden wiersz, potrzebny kreatorowi bez sieci. */
@Entity(tableName = "goal_catalog")
data class GoalCatalogEntity(
    @PrimaryKey val id: Int = 1,
    val payload: String,
    val syncedAt: Long,
)

/**
 * Zmiana celu czekająca na wysyłkę.
 *
 * Jeden wiersz = jedna decyzja człowieka: założenie celu, łatka, check-in,
 * zamknięcie okresu albo skasowanie. Klucz główny (`goalId`, `field`) sprawia,
 * że poprawiona przed wysyłką wartość nadpisuje poprzednią — w kolejce liczy
 * się ostatnia decyzja, nie historia klikania.
 *
 * [payload] to gotowe ciało żądania, żeby kolejka nie musiała znać kształtu
 * DTO ani serializować go po raz drugi. Cel założony bez zasięgu dostaje
 * identyfikator lokalny ([LOCAL_ID_PREFIX]) i po wysłaniu podmieniamy go na
 * serwerowy razem z resztą wierszy tego celu — ten sam wzorzec chodzi
 * w Zadaniach i Serwisie.
 */
@Entity(tableName = "goal_mutations", primaryKeys = ["goalId", "field"])
data class GoalMutationEntity(
    val goalId: String,
    val field: String,
    val payload: String,
    /** Kiedy człowiek zrobił zmianę — kolejność wysyłki i podpowiedź w UI. */
    val createdAt: Long,
) {
    companion object {
        /** Pseudopole tworzenia — porządkuje się przed każdą inną zmianą celu. */
        const val FIELD_CREATE = "__create"

        /** Wpis ręczny do celu `manual` (osobna trasa, nie łatka celu). */
        const val FIELD_CHECKIN = "__checkin"

        /** Zamknięcie okresu — wykonuje się PO łatkach, bo zamraża wynik. */
        const val FIELD_CLOSE = "__close"

        /** Skasowanie celu — kasuje też wszystkie inne wiersze tego celu. */
        const val FIELD_DELETE = "__delete"

        /** Łatka pól celu (nazwa, wartość, okres, próg). */
        const val FIELD_PATCH = "__patch"

        /** Prefiks identyfikatora nadawanego lokalnie do czasu wysłania. */
        const val LOCAL_ID_PREFIX = "local:"
    }
}
