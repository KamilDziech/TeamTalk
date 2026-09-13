package com.ekotak.teamtalk.presentation.crm

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.CategoryNode
import com.ekotak.teamtalk.domain.model.pruneToSelected
import com.ekotak.teamtalk.domain.model.selectedCount
import com.ekotak.teamtalk.domain.model.subtreeIds

/**
 * Drzewo katalogu technologii z zaznaczeniem instalacji — sekcja „Zakres
 * instalacji" zakładki LEAD.
 *
 * Dwie akcje na jednym wierszu: nazwa zaznacza/odznacza instalację, strzałka
 * rozwija gałąź. Rozdzielenie jest konieczne, bo wybór wskazuje węzły dowolnej
 * głębokości — klient bywa zdecydowany na „Ogrzewanie" i niezdecydowany co do
 * marki, więc gałąź musi dać się zaznaczyć bez wchodzenia w nią.
 *
 * Licznik pokazujemy tylko przy kategoriach głównych. Niżej i tak widać
 * zaznaczenia gołym okiem, a przy każdej gałęzi robiłby z listy tabelę liczb.
 *
 * `pickedOnly` (Lead, Remarketing, Audyt, Oferta — 1:1 z panelem): drzewo
 * pokazuje wyłącznie kategorie, którymi klient jest zainteresowany. Nowe
 * dochodzą przyciskiem „+ Dodaj instalację", zbędne schodzą przyciskiem „−"
 * przy kategorii głównej. Na podglądzie (bez edycji) gałęzie są przycięte do
 * samej ścieżki wyboru; przy edycji rozwinięta kategoria pokazuje wszystkie
 * dzieci, bo tak się ją doprecyzowuje.
 */
@Composable
fun InstallationTree(
    nodes: List<CategoryNode>,
    selected: Set<String>,
    expanded: Set<String>,
    editable: Boolean,
    onToggleSelection: (String) -> Unit,
    onToggleBranch: (String) -> Unit,
    pickedOnly: Boolean = false,
    // Zapis w toku (tylko `pickedOnly`): dotyk wyłączony, ale układ drzewa
    // zostaje edycyjny — inaczej na czas zapisu gałęzie skakałyby do podglądu.
    busy: Boolean = false,
    onAddRoot: ((String) -> Unit)? = null,
    onRemoveRoot: ((String) -> Unit)? = null,
) {
    if (pickedOnly) {
        PickedInstallationTree(
            nodes = nodes,
            selected = selected,
            expanded = expanded,
            editable = editable,
            busy = busy,
            onToggleSelection = onToggleSelection,
            onToggleBranch = onToggleBranch,
            onAddRoot = onAddRoot,
            onRemoveRoot = onRemoveRoot,
        )
        return
    }
    Column(Modifier.fillMaxWidth()) {
        nodes.forEach { node ->
            InstallationBranch(
                node = node,
                selected = selected,
                expanded = expanded,
                editable = editable,
                onToggleSelection = onToggleSelection,
                onToggleBranch = onToggleBranch,
            )
        }
    }
}

