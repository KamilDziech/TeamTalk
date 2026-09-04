package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.model.ServiceJob
import com.ekotak.teamtalk.domain.model.ServiceJobStatus
import com.ekotak.teamtalk.domain.model.ServiceJobType
import com.ekotak.teamtalk.domain.repository.ServiceRepository
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import com.ekotak.teamtalk.presentation.service.jobTitle
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Alarmy okna SLA własnych zleceń awaryjnych (ustalenie 2026-09-02): jeden na
 * dwie godziny przed końcem okna, drugi w chwili przekroczenia.
 *
 * Liczy z cache Room, więc działa też bez zasięgu — odświeżenie z serwera jest
 * próbą, nie warunkiem. Każdy alarm trąbi RAZ: znacznik `jobId:faza` leży
 * w preferencjach, bo robotnik chodzi co 15 minut i bez tego serwisant dostałby
 * to samo powiadomienie cztery razy na godzinę.
 *
 * Alarmujemy tylko o SWOICH zleceniach: cudze okno SLA to nie jest wiadomość,
 * na którą można zareagować, a szum uczy ludzi zamiatać powiadomienia.
 */
@HiltWorker
class ServiceSlaWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val service: ServiceRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "service_sla_alerts"

        /** Ile przed końcem okna ostrzegamy. */
        private const val WARN_BEFORE_MS = 2 * 60 * 60 * 1000L

        private const val PHASE_SOON = "soon"
        private const val PHASE_BREACH = "breach"
    }

    override suspend fun doWork(): Result {
        val session = sessionPreferences.session.first() ?: return Result.success()

        // Świeże dane, jeśli są; bez sieci lecimy na tym, co w cache.
        runCatching { service.refresh() }

        val now = System.currentTimeMillis()
        val mine = service.observe().first().jobs.filter {
            it.technicianId == session.userId &&
                it.type == ServiceJobType.AWARIA &&
                it.status != ServiceJobStatus.DONE
        }

        // Znaczniki zamkniętych zleceń nie mają czego pilnować — niech nie rosną
        // w nieskończoność w preferencjach.
        sessionPreferences.retainSlaAlerts(
            mine.flatMap { listOf("${it.id}:$PHASE_SOON", "${it.id}:$PHASE_BREACH") }.toSet(),
        )
        val sent = sessionPreferences.slaAlertsSent.first()

        for (job in mine) {
            val due = parseIsoMillis(job.slaDueAt) ?: continue
            val breached = job.slaBreached || due <= now
            val phase = when {
                breached -> PHASE_BREACH
                due - now <= WARN_BEFORE_MS -> PHASE_SOON
                else -> continue
            }
            val marker = "${job.id}:$phase"
            if (marker in sent) continue

            notifications.showSlaAlert(
                title = if (breached) "Zlecenie po SLA" else "SLA kończy się za 2 h",
                text = alertText(job),
                jobId = job.id,
                urgent = breached,
            )
            sessionPreferences.markSlaAlertSent(marker)
        }
        return Result.success()
    }

    /** Treść alarmu: opis usterki — więcej i tak widać po wejściu w kartę. */
    private fun alertText(job: ServiceJob): String = jobTitle(job)
}
