package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.CategoryNode
import com.ekotak.teamtalk.domain.model.DocumentCategory

/**
 * Zakładka „Remarketing" karty deala — mobilny odpowiednik zakładki `edukacja`
 * z `DealDrawer` panelu.
 *
 * To ten sam układ co LEAD, ale bez artykułów wiedzy i bez ikonografiki
 * budynku (w panelu decyduje o tym `detailMode = "none"`). Remarketing jest
 * przystankiem dla leada, który nie dojechał do audytu w oknie automatu lejka:
 * zakres instalacji dostaje TU WŁASNĄ migawkę — dziedziczoną z LEAD-a, ale
 * edytowalną niezależnie, żeby zmiana zdania klienta po tygodniach nie
 * przepisywała historii tego, czego chciał na starcie.
 *
 * Kolejność bloków idzie od tego, co ustala się przy kliencie, do tego, co ma
 * mu pomóc wrócić do rozmowy:
 *  1. rodzaj budynku — jak w LEAD, jedno dotknięcie i tor rozmowy,
 *  2. zakres instalacji tego etapu, edytowalny na miejscu,
 *  3. materiały: „+ Projekt domu" (z aparatem — projekt leży u klienta na
 *     stole, nie w chmurze) i „+ OZC", gdy w zakresie jest ogrzewanie,
 *  4. spotkanie wstępne — jedyny termin, który da się tu jeszcze umówić.
 *
 * Wszystko zapisuje się od razu, a bez zasięgu ląduje w kolejce: rozmowa
 * remarketingowa bywa dogrywana u klienta, gdzie zasięgu nie ma.
 */
@Composable
fun DealRemarketingTab(
    state: DealDetailViewModel.UiState,
    onEdit: () -> Unit,
    onUploaded: () -> Unit,
    viewModel: DealDetailViewModel,
) {
    val detail = state.detail ?: return
    val remarketing = state.remarketing

    // Aparat/wybór plików ustawiamy tak jak w zakładce „Pliki" — cel jest
    // stały (sekcja „Projekt domu"), więc wystarczy jedno wywołanie.
    val pickers = rememberFilePickers { picked ->
        picked.forEach {
            viewModel.uploadFile(
                name = it.name,
                contentType = it.contentType,
                bytes = it.bytes,
                category = DocumentCategory.PROJEKT,
            )
        }
        if (picked.isNotEmpty()) onUploaded()
    }

    var ozcOpen by remember { mutableStateOf(false) }

    BuildingKindCard(
        deal = detail.deal,
        canManage = state.canManage,
        isSaving = state.isSaving,
        onSelect = viewModel::setBuildingKind,
    )
    SectionGap()

    RemarketingScopeCard(
        state = state,
        onToggleSelection = viewModel::toggleRemarketingInstallation,
        onToggleBranch = viewModel::toggleRemarketingBranch,
        onRetry = { viewModel.loadRemarketing(force = true) },
    )
    SectionGap()

    MaterialsCard(
        canManage = state.canManage,
        busy = state.files.busy,
        // „+ OZC" tylko wtedy, gdy klient ma w zakresie cokolwiek z gałęzi
        // „Ogrzewanie" — ta sama bramka co `stageHasHeating` w panelu.
        showOzc = hasHeating(remarketing.catalog, remarketing.selected.orEmpty()),
        ozcSummary = ozcSummary(detail.deal.ozcData),
        onPickProject = pickers::pickFiles,
        onPhotoProject = pickers::takePhoto,
        onOzc = { ozcOpen = true },
    )
    SectionGap()

    MeetingCard(
        deal = detail.deal,
        members = state.members,
        canManage = state.canManage,
        isSaving = state.isSaving,
        onKindSelect = viewModel::setMeetingKind,
        onTermChange = viewModel::setMeetingAt,
        onEdit = onEdit,
    )

    if (ozcOpen) {
        OzcDialog(
            state = state,
            onDismiss = { ozcOpen = false },
            onSave = { kw, dhw, url, confirmed, area ->
                viewModel.saveOzc(kw, dhw, url, confirmed, area) { ozcOpen = false }
            },
        )
    }
}

