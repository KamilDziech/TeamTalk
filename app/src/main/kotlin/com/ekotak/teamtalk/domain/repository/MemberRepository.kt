package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.TaskMember
import kotlinx.coroutines.flow.Flow

/**
 * Książka zespołu — kto u nas pracuje, z rolą i funkcjami.
 *
 * Wydzielona z modułów, bo tego samego zestawu potrzebuje filtr osoby
 * w Zadaniach, Kalendarzu i na Mapie (drzewo działów, ustalenie 2026-09-04).
 * Strumień idzie z cache Room, więc działy da się złożyć bez zasięgu;
 * [refresh] jest MIĘKKI — nieudane pobranie zostawia to, co w cache.
 */
interface MemberRepository {
    fun observe(): Flow<List<TaskMember>>
    suspend fun refresh()
}
