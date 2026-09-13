package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.CommChannel
import com.ekotak.teamtalk.domain.model.DealCallSummary
import com.ekotak.teamtalk.domain.model.DealComment
import com.ekotak.teamtalk.domain.model.EmailThread
import com.ekotak.teamtalk.domain.model.WhatsappDirection
import com.ekotak.teamtalk.domain.model.WhatsappMessage
import com.ekotak.teamtalk.presentation.components.MentionComposer
import com.ekotak.teamtalk.presentation.components.rememberMentionState
import com.ekotak.teamtalk.presentation.email.EmailComposeSheet
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * Zakładka „Komunikacja" karty deala — hub kanałów zawężony do tego deala,
 * 1:1 z `DealCommsPanel` panelu: Komunikator, Email, WhatsApp, Telefon i SMS
 * (ten ostatni w przygotowaniu, tak samo jak w web).
 *
 * Kanały nie są tu jedną osią czasu, choć na wąskim ekranie kusi, żeby je zlać.
 * Byłby to jednak inny produkt niż panel: człowiek szuka „co pisaliśmy mailem",
 * a nie „co się w ogóle działo", a wysyłka w każdym kanale ma inne reguły
 * (okno 24h WhatsAppa, wybór skrzynki w poczcie, wywołania „@" w Komunikatorze).
 *
 * Poczta jest zrobiona z tych samych ekranów, co moduł Email: lista otwiera
 * wątek modułu, a pisanie idzie oknem modułu. Zakładka nie ma więc drugiej,
 * własnej poczty — ma wycinek tej samej.
 *
 * Kanały bez własnego modułu (Komunikator, WhatsApp, Telefon) mają pełny
 * offline z kolejką: wpis zrobiony bez zasięgu jest widoczny od razu, ze
 * znacznikiem „w kolejce", i poleci sam po powrocie łączności.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DealCommsTab(
    state: DealDetailViewModel.UiState,
    onOpenEmailThread: (String) -> Unit,
    viewModel: DealDetailViewModel,
) {
    val comms = state.comms

    SectionCard {
        SectionTitle("Komunikacja")
        SectionGap()
        Text(
            text = "Rozmowy i korespondencja w sprawie TEGO deala. Pełny rejestr " +
                "kontaktów z klientem jest w module Komunikacja i na jego karcie.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionGap()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CommChannel.entries.forEach { channel ->
                ChoicePill(
                    label = "${channel.icon} ${channel.label}",
                    selected = channel == comms.channel,
                    onClick = { viewModel.selectCommChannel(channel) },
                )
            }
        }

        comms.error?.let { error ->
            SectionGap()
            CommsStatusLine(text = error, color = MaterialTheme.colorScheme.error)
        }
    }

    SectionGap()

    when {
        comms.isLoading && !comms.loadedFor(comms.channel) -> SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Wczytuję…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        else -> when (comms.channel) {
            CommChannel.KOMUNIKATOR -> InternalChannel(state, viewModel)
            CommChannel.EMAIL -> EmailChannel(state, onOpenEmailThread, viewModel)
            CommChannel.WHATSAPP -> WhatsappChannel(state, viewModel)
            CommChannel.TELEFON -> PhoneChannel(state, viewModel)
            CommChannel.SMS -> SmsChannel()
        }
    }

    if (comms.callForm != null) CallSummaryDialog(state, viewModel)
    if (comms.compose != null) DealEmailSheet(state, viewModel)
}

// ── Komunikator wewnętrzny ───────────────────────────────────────────────────

/**
 * Wewnętrzny czat zespołu o tym dealu. Ten sam byt, co komentarze zadania, więc
 * i to samo pole z wywołaniami przez „@" — wywołany dostaje powiadomienie tą
 * samą drogą, co przy zadaniu.
 */