@Composable
private fun PickedInstallationTree(
    nodes: List<CategoryNode>,
    selected: Set<String>,
    expanded: Set<String>,
    editable: Boolean,
    busy: Boolean,
    onToggleSelection: (String) -> Unit,
    onToggleBranch: (String) -> Unit,
    onAddRoot: ((String) -> Unit)?,
    onRemoveRoot: ((String) -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    // Podgląd: sama ścieżka wyboru, bez rodzeństwa, którego klient nie wziął.
    val shown = if (editable) {
        nodes.filter { it.selectedCount(selected) > 0 }
    } else {
        pruneToSelected(nodes, selected)
    }
    val addable = nodes.filter { it.selectedCount(selected) == 0 }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<CategoryNode?>(null) }
    val canRemove = editable && onRemoveRoot != null

    Column(Modifier.fillMaxWidth()) {
        if (shown.isEmpty()) {
            Text(
                text = "Klient nie wskazał jeszcze żadnej instalacji.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        shown.forEach { root ->
            InstallationBranch(
                node = root,
                selected = selected,
                expanded = expanded,
                editable = editable && !busy,
                onToggleSelection = onToggleSelection,
                onToggleBranch = onToggleBranch,
                onRemoveRoot = if (canRemove) {
                    { id ->
                        // Pytamy tylko wtedy, gdy pod kategorią jest już coś
                        // doprecyzowane — ogólny wpis znika jednym dotknięciem.
                        val refined = root.subtreeIds().any { it != id && it in selected }
                        if (!busy && refined) confirmRemove = root
                        if (!busy && !refined) onRemoveRoot?.invoke(id)
                    }
                } else {
                    null
                },
            )
        }

        if (editable && onAddRoot != null && addable.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            if (adding) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Dodaj instalację",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { adding = false }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Zamknij listę",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    addable.forEach { root ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !busy, onClickLabel = "Dodaj instalację") {
                                    adding = false
                                    onAddRoot(root.id)
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = root.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { adding = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Dodaj instalację")
                }
            }
        }
    }

    confirmRemove?.let { root ->
        val count = root.selectedCount(selected)
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Odjąć „${root.name}”?") },
            text = {
                Text(
                    "Instalacja zniknie z zakresu tego etapu razem z wybranymi " +
                        "pod nią pozycjami ($count).",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = null
                        onRemoveRoot?.invoke(root.id)
                    },
                ) { Text("Odejmij", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Anuluj") }
            },
        )
    }
}

@Composable
private fun InstallationBranch(
    node: CategoryNode,
    selected: Set<String>,
    expanded: Set<String>,
    editable: Boolean,
    onToggleSelection: (String) -> Unit,
    onToggleBranch: (String) -> Unit,
    onRemoveRoot: ((String) -> Unit)? = null,
) {
    InstallationRow(
        node = node,
        isSelected = node.id in selected,
        isExpanded = node.id in expanded,
        // Licznik zaznaczeń w gałęzi ma sens tylko tam, gdzie gałąź bywa
        // zwinięta na wejściu — czyli przy kategoriach głównych.
        branchCount = if (node.depth == 0 && !node.isLeaf) node.selectedCount(selected) else null,
        editable = editable,
        onToggleSelection = { onToggleSelection(node.id) },
        onToggleBranch = { onToggleBranch(node.id) },
        onRemove = onRemoveRoot?.takeIf { node.depth == 0 }?.let { remove -> { remove(node.id) } },
    )

    if (node.id in expanded) {
        node.children.forEach { child ->
            InstallationBranch(
                node = child,
                selected = selected,
                expanded = expanded,
                editable = editable,
                onToggleSelection = onToggleSelection,
                onToggleBranch = onToggleBranch,
            )
        }
    }
}

@Composable
private fun InstallationRow(
    node: CategoryNode,
    isSelected: Boolean,
    isExpanded: Boolean,
    branchCount: Int?,
    editable: Boolean,
    onToggleSelection: () -> Unit,
    onToggleBranch: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (isExpanded) 90f else 0f, label = "gałąź")
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (node.depth * 14).dp, top = 2.dp, bottom = 2.dp)
            .clip(shape)
            .background(if (isSelected) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
            .then(
                if (isSelected) Modifier.border(1.dp, colors.primary.copy(alpha = 0.6f), shape)
                else Modifier,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Strzałka rozwijania stoi przed nazwą i ma własny obszar dotyku —
        // inaczej wejście w gałąź zaznaczałoby ją przy okazji.
        if (node.isLeaf) {
            Spacer(Modifier.width(20.dp))
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) {
                    "Zwiń: ${node.name}"
                } else {
                    "Rozwiń: ${node.name}"
                },
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleBranch),
            )
        }

        Spacer(Modifier.width(6.dp))

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }

        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) colors.primary else colors.onSurface,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    enabled = editable,
                    role = Role.Checkbox,
                    onClickLabel = if (isSelected) "Odznacz instalację" else "Zaznacz instalację",
                    onClick = onToggleSelection,
                )
                .padding(vertical = 2.dp),
        )

        if (branchCount != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = branchCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (branchCount > 0) colors.primary else colors.onSurfaceVariant,
            )
        }

        if (onRemove != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, colors.outline.copy(alpha = 0.5f), CircleShape)
                    .clickable(onClickLabel = "Odejmij instalację", onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Odejmij: ${node.name}",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    if (node.depth == 0) Spacer(Modifier.height(2.dp))
}

/**
 * Karta „Zakres instalacji" zakładek Audyt i Oferta. W panelu to drzewo po
 * lewej od formularza audytu / wyceny; telefon nie zmieści ich obok siebie,
 * więc karta stoi nad nimi. Zasady jak w LEAD: same instalacje klienta,
 * „+ Dodaj instalację" i „−", zmiana tylko na etapie, na którym deal stoi.
 */
@Composable
fun InstallationScopeCard(
    scope: DealDetailViewModel.InstallationScopeState,
    canManage: Boolean,
    stageLabel: String,
    onToggleSelection: (String) -> Unit,
    onToggleBranch: (String) -> Unit,
    onAddRoot: (String) -> Unit,
    onRemoveRoot: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val selected = scope.selected

    SectionCard {
        SectionTitle(
            text = "Zakres instalacji",
            accent = selected?.size?.takeIf { it > 0 }?.let { "$it wybrane" },
        )
        SectionGap()

        if (selected == null || scope.catalog.isEmpty()) {
            Text(
                text = "Nie udało się wczytać zakresu instalacji.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            return@SectionCard
        }

        InstallationTree(
            nodes = scope.catalog,
            selected = selected,
            expanded = scope.expanded,
            editable = canManage && scope.editable,
            onToggleSelection = onToggleSelection,
            onToggleBranch = onToggleBranch,
            pickedOnly = true,
            busy = scope.isSaving,
            onAddRoot = onAddRoot,
            onRemoveRoot = onRemoveRoot,
        )

        if (scope.pendingSync) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Zapisane na telefonie — pójdzie na serwer, gdy wróci zasięg.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        if (!canManage || !scope.editable) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (!canManage) {
                    "Podgląd — zmiana zakresu wymaga uprawnienia do edycji dealów."
                } else {
                    "Podgląd — zakres etapu „$stageLabel” zmienia się tylko wtedy, " +
                        "gdy deal stoi na tym etapie."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
