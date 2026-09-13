package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.AuditPhotoMeta
import com.ekotak.teamtalk.domain.model.AuditPhotoPlan
import com.ekotak.teamtalk.domain.model.AuditPhotoRole
import com.ekotak.teamtalk.domain.model.BUILDING_SCOPE
import com.ekotak.teamtalk.domain.model.DealDocument
import com.ekotak.teamtalk.domain.model.ExtraPhoto
import com.ekotak.teamtalk.domain.model.PhotoSlot

/**
 * Karta „Zdjęcia" zakładki „Audyt" — kadry budynku (wspólne dla wszystkich
 * instalacji deala), po jednym na każdy rozdzielacz z formularza i dowolne
 * dodatkowe z opisem.
 *
 * Pusty kafelek to nie brak danych, tylko POLECENIE: „zrób to zdjęcie". Klik
 * otwiera od razu aparat — na budowie nikt nie szuka menu, a zdjęcie robi się,
 * stojąc przed rozdzielaczem.
 *
 * BLOKADA OFERTY TU NIE SIĘGA. Formularz po podpisaniu umowy jest do odczytu,
 * bo z niego liczy się cena; zdjęcie nie przelicza niczego, a montaż i serwis
 * go potrzebują — więc kadr i opis wolno dorobić także po podpisie.
 */