@Composable
private fun InternalChannel(state: DealDetailViewModel.UiState, viewModel: DealDetailViewModel) {
    val comms = state.comms
    val mention = rememberMentionState()

    SectionCard {
        SectionTitle("Komunikator", accent = comms.comments.size.takeIf { it > 0 }?.toString())
        SectionGap()
        Text(
            text = "Rozmowa zespołu o tym dealu. Klient jej nie widzi. " +
                "Wpisz @, żeby wywołać osobę albo grupę.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionGap()

        if (comms.comments.isEmpty()) {
            Text(
                text = "Brak wiadomości. Zacznij rozmowę o tym dealu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                comms.comments.forEach { CommentBubble(it) }
            }
        }

        SectionGap()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SectionGap()
        MentionComposer(
            state = mention,
            members = state.members,
            sending = comms.isSendingComment,
            placeholder = "Napisz do zespołu… (@ wywołuje osobę)",
            onSend = {
                viewModel.sendDealComment(mention.text, mention.tokens)
                mention.clear()
            },
        )
    }
}

@Composable
private fun CommentBubble(comment: DealComment) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (comment.mine) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = buildString {
                append(comment.authorName)
                formatDateTime(comment.createdAt)?.let { append(" · ").append(it) }
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (comment.mine) EkotakGreen.copy(alpha = 0.16f)
                    else colors.surfaceVariant,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(text = comment.body, style = MaterialTheme.typography.bodyMedium)
        }
        if (comment.pending) {
            Spacer(Modifier.height(2.dp))
            CommsStatusLine(text = "W kolejce — poleci po powrocie zasięgu.", color = SyncBlue)
        }
    }
}

// ── Email ────────────────────────────────────────────────────────────────────

/**
 * Korespondencja dowiązana do deala — wszystkie foldery i obie skrzynki, bez
 * wycinka opiekuna: kto widzi kartę, ten widzi jej wątki. Wiersz otwiera wątek
 * EKRANEM MODUŁU, więc odpowiadanie, załączniki i etykiety działają tam tak
 * samo jak w skrzynce; zakładka nie dubluje czytnika poczty.
 */
@Composable
private fun EmailChannel(
    state: DealDetailViewModel.UiState,
    onOpenThread: (String) -> Unit,
    viewModel: DealDetailViewModel,
) {
    val comms = state.comms

    SectionCard {
        SectionTitle(
            text = "Email",
            accent = comms.threads.size.takeIf { it > 0 }?.toString(),
        )
        SectionGap()
        OutlinedButton(onClick = viewModel::openDealEmailCompose, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Nowy e-mail do klienta")
        }

        SectionGap()
        if (comms.threads.isEmpty()) {
            Text(
                text = "Brak e-maili dowiązanych do tego deala. Wiadomość napisana " +
                    "stąd dowiąże się sama.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                comms.threads.forEach { thread ->
                    EmailThreadRow(thread) { onOpenThread(thread.id) }
                }
            }
        }
    }
}

@Composable
private fun EmailThreadRow(thread: EmailThread, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = thread.who,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (thread.unread) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            formatDateTime(thread.lastAt)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Text(
            text = thread.subject,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (thread.unread) FontWeight.SemiBold else FontWeight.Normal,
            color = colors.onSurface,
        )
        if (thread.snippet.isNotBlank()) {
            Text(
                text = thread.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
            )
        }
        if (thread.pendingSync) {
            CommsStatusLine(text = "W kolejce — poleci po powrocie zasięgu.", color = SyncBlue)
        }
    }
}

