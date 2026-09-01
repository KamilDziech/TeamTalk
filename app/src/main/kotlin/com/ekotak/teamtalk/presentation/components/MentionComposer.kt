package com.ekotak.teamtalk.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.DEFAULT_MENTION_GROUPS
import com.ekotak.teamtalk.domain.model.MentionTarget
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.presentation.theme.EkotakGreen

/**
 * Ile znaków po „@" jeszcze traktujemy jak szukanie osoby. Dłuższy ciąg to już
 * zwykłe zdanie z małpą (np. adres e-mail wklejony w komentarz).
 */
private const val MAX_QUERY = 30

/**
 * Dopasowanie etykiety do zapytania — im niżej, tym lepiej: 0 = dokładnie,
 * 1 = początek etykiety, 2 = początek słowa (czyli NAZWISKO — o to chodzi:
 * „@kowal" ma trafiać w „Jan Kowalski"), 3+ = trafienie w środku, −1 = brak.
 * Lustro `matchScore` z `web/src/components/MentionTextarea.tsx`.
 */
private fun matchScore(label: String, query: String): Double {
    val l = label.lowercase()
    val q = query.lowercase()
    if (q.isEmpty()) return 1.0
    if (l == q) return 0.0
    if (l.startsWith(q)) return 1.0
    if (l.split(' ', '.', '-').any { it.startsWith(q) }) return 2.0
    val idx = l.indexOf(q)
    return if (idx >= 0) 3.0 + idx / 100.0 else -1.0
}

/**
 * Stan pola komentarza z wywołaniami. Tekst niesie „@Imię Nazwisko" (człowiek
 * ma widzieć, kogo woła), a osobno trzymamy TOKENY — dzięki temu zmiana
 * nazwiska w kadrach nie psuje starych wzmianek, a backend nie zgaduje po
 * tekście. Wywołanie skasowane z tekstu przestaje się liczyć: [tokens] patrzy,
 * czy etykieta nadal w nim jest (tak samo robi panel).
 */
@Stable
class MentionState {
    var input by mutableStateOf(TextFieldValue(""))
        private set

    private val picked = mutableStateMapOf<String, String>() // etykieta → token

    val text: String get() = input.text

    val tokens: List<String>
        get() = picked.filterKeys { text.contains("@" + it) }.values.distinct()

    /** Fragment po „@" tuż przed kursorem — null, gdy nie wołamy nikogo. */
    val query: String?
        get() {
            val cursor = input.selection.start.coerceIn(0, text.length)
            val before = text.take(cursor)
            val at = before.lastIndexOf('@')
            if (at < 0) return null
            if (at > 0 && !before[at - 1].isWhitespace()) return null
            val frag = before.substring(at + 1)
            if (frag.length > MAX_QUERY || frag.contains('\n') || frag.count { it == ' ' } > 1) {
                return null
            }
            return frag
        }

    fun onChange(value: TextFieldValue) {
        input = value
    }

    /** Wstawia „@Etykieta " w miejsce pisanego zapytania i zapamiętuje token. */
    fun pick(target: MentionTarget) {
        val cursor = input.selection.start.coerceIn(0, text.length)
        val at = text.take(cursor).lastIndexOf('@')
        if (at < 0) return
        val inserted = "@" + target.label + " "
        val newText = text.take(at) + inserted + text.substring(cursor)
        picked[target.label] = target.token
        input = TextFieldValue(newText, TextRange(at + inserted.length))
    }

    fun clear() {
        input = TextFieldValue("")
        picked.clear()
    }
}

@Composable
fun rememberMentionState(): MentionState = remember { MentionState() }

/**
 * Pole komentarza z listą podpowiedzi po „@": osoby z zespołu i grupy
 * (rola / obserwujący / wszyscy). Lista otwiera się NAD polem — pod nim jest
 * klawiatura.
 */
@Composable
fun MentionComposer(
    state: MentionState,
    members: List<TaskMember>,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    sending: Boolean = false,
    placeholder: String = "Napisz komentarz… (@ wywołuje osobę)",
) {
    val query = state.query
    val suggestions: List<MentionTarget> = remember(query, members) {
        if (query == null) {
            emptyList()
        } else {
            val people = members.map { MentionTarget.Person(it) as MentionTarget }
            val groups = DEFAULT_MENTION_GROUPS.map { it as MentionTarget }
            (people + groups)
                .map { it to matchScore(it.label, query) }
                .filter { it.second >= 0 }
                .sortedWith(compareBy({ it.second }, { it.first.label.lowercase() }))
                .map { it.first }
                .take(6)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (suggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(suggestions, key = { it.token }) { target ->
                        MentionRow(target) { state.pick(target) }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = state.input,
                onValueChange = state::onChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
                maxLines = 4,
                enabled = !sending,
            )
            IconButton(
                onClick = onSend,
                enabled = !sending && state.text.isNotBlank(),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Wyślij komentarz",
                        tint = EkotakGreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionRow(target: MentionTarget, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (target) {
            is MentionTarget.Person -> Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(EkotakGreen.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    target.member.initials,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            is MentionTarget.Group -> Icon(
                Icons.Filled.Group,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                target.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val hint = when (target) {
                is MentionTarget.Person -> target.member.email
                is MentionTarget.Group -> target.hint
            }
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
