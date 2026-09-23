package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.BriefingItem

/**
 * Skrzynka odprawy. Moduł jest WYŁĄCZNIE ONLINE (ustalenie 2026-09-23):
 * komunikaty są krótkie i tracą sens po czasie, a cache w Room wymagałby
 * migracji w pliku, który akurat przepisuje moduł Montaż. Powiadomienia i tak
 * chodzą z robotnika, więc bez zasięgu człowiek nie traci sygnału, tylko treść.
 */
interface BriefingRepository {
    suspend fun inbox(): List<BriefingItem>
    suspend fun unreadCount(): Int
    suspend fun ack(id: String)
}