@Composable
private fun DealEmailSheet(state: DealDetailViewModel.UiState, viewModel: DealDetailViewModel) {
    val compose = state.comms.compose ?: return
    EmailComposeSheet(
        title = "Nowy e-mail",
        fromAddress = compose.fromAddress,
        to = compose.to,
        cc = compose.cc,
        subject = compose.subject,
        body = compose.body,
        attachments = compose.attachments,
        sending = state.comms.isSendingEmail,
        senders = state.comms.mailboxes.map { it.id to it.address },
        onPickSender = viewModel::pickDealEmailSender,
        onToChange = { value -> viewModel.editDealEmailCompose { it.copy(to = value) } },
        onCcChange = { value -> viewModel.editDealEmailCompose { it.copy(cc = value) } },
        onSubjectChange = { value -> viewModel.editDealEmailCompose { it.copy(subject = value) } },
        onBodyChange = { value -> viewModel.editDealEmailCompose { it.copy(body = value) } },
        onAddAttachment = { file ->
            viewModel.editDealEmailCompose { it.copy(attachments = it.attachments + file) }
        },
        onRemoveAttachment = { uri ->
            viewModel.editDealEmailCompose {
                it.copy(attachments = it.attachments.filterNot { file -> file.uri == uri })
            }
        },
        onSend = { viewModel.sendDealEmail() },
        onSaveDraft = { viewModel.sendDealEmail(asDraft = true) },
        onDismiss = viewModel::closeDealEmailCompose,
    )
}

// ── WhatsApp ─────────────────────────────────────────────────────────────────

/**
 * Skrzynka WhatsApp deala. Wysyłka jest tu za `deal.manage` (tak samo jak
 * w board360) i podlega oknu 24h — poza nim serwer przyjmuje wyłącznie
 * zatwierdzony szablon i mówi to własnym komunikatem, którego nie tłumaczymy.
 */
