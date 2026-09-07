package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.model.LeaveMode
import com.ekotak.teamtalk.domain.model.LeaveRequest
import com.ekotak.teamtalk.domain.model.LeaveStatus
import com.ekotak.teamtalk.domain.repository.LeaveRepository
import com.ekotak.teamtalk.presentation.leave.daysLabel
import com.ekotak.teamtalk.presentation.leave.rangeLabel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

/**
 * Dwa powiadomienia modułu Urlop (ustalenie 2026-09-06):
 *  • **decyzja o moim wniosku** — dla składającego,
 *  • **nowy wniosek do akceptacji** — dla zwierzchnika i jego backupu.
 *
 * board360 nie ma pusha, więc robotnik odpytuje — jak przy wywołaniach (@).
 * Rzadziej niż tam, bo urlop to nie rozmowa: pół godziny zwłoki niczego nie
 * psuje, a bateria zostaje w telefonie.
 *
 * Dwa zabezpieczenia przed hałasem, oba konieczne:
 *  1. **znacznik `id:status`** w preferencjach — jeden alarm na zmianę stanu,
 *     więc robotnik chodzący co pół godziny nie powtórzy go szesnaście razy
 *     dziennie, a ponowna decyzja po edycji wniosku zatrąbi na nowo;
 *  2. **okno świeżości** — po instalacji aplikacji cała historia wniosków jest
 *     dla telefonu „nowa"; bez tego zwierzchnik dostałby przy pierwszym
 *     uruchomieniu powiadomienie o każdym urlopie z ostatniego roku. Starsze
 *     wpisy tylko zapisujemy jako widziane, w ciszy.
 */
@HiltWorker
class LeaveNotifyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val leave: LeaveRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "leave_notify"

        /** Jak stara decyzja jeszcze zasługuje na powiadomienie. */
        private val DECISION_FRESH = Duration.ofDays(3)

        /** Jak stary wniosek podwładnego jeszcze wywołuje alarm u zwierzchnika. */
        private val REQUEST_FRESH = Duration.ofDays(7)
    }

    override suspend fun doWork(): Result {
        sessionPreferences.session.first() ?: return Result.success()

        // Świeże dane, jeśli są; bez zasięgu i tak porównamy to, co w cache.
        runCatching { leave.refresh() }

        val snapshot = leave.observe().first()
        val markers = (snapshot.myRequests + snapshot.inbox).map { it.marker() }.toSet()
        // Znaczniki wniosków, których już nie ma, nie mają czego pilnować.
        sessionPreferences.retainLeaveAlerts(markers)
        val sent = sessionPreferences.leaveAlertsSent.first()
        val now = Instant.now()

        for (request in snapshot.myRequests) {
            if (request.status != LeaveStatus.ZATWIERDZONY && request.status != LeaveStatus.ODRZUCONY) continue
            val marker = request.marker()
            if (marker in sent) continue

            // Wniosek rozpatrzony dawno temu (albo przed instalacją) tylko
            // odnotowujemy — powiadomienie o nim byłoby wiadomością z zeszłego roku.
            if (!request.decidedRecently(now)) {
                sessionPreferences.markLeaveAlertSent(marker)
                continue
            }

            val approved = request.status == LeaveStatus.ZATWIERDZONY
            notifications.showLeaveNotification(
                title = if (approved) "Urlop zatwierdzony" else "Urlop odrzucony",
                text = buildString {
                    append(rangeLabel(request.start, request.end))
                    append(" (")
                    append(daysLabel(request.workingDays))
                    append(')')
                    // Przy odmowie notatka zwierzchnika jest jedyną informacją,
                    // co dalej robić — musi być w treści, nie po kliknięciu.
                    request.decisionNote?.takeIf { it.isNotBlank() }?.let {
                        append(" — ")
                        append(it)
                    }
                    if (approved) remainingSuffix(snapshot.balance)?.let(::append)
                },
                notificationId = request.id.hashCode(),
                team = false,
            )
            sessionPreferences.markLeaveAlertSent(marker)
        }

        for (item in snapshot.inbox) {
            if (item.status != LeaveStatus.OCZEKUJE || !item.canDecide) continue
            val marker = item.marker()
            if (marker in sent) continue

            if (!item.startsSoon(now)) {
                sessionPreferences.markLeaveAlertSent(marker)
                continue
            }

            notifications.showLeaveNotification(
                title = "Wniosek do decyzji",
                text = buildString {
                    append(item.employeeName ?: "Pracownik")
                    append(" prosi o ")
                    append(daysLabel(item.workingDays))
                    append(" — ")
                    append(rangeLabel(item.start, item.end))
                    append(". Czeka na Twoją decyzję.")
                },
                notificationId = item.id.hashCode(),
                team = true,
            )
            sessionPreferences.markLeaveAlertSent(marker)
        }

        return Result.success()
    }

    /**
     * Znacznik alarmu. Status w kluczu sprawia, że wniosek cofnięty do akceptacji
     * i zatwierdzony ponownie zatrąbi drugi raz — bo to druga decyzja, nie echo
     * pierwszej.
     */
    private fun LeaveRequest.marker(): String = "$id:${status.wire}"

    private fun LeaveRequest.decidedRecently(now: Instant): Boolean {
        val at = decidedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
        return Duration.between(at, now) < DECISION_FRESH
    }

    /**
     * Czy wniosek podwładnego jest na tyle świeży, by o nim trąbić. Liczymy od
     * POCZĄTKU urlopu, nie od daty złożenia: wniosek złożony z półrocznym
     * wyprzedzeniem czeka, ale to nadal sprawa do załatwienia.
     */
    private fun LeaveRequest.startsSoon(now: Instant): Boolean {
        val startedAgo = Duration.between(
            start.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),
            now,
        )
        return startedAgo < REQUEST_FRESH
    }

    /** „Zostało Ci 8 dni wymiaru." — tylko przy umowie, która ten wymiar ma. */
    private fun remainingSuffix(balance: com.ekotak.teamtalk.domain.model.LeaveBalance?): String? {
        if (balance == null || balance.mode != LeaveMode.QUOTA) return null
        return " Zostało Ci ${balance.remaining} z ${balance.entitled} dni."
    }
}
