package com.ekotak.teamtalk.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.ChatPerson

/** Kolejność działów — ta sama, co w filtrze osób w Zadaniach. */
private val DEPARTMENTS = listOf(
    "biuro" to "Biuro",
    "serwis" to "Serwis",
    "montaz" to "Montaż",
    "pozostali" to "Pozostali",
)

/**
 * Nowa rozmowa. Kliknięcie w osobę otwiera czat od razu (jak WhatsApp);
 * grupę i kanał składa się z zaznaczonych osób i nazwy.
 *
 * Pisać może każdy z każdym (decyzja z 2026-09-23), więc lista nikogo nie
 * odsiewa — dział służy wyłącznie do porządku.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatSheet(
    people: List<ChatPerson>,
    onDismiss: () -> Unit,
    onPickPerson: (ChatPerson) -> Unit,
    onCreateGroup: (channel: Boolean, title: String, memberIds: List<String>) -> Unit,
) {
    var mode by remember { mutableStateOf(Mode.PERSON) }
    var query by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(emptySet<String>()) }

    val filtered = people.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) ||
            it.email.contains(query, ignoreCase = true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(
                text = when (mode) {
                    Mode.PERSON -> "Nowa rozmowa"
                    Mode.GROUP -> "Nowa grupa"
                    Mode.CHANNEL -> "Nowy kanał ogłoszeń"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))

            if (mode == Mode.PERSON) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { mode = Mode.GROUP }) { Text("Nowa grupa") }
                    TextButton(onClick = { mode = Mode.CHANNEL }) { Text("Kanał ogłoszeń") }
                }
            } else {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = {
                        Text(
                            if (mode == Mode.GROUP) "Nazwa grupy" else "Nazwa kanału",
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                if (mode == Mode.CHANNEL) {
                    Text(
                        text = "W kanale piszesz tylko Ty. Reszta czyta i reaguje.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Szukaj osoby") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )

            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                DEPARTMENTS.forEach { (key, label) ->
                    val group = filtered.filter { it.department == key }
                    if (group.isEmpty()) return@forEach
                    item(key = "head-$key") {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                    items(group, key = { it.id }) { person ->
                        PersonRow(
                            person = person,
                            selected = picked.contains(person.id),
                            selectable = mode != Mode.PERSON,
                        ) {
                            if (mode == Mode.PERSON) {
                                onPickPerson(person)
                            } else {
                                picked = if (picked.contains(person.id)) {
                                    picked - person.id
                                } else {
                                    picked + person.id
                                }
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item { Text("Nikogo takiego nie ma.", modifier = Modifier.padding(16.dp)) }
                }
            }

            if (mode != Mode.PERSON) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { mode = Mode.PERSON }) { Text("Wróć") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = title.isNotBlank() && picked.isNotEmpty(),
                        onClick = { onCreateGroup(mode == Mode.CHANNEL, title.trim(), picked.toList()) },
                    ) { Text("Załóż") }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(
    person: ChatPerson,
    selected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(seed = person.id, initials = person.initials, size = 36)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                person.email,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selectable && selected) Text("✓", style = MaterialTheme.typography.titleMedium)
    }
}

private enum class Mode { PERSON, GROUP, CHANNEL }