@Composable
private fun WhatsappChannel(state: DealDetailViewModel.UiState, viewModel: DealDetailViewModel) {
    val comms = state.comms

    SectionCard {
        SectionTitle("WhatsApp", accent = comms.whatsapp.size.takeIf { it > 0 }?.toString())
        SectionGap()
        Text(
            text = "Skrzynka zespołowa. Integracja Meta bywa niepodłączona — wtedy " +
                "wiadomość zapisuje się jako oczekująca i wyjdzie po konfiguracji.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionGap()

        if (comms.whatsapp.isEmpty()) {
            Text(
                text = "Brak wiadomości.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                comms.whatsapp.forEach { WhatsappBubble(it) }
            }
        }

        if (state.canManage) {
            SectionGap()
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionGap()
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = comms.whatsappDraft,
                    onValueChange = viewModel::onWhatsappDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Napisz do klienta…") },
                    maxLines = 4,
                    enabled = !comms.isSendingWhatsapp,
                )
                IconButton(
                    onClick = viewModel::sendDealWhatsapp,
                    enabled = !comms.isSendingWhatsapp && comms.whatsappDraft.isNotBlank(),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                ) {
                    if (comms.isSendingWhatsapp) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Wyślij wiadomość",
                            tint = EkotakGreen,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsappBubble(message: WhatsappMessage) {
    val colors = MaterialTheme.colorScheme
    val mine = message.direction == WhatsappDirection.OUTBOUND
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (mine) EkotakGreen.copy(alpha = 0.16f) else colors.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(text = message.display, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildString {
                formatDateTime(message.createdAt)?.let { append(it).append(" · ") }
                append(
                    if (message.pending) "w kolejce telefonu"
                    else WhatsappMessage.STATUS_LABEL[message.status] ?: message.status,
                )
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (message.pending) SyncBlue else colors.onSurfaceVariant,
        )
    }
}

// ── Telefon ──────────────────────────────────────────────────────────────────

/**
 * Rozmowy telefoniczne w sprawie tego deala. Widać tu wyłącznie streszczenia
 * przypięte do deala — pełny rejestr połączeń klienta (w tym te, które TeamTalk
 * zapisał sam, nie znając deala) jest w module Komunikacja i na karcie klienta.
 */
@Composable
private fun PhoneChannel(state: DealDetailViewModel.UiState, viewModel: DealDetailViewModel) {
    val comms = state.comms

    SectionCard {
        SectionTitle("Telefon", accent = comms.calls.size.takeIf { it > 0 }?.toString())
        SectionGap()
        OutlinedButton(onClick = viewModel::openCallSummaryForm, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Dopisz streszczenie rozmowy")
        }

        SectionGap()
        if (comms.calls.isEmpty()) {
            Text(
                text = "Brak rozmów zapisanych przy tym dealu. Przyciskiem wyżej " +
                    "dopiszesz rozmowę, której TeamTalk nie nagrał.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                comms.calls.forEach { CallSummaryRow(it) }
            }
        }
    }
}

@Composable
private fun CallSummaryRow(call: DealCallSummary) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = buildString {
                formatDateTime(call.occurredAt)?.let { append(it) }
                call.phoneNumber?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                append(if (call.manual) " · dopisane ręcznie" else " · z TeamTalka")
                if (call.hasRecording) append(" · nagranie")
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = call.body.ifBlank { "(bez treści)" },
            style = MaterialTheme.typography.bodyMedium,
        )
        call.agreements?.let {
            Spacer(Modifier.height(4.dp))
            InfoRow("Ustalenia", it)
        }
        call.nextStep?.let {
            InfoRow("Następny krok", it)
        }
        if (call.pending) {
            CommsStatusLine(text = "W kolejce — poleci po powrocie zasięgu.", color = SyncBlue)
        }
    }
}

/**
 * Okno dopisania streszczenia — te same trzy pola co w panelu (przebieg,
 * ustalenia, następny krok) plus założenie zadania z następnego kroku. O datę
 * i kierunek rozmowy nie pytamy: wpis dostaje czas zapisu, a że połączenia za
 * nim nie ma, lista oznacza go jako dopisany ręcznie.
 */
@Composable
private fun CallSummaryDialog(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val form = state.comms.callForm ?: return

    AlertDialog(
        onDismissRequest = viewModel::closeCallSummaryForm,
        title = { Text("Streszczenie rozmowy") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Rozmowa, której TeamTalk nie nagrał — z prywatnego, " +
                        "ze stacjonarnego albo u klienta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = form.text,
                    onValueChange = { value -> viewModel.editCallSummaryForm { it.copy(text = value) } },
                    label = { Text("Przebieg rozmowy *") },
                    placeholder = { Text("O czym rozmawialiście…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.agreements,
                    onValueChange = { value ->
                        viewModel.editCallSummaryForm { it.copy(agreements = value) }
                    },
                    label = { Text("Ustalenia") },
                    placeholder = { Text("Ceny, terminy, zakres…") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.nextStep,
                    onValueChange = { value ->
                        viewModel.editCallSummaryForm { it.copy(nextStep = value) }
                    },
                    label = { Text("Następny krok") },
                    placeholder = { Text("Co trzeba zrobić po tej rozmowie…") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = form.createTask && form.hasNextStep,
                        enabled = form.hasNextStep,
                        onCheckedChange = { checked ->
                            viewModel.editCallSummaryForm { it.copy(createTask = checked) }
                        },
                    )
                    Text(
                        text = "Załóż z tego zadanie dla mnie",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (form.hasNextStep) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (form.createTask && form.hasNextStep) {
                    OutlinedTextField(
                        value = form.taskDueAt,
                        onValueChange = { value ->
                            viewModel.editCallSummaryForm { it.copy(taskDueAt = value) }
                        },
                        label = { Text("Termin zadania (RRRR-MM-DD, opcjonalnie)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::saveCallSummary,
                enabled = form.canSave && !state.comms.isSavingCall,
            ) {
                Text(if (state.comms.isSavingCall) "Zapisuję…" else "Zapisz streszczenie")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeCallSummaryForm) { Text("Anuluj") }
        },
    )
}

// ── SMS ──────────────────────────────────────────────────────────────────────

@Composable
private fun SmsChannel() {
    SectionCard {
        SectionTitle("SMS")
        SectionGap()
        Text(
            text = "SMS — w przygotowaniu. Wysyłka i historia SMS-ów tego deala włączą " +
                "się po dopięciu kredencji bramki, tak samo jak w panelu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Wspólne ──────────────────────────────────────────────────────────────────

/** Jednowierszowy komunikat stanu (kolejka, brak zasięgu, odmowa serwera). */
@Composable
private fun CommsStatusLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
