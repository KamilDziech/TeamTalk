package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Zadanie w cache offline. Źródło (deal albo projekt) leży tu płasko — sealed
 * `TaskSource` składamy dopiero w mapperze, bo Room i tak zapisałby to dwiema
 * kolumnami, a płaski zapis pozwala filtrować po źródle zapytaniem.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val assigneeId: String?,
    val assigneeEmail: String?,
    val dueAt: String?,
    val status: String,
    val priority: String,
    val section: String?,
    val estimatedMinutes: Int?,
    val slaHours: Int?,
    val commentCount: Int,
    val createdBy: String?,
    val createdAt: String,
    val updatedAt: String?,
    val dealId: String?,
    val dealName: String?,
    val projectId: String?,
    val projectName: String?,
)
