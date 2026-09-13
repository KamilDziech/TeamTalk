package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.VoiceReportEntity
import com.ekotak.teamtalk.data.remote.dto.VoiceReportResponseDto
import com.ekotak.teamtalk.domain.model.TranscriptionStatus
import com.ekotak.teamtalk.domain.model.VoiceReport

fun VoiceReportResponseDto.toEntity(): VoiceReportEntity = VoiceReportEntity(
    id                  = id,
    callLogId           = callLogId,
    clientId            = clientId,
    text                = text,
    transcript          = transcript,
    recordingKey        = recordingKey,
    durationSec         = durationSec,
    createdAt           = createdAt,
    updatedAt           = updatedAt,
    summary             = summary,
    agreements          = agreements,
    nextStep            = nextStep,
    transcriptionStatus = transcriptionStatus,
    transcriptionError  = transcriptionError,
)

fun VoiceReportEntity.toDomain(): VoiceReport = VoiceReport(
    id                  = id,
    callLogId           = callLogId,
    clientId            = clientId,
    text                = text,
    transcript          = transcript,
    recordingKey        = recordingKey,
    durationSec         = durationSec,
    createdAt           = createdAt,
    updatedAt           = updatedAt,
    summary             = summary,
    agreements          = agreements,
    nextStep            = nextStep,
    transcriptionStatus = TranscriptionStatus.fromValue(transcriptionStatus),
    transcriptionError  = transcriptionError,
)

fun VoiceReportResponseDto.toDomain(): VoiceReport = toEntity().toDomain()
