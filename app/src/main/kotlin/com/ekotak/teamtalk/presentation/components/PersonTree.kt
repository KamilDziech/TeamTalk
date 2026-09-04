package com.ekotak.teamtalk.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.Department
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.departmentOf
import com.ekotak.teamtalk.domain.model.groupMembersByDepartment

/**
 * Wybór w drzewie osób. Wspólny dla Zadań, Kalendarza i Mapy — te ekrany pytają
 * o to samo („kogo pokazujemy"), więc mają mieć jeden kształt filtra.
 */
sealed interface PersonScope {
    /** Zalogowany użytkownik. */
    data object Mine : PersonScope

    /** Bez zawężenia. */
    data object All : PersonScope

    /** Rekordy bez osoby — tylko tam, gdzie to ma sens (zadania). */
    data object Unassigned : PersonScope

    /** Cały dział. */
    data class Dept(val department: Department) : PersonScope

    data class Person(val id: String) : PersonScope
}

/**
 * Drzewo osób: Moje / Wszyscy [/ Nieprzypisane] → szukajka → działy (Biuro,
 * Serwis, Montaż, Pozostali) rozwijane w miejscu, w środku osoby.
 *
 * Ustalenia 2026-09-04:
 *  • akordeon, nie osobna plansza z „Wstecz" — dział rozwija się w miejscu,
 *    pozostałe zostają widoczne (naraz otwarty jeden),
 *  • KLIKNIĘCIE DZIAŁU ROZWIJA go na osoby; filtr „cały dział" siedzi jako
 *    pierwsza pozycja w środku („Wszyscy z Biura"). Jeden cel dotyku na wiersz
 *    — wcześniejszy podział na „etykieta filtruje, strzałka rozwija" mieszał
 *    dwie akcje w jednej linijce,
 *  • jeden wybór naraz,
 *  • puste działy zostają widoczne, żeby lista miała stały kształt.
 *
 * @param me zalogowany użytkownik — jego awatar stoi przy „Moje".
 * @param counts opcjonalne liczniki przy osobach (Mapa liczy punkty); dział
 *   pokazuje wtedy sumę swoich osób, a bez liczników — liczbę osób.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonTree(
    members: List<TaskMember>,
    selected: PersonScope,
    onSelect: (PersonScope) -> Unit,
    modifier: Modifier = Modifier,
    me: TaskMember? = null,
    mineLabel: String? = "Moje",
    allLabel: String = "Wszyscy",
    unassignedLabel: String? = null,
    counts: Map<String, Int> = emptyMap(),
) {
    var query by remember { mutableStateOf("") }
    val groups = remember(members) { groupMembersByDepartment(members) }

    // Akordeon: otwarty najwyżej jeden dział. Start od tego, w którym stoi
    // bieżący wybór — inaczej po otwarciu filtra nie widać, gdzie się jest.
    // Kluczem jest wybór i LICZEBNOŚĆ listy, nie sama lista: Mapa składa ją na
    // nowo przy każdym przeliczeniu i rozwinięty dział zwijałby się po każdym
    // ruchu suwakiem promienia.
    var openDept by remember(selected, members.size) {
        mutableStateOf(
            when (selected) {
                is PersonScope.Dept -> selected.department
                is PersonScope.Person ->
                    members.firstOrNull { it.id == selected.id }?.let(::departmentOf)
                else -> null
            },
        )
    }

    val needle = query.trim().lowercase()
    val matches = remember(needle, groups) {
        if (needle.isBlank()) {
            null
        } else {
            groups.entries.flatMap { (dept, people) ->
                people
                    .filter {
                        it.displayName.lowercase().contains(needle) ||
                            it.email.lowercase().contains(needle)
                    }
                    .map { dept to it }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (mineLabel != null) {
                FilterChip(
                    selected = selected == PersonScope.Mine,
                    onClick = { onSelect(PersonScope.Mine) },
                    label = { Text(mineLabel) },
                    // Awatar przy „Moje" — od razu widać, na kogo patrzysz.
                    // Zdjęcie to samo co w panelu (jedno API); bez zdjęcia —
                    // kółko z inicjałami.
                    leadingIcon = me?.let { { UserAvatar(it.id, it.initials, size = 20.dp) } },
                )
            }
            FilterChip(
                selected = selected == PersonScope.All,
                onClick = { onSelect(PersonScope.All) },
                label = { Text(allLabel) },
            )
            if (unassignedLabel != null) {
                FilterChip(
                    selected = selected == PersonScope.Unassigned,
                    onClick = { onSelect(PersonScope.Unassigned) },
                    label = { Text(unassignedLabel) },
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Szukaj osoby") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (matches != null) {
            // Wynik szukania idzie płasko, ponad działami — z podpisem działu,
            // żeby było wiadomo, skąd ta osoba.
            if (matches.isEmpty()) {
                EmptyNote("Nikogo nie znaleziono")
            } else {
                matches.forEach { (dept, member) ->
                    PersonRow(
                        member = member,
                        note = dept.label,
                        count = counts[member.id],
                        selected = selected == PersonScope.Person(member.id),
                        onClick = { onSelect(PersonScope.Person(member.id)) },
                    )
                }
            }
            return@Column
        }

        Department.entries.forEach { dept ->
            val people = groups[dept].orEmpty()
            val open = openDept == dept
            DeptRow(
                label = dept.label,
                count = if (counts.isEmpty()) people.size else people.sumOf { counts[it.id] ?: 0 },
                open = open,
                enabled = people.isNotEmpty(),
                selected = selected == PersonScope.Dept(dept),
                onToggle = { openDept = if (open) null else dept },
            )
            if (open) {
                if (people.isEmpty()) {
                    EmptyNote("Brak osób w dziale")
                } else {
                    // Filtr „cały dział" jako pierwsza pozycja w środku — klik
                    // w nagłówek służy już do rozwijania.
                    DeptAllRow(
                        label = "Wszyscy z działu ${dept.label}",
                        selected = selected == PersonScope.Dept(dept),
                        onClick = { onSelect(PersonScope.Dept(dept)) },
                    )
                    people.forEach { member ->
                        PersonRow(
                            member = member,
                            note = null,
                            count = counts[member.id],
                            selected = selected == PersonScope.Person(member.id),
                            onClick = { onSelect(PersonScope.Person(member.id)) },
                            indent = true,
                        )
                    }
                }
            }
        }
    }
}

/** Nagłówek działu — cały wiersz rozwija i zwija listę osób. */
@Composable
private fun DeptRow(
    label: String,
    count: Int,
    open: Boolean,
    enabled: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 10.dp),
    ) {
        Icon(
            imageVector = if (open) {
                Icons.Filled.KeyboardArrowDown
            } else {
                Icons.AutoMirrored.Filled.KeyboardArrowRight
            },
            contentDescription = if (open) "Zwiń dział $label" else "Rozwiń dział $label",
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            // Wersalikami — dział ma się czytać jako grupa, nie jako nazwisko.
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** „Wszyscy z działu …" — filtr obejmujący cały dział, w środku rozwinięcia. */
@Composable
private fun DeptAllRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 30.dp)
            .padding(vertical = 6.dp),
    )
}


@Composable
private fun PersonRow(
    member: TaskMember,
    note: String?,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    indent: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (indent) 24.dp else 0.dp)
            .padding(vertical = 6.dp),
    ) {
        UserAvatar(member.id, member.initials, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = member.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp),
    )
}