// ── Zakres instalacji etapu ──────────────────────────────────────────────────

/**
 * Drzewo katalogu z migawką etapu `edukacja`. Osobne od karty LEAD, choć
 * wygląda tak samo: to inna migawka, więc dzielenie stanu pokazywałoby na obu
 * zakładkach ten sam wybór.
 *
 * Zmieniać wolno tylko wtedy, gdy deal STOI na Remarketingu — API oddaje to
 * w `editable`, my dokładamy `deal.manage`. Podgląd zostaje zawsze: zakres
 * z remarketingu wchodzi do audytu, więc audytor chce go zobaczyć także wtedy,
 * gdy deal poszedł dalej.
 */
@Composable
private fun RemarketingScopeCard(
    state: DealDetailViewModel.UiState,
    onToggleSelection: (String) -> Unit,
    onToggleBranch: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val remarketing = state.remarketing
    val selected = remarketing.selected
    val editable = state.canManage && remarketing.editable && !remarketing.isSaving

    SectionCard {
        SectionTitle(
            text = "Zakres instalacji",
            accent = selected?.size?.takeIf { it > 0 }?.let { "$it wybrane" },
        )
        SectionGap()

        when {
            !remarketing.loaded -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }

            remarketing.error != null || selected == null || remarketing.catalog.isEmpty() -> {
                Text(
                    text = remarketing.error
                        ?: "Nie udało się wczytać katalogu instalacji.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry) { Text("Spróbuj ponownie") }
            }

            else -> {
                InstallationTree(
                    nodes = remarketing.catalog,
                    selected = selected,
                    expanded = remarketing.expanded,
                    editable = editable,
                    onToggleSelection = onToggleSelection,
                    onToggleBranch = onToggleBranch,
                )

                if (remarketing.pendingSync) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Zapisane na telefonie — pójdzie na serwer, " +
                                "gdy wróci zasięg.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!state.canManage || !remarketing.editable) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (!state.canManage) {
                            "Podgląd — zmiana zakresu wymaga uprawnienia do edycji dealów."
                        } else {
                            "Migawka Remarketingu jest zamknięta — zakres zmienia się " +
                                "na etapie, na którym deal stoi."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Materiały: projekt domu i OZC ────────────────────────────────────────────

/**
 * Dwa przyciski, które w panelu stoją obok „+ Spotkanie": wgranie projektu domu
 * i okno OZC. Aparat obok pierwszego to różnica wobec panelu — projekt leży
 * u klienta na stole, a nie w chmurze, więc na telefonie da się go po prostu
 * sfotografować.
 */
@Composable
private fun MaterialsCard(
    canManage: Boolean,
    busy: Boolean,
    showOzc: Boolean,
    ozcSummary: String?,
    onPickProject: () -> Unit,
    onPhotoProject: () -> Unit,
    onOzc: () -> Unit,
) {
    if (!canManage) return

    SectionCard {
        SectionTitle("Materiały")
        SectionGap()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPickProject,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Home, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("+ Projekt domu")
            }
            OutlinedButton(onClick = onPhotoProject, enabled = !busy) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Sfotografuj projekt domu",
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Wgrane pliki trafiają do sekcji „Projekt domu” w zakładce Pliki.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (showOzc) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOzc, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("+ OZC")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = ozcSummary
                    ?: "Zapotrzebowanie na ciepło przepisane z cieplo.app — jeszcze puste.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Czy w zaznaczeniu jest cokolwiek z gałęzi „Ogrzewanie". Wybór wskazuje węzły
 * DOWOLNEJ głębokości, więc pytamy o korzeń poddrzewa — dokładnie tak jak
 * `stageHasHeating` w panelu (porównanie nazwy korzenia, bez rozróżniania
 * wielkości liter).
 */
private fun hasHeating(catalog: List<CategoryNode>, selected: Set<String>): Boolean {
    if (selected.isEmpty()) return false

    fun anySelected(node: CategoryNode): Boolean =
        node.id in selected || node.children.any(::anySelected)

    return catalog.any { root ->
        root.name.trim().lowercase() == "ogrzewanie" && anySelected(root)
    }
}
