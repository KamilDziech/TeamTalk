package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.VoiceReportEntity
import com.ekotak.teamtalk.data.remote.dto.VoiceReportResponseDto
import com.ekotak.teamtalk.domain.model.VoiceReport

fun VoiceReportResponseDto.toEntity(): VoiceReportEntity = VoiceReportEntity(
    id            = id,
    callLogId     = callLogId,
    audioUrl      = audioUrl,
    transcription = transcription,
    aiSummary     = aiSummary,
    createdBy     = createdBy,
    callCount     = callCount,
    createdAt     = createdAt,
    updatedAt     = updatedAt,
)

fun VoiceReportEntity.toDomain(): VoiceReport = VoiceReport(
    id            = id,
    callLogId     = callLogId,
    audioUrl      = audioUrl,
    transcription = transcription,
    aiSummary     = aiSummary,
    createdBy     = createdBy,
    callCount     = callCount,
    createdAt     = createdAt,
    updatedAt     = updatedAt,
)

fun VoiceReportResponseDto.toDomain(): VoiceReport = VoiceReport(
    id            = id,
    callLogId     = callLogId,
    audioUrl      = audioUrl,
    transcription = transcription,
    aiSummary     = aiSummary,
    createdBy     = createdBy,
    callCount     = callCount,
    createdAt     = createdAt,
    updatedAt     = updatedAt,
)
