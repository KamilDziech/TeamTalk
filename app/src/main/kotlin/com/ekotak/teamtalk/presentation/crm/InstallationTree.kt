package com.ekotak.teamtalk.presentation.crm

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.CategoryNode
import com.ekotak.teamtalk.domain.model.selectedCount

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
 */
@Composable
fun InstallationTree(
    nodes: List<CategoryNode>,
    selected: Set<String>,
    expanded: Set<String>,
    editable: Boolean,
    onToggleSelection: (String) -> Unit,
    onToggleBranch: (String) -> Unit,
) {
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
private fun InstallationBranch(
    node: CategoryNode,
    selected: Set<String>,
    expanded: Set<String>,
    editable: Boolean,
    onToggleSelection: (String) -> Unit,
    onToggleBranch: (String) -> Unit,
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
    }

    if (node.depth == 0) Spacer(Modifier.height(2.dp))
}