@Composable
fun AuditPhotosCard(
    plan: AuditPhotoPlan,
    /** Ścieżka wybranej instalacji — czyje to rozdzielacze. */
    installationLabel: String?,
    /** Id instalacji; `null` = kadry rozdzielaczy nie mają do czego należeć. */
    installationId: String?,
    canManage: Boolean,
    busy: Boolean,
    onTake: (ByteArray, AuditPhotoMeta, String) -> Unit,
    onSaveNote: (DealDocument, AuditPhotoMeta, String) -> Unit,
    onDelete: (DealDocument) -> Unit,
) {
    /** Kafelek, dla którego otwarto aparat — kadr wraca z wybieraka bez adresu. */
    var pending by remember { mutableStateOf<Pair<AuditPhotoMeta, String>?>(null) }
    /** Otwarty kadr (podgląd + opis + akcje). */
    var opened by remember { mutableStateOf<OpenedPhoto?>(null) }
    /** Kadr czekający na potwierdzenie usunięcia. */
    var toDelete by remember { mutableStateOf<OpenedPhoto?>(null) }
    /** Zakres nowego zdjęcia dodatkowego: budynek czy ta jedna instalacja. */
    var extraShared by remember { mutableStateOf(false) }

    val pickers = rememberFilePickers { picked ->
        val target = pending
        pending = null
        val file = picked.firstOrNull() ?: return@rememberFilePickers
        if (target != null) onTake(file.bytes, target.first, target.second)
    }

    fun shoot(meta: AuditPhotoMeta, label: String) {
        pending = meta to label
        pickers.takePhoto()
    }

    SectionCard {
        SectionTitle(
            text = "Zdjęcia",
            accent = when {
                // Kolejka przed licznikiem: „czeka na wysyłkę" jest dla audytora
                // ważniejsze niż to, ilu kadrów brakuje.
                plan.pendingCount > 0 -> "${plan.done}/${plan.required} · czeka na wysyłkę"
                plan.required == 0 -> null
                plan.done < plan.required -> "${plan.done} z ${plan.required}"
                else -> "komplet (${plan.required})"
            },
        )
        installationLabel?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionGap()

        PhotoGroupHeader(
            title = "Budynek",
            badge = "wspólne",
            hint = "te same kadry widać przy każdej instalacji tego deala",
        )
        PhotoGrid(
            slots = plan.building,
            enabled = canManage && !busy,
            onShoot = ::shoot,
            onOpen = { slot, document ->
                opened = OpenedPhoto(document, slot.meta, slot.label, slot.shared)
            },
        )

        plan.manifolds.forEach { group ->
            Spacer(Modifier.height(12.dp))
            PhotoGroupHeader(
                title = group.floorLabel,
                badge = "rozdzielacze",
                hint = if (group.slots.size == 1) {
                    "1 kadr — tyle, ile rozdzielaczy w formularzu"
                } else {
                    "${group.slots.size} kadry — tyle, ile rozdzielaczy w formularzu"
                },
            )
            PhotoGrid(
                slots = group.slots,
                enabled = canManage && !busy,
                onShoot = ::shoot,
                onOpen = { slot, document ->
                    opened = OpenedPhoto(document, slot.meta, slot.label, slot.shared)
                },
            )
        }

        if (installationId != null && plan.manifolds.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Formularz nie ma jeszcze rozdzielaczy — wpisz ich ilość na " +
                    "kondygnacjach, a kadry pojawią się tutaj same.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        PhotoGroupHeader(title = "Dodatkowe", badge = null, hint = "bez limitu, każde z opisem")
        ExtrasGrid(
            extras = plan.extras,
            enabled = canManage && !busy,
            onOpen = { extra ->
                opened = OpenedPhoto(
                    extra.document,
                    extra.meta,
                    extra.meta.note.ifBlank { "Zdjęcie dodatkowe" },
                    extra.shared,
                )
            },
            onAdd = {
                val scope = if (extraShared || installationId == null) {
                    BUILDING_SCOPE
                } else {
                    installationId
                }
                shoot(
                    AuditPhotoMeta(scope = scope, role = AuditPhotoRole.DODATKOWE),
                    "Zdjęcie dodatkowe",
                )
            },
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Dodatkowe dotyczy:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            ChoicePill(
                label = "tej instalacji",
                selected = !extraShared,
                onClick = { extraShared = false },
                enabled = canManage && installationId != null,
            )
            Spacer(Modifier.width(6.dp))
            ChoicePill(
                label = "budynku",
                selected = extraShared,
                onClick = { extraShared = true },
                enabled = canManage,
            )
        }

        if (plan.required > 0 && plan.done < plan.required) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Brakuje ${plan.required - plan.done} z ${plan.required} wymaganych " +
                    "zdjęć — audyt zapiszesz, ale nie jest udokumentowany.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }

    opened?.let { photo ->
        PhotoDialog(
            photo = photo,
            canManage = canManage,
            onDismiss = { opened = null },
            onSaveNote = { note ->
                onSaveNote(photo.document, photo.meta, note)
                opened = null
            },
            onRetake = {
                opened = null
                shoot(photo.meta, photo.label)
            },
            onDelete = {
                opened = null
                toDelete = photo
            },
        )
    }

    toDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Usunąć zdjęcie?") },
            text = {
                Text(
                    if (photo.shared) {
                        "„${photo.label}” to kadr wspólny — zniknie przy KAŻDEJ instalacji " +
                            "tego deala."
                    } else {
                        "„${photo.label}” zniknie z audytu i z plików deala."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(photo.document)
                    toDelete = null
                }) { Text("Usuń") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Zostaw") }
            },
        )
    }
}

/** Kadr otwarty do obejrzenia i opisania. */
private data class OpenedPhoto(
    val document: DealDocument,
    val meta: AuditPhotoMeta,
    val label: String,
    val shared: Boolean,
)

@Composable
private fun PhotoGroupHeader(title: String, badge: String?, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        badge?.let {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
    Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
}

/**
 * Siatka kadrów po dwa w rzędzie. Zwykłe wiersze, a nie `LazyVerticalGrid`:
 * karta stoi w przewijanej kolumnie zakładki, a siatka leniwa w takim miejscu
 * nie ma wysokości, którą mogłaby sobie policzyć.
 */
@Composable
private fun PhotoGrid(
    slots: List<PhotoSlot>,
    enabled: Boolean,
    onShoot: (AuditPhotoMeta, String) -> Unit,
    onOpen: (PhotoSlot, DealDocument) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { slot ->
                    Box(Modifier.weight(1f)) {
                        PhotoTile(
                            label = slot.label,
                            hint = slot.hint,
                            required = slot.required,
                            document = slot.document,
                            enabled = enabled,
                            onClick = {
                                val document = slot.document
                                if (document != null) onOpen(slot, document)
                                else onShoot(slot.meta, slot.label)
                            },
                        )
                    }
                }
                // Nieparzysta liczba kadrów — puste miejsce trzyma szerokość
                // kolumny, żeby ostatni kafelek nie rozlał się na całą kartę.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExtrasGrid(
    extras: List<ExtraPhoto>,
    enabled: Boolean,
    onOpen: (ExtraPhoto) -> Unit,
    onAdd: () -> Unit,
) {
    // „Dodaj" jest ostatnim kafelkiem siatki, a nie przyciskiem pod nią —
    // wtedy ruch ręki jest ten sam co przy kadrach wymaganych.
    val cells: List<ExtraPhoto?> = extras + listOf(null)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { extra ->
                    Box(Modifier.weight(1f)) {
                        if (extra == null) {
                            PhotoTile(
                                label = "Dodaj zdjęcie",
                                hint = "z opisem",
                                required = false,
                                document = null,
                                enabled = enabled,
                                addIcon = true,
                                onClick = onAdd,
                            )
                        } else {
                            PhotoTile(
                                label = extra.meta.note.ifBlank { "Zdjęcie dodatkowe" },
                                hint = listOfNotNull(
                                    if (extra.shared) "wspólne" else null,
                                    extra.orphanOf,
                                ).joinToString(" · ").ifBlank { "dotyczy tej instalacji" },
                                required = false,
                                document = extra.document,
                                enabled = enabled,
                                onClick = { onOpen(extra) },
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Kafelek kadru. Pusty i WYMAGANY dostaje obwódkę koloru ostrzeżenia — po niej
 * widać z metra, ile jeszcze roboty; pusty i podpowiadany zostaje szary.
 */
@Composable
private fun PhotoTile(
    label: String,
    hint: String,
    required: Boolean,
    document: DealDocument?,
    enabled: Boolean,
    onClick: () -> Unit,
    addIcon: Boolean = false,
) {
    val needed = document == null && required
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (document == null) {
                        Modifier.border(
                            width = 1.dp,
                            color = if (needed) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = RoundedCornerShape(10.dp),
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (document != null) {
                DocumentThumb(document = document, modifier = Modifier.fillMaxWidth())
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (document.pending) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (document.pending) {
                            Icons.Default.Schedule
                        } else {
                            Icons.Default.Check
                        },
                        contentDescription = if (document.pending) "Czeka na wysyłkę" else "Jest",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            } else {
                Icon(
                    imageVector = if (addIcon) Icons.Default.Add else Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = if (needed) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (needed) "wymagane · $hint" else hint,
            style = MaterialTheme.typography.labelSmall,
            color = if (needed) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Otwarty kadr: podgląd, opis i dwie decyzje — poprawić albo usunąć. */
@Composable
private fun PhotoDialog(
    photo: OpenedPhoto,
    canManage: Boolean,
    onDismiss: () -> Unit,
    onSaveNote: (String) -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit,
) {
    var note by remember(photo.document.id) { mutableStateOf(photo.meta.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(photo.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                DocumentThumb(
                    document = photo.document,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(10.dp)),
                    targetPx = 900,
                )
                if (photo.document.pending) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Kadr czeka na wysyłkę — pójdzie sam, gdy wróci zasięg.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Opis") },
                    placeholder = { Text("Co widać i dlaczego to ważne") },
                    enabled = canManage,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                if (canManage) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onRetake) { Text("Zrób ponownie") }
                        TextButton(onClick = onDelete) { Text("Usuń") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSaveNote(note) },
                enabled = canManage,
            ) { Text("Zapisz opis") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}
