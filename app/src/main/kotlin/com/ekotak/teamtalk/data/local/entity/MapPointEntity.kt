package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Punkt mapy w cache offline. Trzymamy punkty POLICZONE (z kolorem, etykietą
 * i literą pinu), a nie surowe deale i zlecenia — dzięki temu mapa otwiera się
 * bez zasięgu jednym zapytaniem do bazy, zamiast składać ją z pięciu źródeł.
 *
 * Etykiety osób leżą płasko obok identyfikatorów, bo filtr osoby na mapie
 * pokazuje wyłącznie ludzi obecnych w widoku — słownik z serwera nie jest
 * wtedy do niczego potrzebny.
 *
 * `installs` idzie przez konwerter listy (rozdzielana przecinkiem) — nazwy
 * kategorii głównych przecinków nie zawierają.
 */
@Entity(tableName = "map_points")
data class MapPointEntity(
    @PrimaryKey val id: String,
    val kind: String,
    /** null = adres niezweryfikowany → lista „bez lokalizacji". */
    val lat: Double?,
    val lng: Double?,
    val name: String,
    val city: String?,
    val address: String?,
    val phone: String?,
    val installs: List<String>,
    val ownerId: String?,
    val ownerLabel: String?,
    val stageOwnerId: String?,
    val stageOwnerLabel: String?,
    val technicianId: String?,
    val technicianLabel: String?,
    val badgeKey: String,
    val badgeLabel: String,
    val badgeColor: Long,
    val badgeOrder: Int,
    val badgeLetter: String,
    val dealId: String?,
    val clientId: String?,
    /** Moment pobrania migawki — pasek „dane z HH:mm" przy pracy bez zasięgu. */
    val syncedAt: Long,
)
