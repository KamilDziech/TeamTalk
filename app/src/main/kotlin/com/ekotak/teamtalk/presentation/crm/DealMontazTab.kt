package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.Category
import com.ekotak.teamtalk.domain.model.DealDocument
import com.ekotak.teamtalk.domain.model.DocumentCategory
import com.ekotak.teamtalk.domain.model.MaterialStatus
import com.ekotak.teamtalk.domain.model.Montaz
import com.ekotak.teamtalk.domain.model.MontazMaterial
import com.ekotak.teamtalk.domain.model.packKey
import com.ekotak.teamtalk.domain.model.MontazPhoto
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.montaz.CoverageKind
import com.ekotak.teamtalk.domain.montaz.MontazTool
import com.ekotak.teamtalk.domain.montaz.RoleState
import com.ekotak.teamtalk.domain.ufh.fmtMb
import com.ekotak.teamtalk.domain.ufh.loopsLabel
import com.ekotak.teamtalk.presentation.theme.OkGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * TECZKA ROBOCZA MONTAŻU — zakładka „Montaż" karty deala, 1:1 z
 * `DealMontazPanel.tsx`.
 *
 * Deal ma zwykle kilka montaży (podłogówka teraz, pompa ciepła po wylewce),
 * więc karta zaczyna się od paska etapów, a wszystko poniżej dotyczy JEDNEGO
 * wybranego montażu. Kolejność sekcji jest ta sama, co w panelu — handlowiec
 * i koordynator znają ją z biurka i szukają tego samego w tym samym miejscu.
 *
 * CZEGO TU NIE MA, tak samo jak w panelu: zmiany TERMINU i OBSADY (ekipa,
 * osoby, role — kto i kiedy układa się wyłącznie w Harmonogramie, decyzja
 * usera 2026-09-24) i protokołu odbioru
 * (formularz z podpisem inwestora zostaje w panelu).
 *
 * CO TELEFON MA PONAD PANEL — bo w biurze nie miałoby sensu:
 *  • „Nawiguj" pod adres montażu i telefon do klienta,
 *  • zdjęcia prosto z aparatu,
 *  • checklista PAKOWANIA (materiał + sprzęt) z ptaszkami trzymanymi lokalnie —
 *    zamiast panelowego „Drukuj listę wyjazdową", bo z telefonu się nie drukuje.
 *    Ptaszek pakowania to NIE jest wydanie z magazynu: pierwsze mówi „leży
 *    w aucie", drugie jest zapisem księgowym i idzie na serwer osobno.
 */
@Composable
fun DealMontazTab(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val montaz = state.montaz

    if (!montaz.loaded) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    montaz.error?.let { error ->
        SectionCard {
            SectionTitle("Montaż")
            SectionGap()
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SectionGap()
    }

    if (montaz.fromCache) {
        MontazBanner(
            text = "Kopia z telefonu — nie było zasięgu. Zmiany zapisują się dalej " +
                "i polecą, gdy sieć wróci.",
            color = SyncBlue,
        )
        SectionGap()
    }

    EtapyCard(state, viewModel)
    SectionGap()

    val selected = montaz.selected
    if (selected == null) {
        SectionCard {
            Text(
                text = "Ten deal nie ma jeszcze montaży. Termin rezerwuje się w zakładce " +
                    "„Oferta”, a planuje w Harmonogramie — albo dodaj etap powyżej.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    TerminCard(state, selected, viewModel)
    SectionGap()

    ZakresCard(state, selected, viewModel)
    SectionGap()

    ObsadaCard(state, selected)
    SectionGap()

    MaterialCard(state, viewModel)
    SectionGap()

    SprzetCard(state, viewModel)
    SectionGap()

    RobotaCard(state)
    SectionGap()

    UmowaZakresCard(state)
    SectionGap()

    OdprawaCard(state, selected, viewModel)
    SectionGap()

    PoRobocieCard(state, viewModel)
    SectionGap()

    UwagaCard(state, viewModel)
}

// ── Etapy montażu ────────────────────────────────────────────────────────────

/**
 * Pasek etapów. Na 360 dp nie mieszczą się trzy montaże obok siebie, więc
 * przewija się w poziomie — a wybrany etap jest podświetlony, bo od niego
 * zależy WSZYSTKO poniżej.
 */
@Composable
private fun EtapyCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val montaz = state.montaz
    val pickTerm = rememberDateTimePicker(
        label = "Termin nowego etapu",
        millis = null,
        onChange = { viewModel.addEtap(it) },
    )

    SectionCard {
        SectionTitle(
            text = "Etapy montażu",
            action = "+ Etap",
            // Wyjazdy zaczynają się rano; godzinę i tak ustala moduł „Montaże",
            // ale bez niej etap siadałby na północy i mylił w kalendarzu.
            onAction = pickTerm,
        )
        SectionGap()

        if (montaz.montaze.isEmpty()) {
            Text(
                text = "Brak montaży.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            montaz.montaze.forEach { item ->
                EtapChip(
                    label = montazJobLabel(item, montaz.catalog),
                    when_ = formatDate(item.scheduledAt) ?: "bez terminu",
                    status = item.status.label,
                    pending = item.pendingSince != null,
                    selected = item.id == montaz.selected?.id,
                    onClick = { viewModel.selectMontaz(item.id) },
                )
            }
        }
    }
}

@Composable
private fun EtapChip(
    label: String,
    when_: String,
    status: String,
    pending: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .width(160.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = colors.onSurface,
        )
        Text(
            text = "$when_ · $status",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        if (pending) {
            Text(
                text = "czeka na wysyłkę",
                style = MaterialTheme.typography.labelSmall,
                color = SyncBlue,
            )
        }
    }
}

// ── Termin i miejsce ─────────────────────────────────────────────────────────

/**
 * Termin jest tu TYLKO do odczytu, tak samo jak w panelu — układa się go
 * w Harmonogramie, gdzie widać obłożenie ekip. Za to telefon dokłada dwie rzeczy, po które
 * sięga się w aucie: nawigację pod adres i telefon do klienta.
 */
@Composable
private fun TerminCard(
    state: DealDetailViewModel.UiState,
    selected: Montaz,
    viewModel: DealDetailViewModel,
) {
    val client = state.detail?.client
    val adres = client?.address?.takeIf { it.isNotBlank() }

    SectionCard {
        SectionTitle(
            text = "Termin i miejsce",
            accent = selected.status.label,
        )
        SectionGap()

        InfoRow("Termin", formatDateTime(selected.scheduledAt) ?: "nieustalony")
        InfoRow("Czas trwania", "${selected.durationDays} dni robocze")
        InfoRow("Trudność", selected.difficulty?.label)
        InfoRow("Adres", adres)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (adres != null) {
                OutlinedButton(
                    onClick = { viewModel.openMap() },
                    modifier = Modifier.weight(1f),
                ) { Text("Nawiguj") }
            }
            client?.primaryPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                OutlinedButton(
                    onClick = { viewModel.call(phone) },
                    modifier = Modifier.weight(1f),
                ) { Text("Zadzwoń do klienta") }
            }
        }

        // Koordynator to opiekun deala — z nim ekipa dzwoni o zmianach zakresu.
        val opiekun = state.members.firstOrNull { it.id == state.detail?.deal?.ownerId }
        if (opiekun != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Koordynator: ${opiekun.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Termin i obsadę układa się wyłącznie w Harmonogramie w panelu — tam widać " +
                "obłożenie ekip i urlopy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Zakres montażu ───────────────────────────────────────────────────────────

@Composable
private fun ZakresCard(
    state: DealDetailViewModel.UiState,
    selected: Montaz,
    viewModel: DealDetailViewModel,
) {
    val montaz = state.montaz
    // Zakres zajęty przez INNE montaże deala — żeby nie ułożyć tego samego dwa razy.
    val takenBy = remember(montaz.montaze, selected.id) {
        buildMap {
            montaz.montaze.filter { it.id != selected.id }.forEach { other ->
                other.nodeIds.forEach { put(it, formatDate(other.scheduledAt) ?: "innym etapem") }
            }
        }
    }

    SectionCard {
        SectionTitle(
            text = "Zakres tego montażu",
            accent = if (selected.nodeIds.isEmpty()) {
                "nieustalony"
            } else {
                "${selected.nodeIds.size} z ${montaz.scopeIds.size}"
            },
        )
        SectionGap()
        Text(
            text = "Z niego wynikają wymagane role, sprzęt i rysunki wykonawcze.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (montaz.scopeIds.isEmpty()) {
            Text(
                text = "Drzewo instalacji na etapie „Montaż” jest puste — zakres wybiera się " +
                    "w zakładce „Dane”.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        montaz.scopeIds.forEach { nodeId ->
            val checked = nodeId in selected.nodeIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !montaz.isSaving) { viewModel.toggleMontazScope(nodeId) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    enabled = !montaz.isSaving,
                    onCheckedChange = { viewModel.toggleMontazScope(nodeId) },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = montaz.catalog[nodeId]?.name ?: nodeId,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val path = montazPath(nodeId, montaz.catalog)
                    if (path.isNotBlank()) {
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    takenBy[nodeId]?.takeIf { !checked }?.let { day ->
                        Text(
                            text = "już w montażu $day",
                            style = MaterialTheme.typography.labelSmall,
                            color = Orange600,
                        )
                    }
                }
            }
        }
    }
}

// ── Obsada ───────────────────────────────────────────────────────────────────

/**
 * Obsada TYLKO do odczytu, tak samo jak w panelu (decyzja usera 2026-09-24):
 * kto montuje i kiedy układa się wyłącznie w Harmonogramie — ekipa, osoby
 * i role. Karta pokazuje stan i pokrycie ról, żeby ekipa wiedziała, kto jedzie.
 */
@Composable
private fun ObsadaCard(
    state: DealDetailViewModel.UiState,
    selected: Montaz,
) {
    val montaz = state.montaz
    val summary = montaz.summary
    val crewName = montaz.crews.firstOrNull { it.id == selected.crewId }?.name

    SectionCard {
        SectionTitle(
            text = "Obsada",
            accent = summary?.text,
        )
        SectionGap()

        summary?.let {
            MontazBanner(
                text = it.text,
                color = when (it.kind) {
                    CoverageKind.FULL -> OkGreen
                    CoverageKind.GAP -> Red600
                    else -> Orange600
                },
            )
            Spacer(Modifier.height(8.dp))
        }

        InfoRow("Ekipa", crewName ?: "bez ekipy")

        Spacer(Modifier.height(8.dp))
        if (selected.assignees.isEmpty()) {
            Text(
                text = "Nikt nie przypisany — bez obsady nie da się wysłać odprawy.",
                style = MaterialTheme.typography.bodySmall,
                color = Orange600,
            )
        } else {
            selected.assignees.forEach { a ->
                val person = state.members.firstOrNull { it.id == a.userId }
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        text = person?.displayName ?: a.userId,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = a.role ?: "bez roli",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (a.role == null) Orange600 else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (montaz.coverage.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            montaz.coverage.forEach { c ->
                val (label, color) = when (c.state) {
                    RoleState.ASSIGNED -> "obsadzona" to OkGreen
                    RoleState.SKILLED -> "bez wskazania" to Orange600
                    RoleState.MISSING -> "brak w obsadzie" to Red600
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = c.role,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
                }
                val hint = when (c.state) {
                    RoleState.ASSIGNED -> c.assignedIds.joinToString(", ") { nameOf(state.members, it) }
                    RoleState.SKILLED ->
                        "umie: " + c.skilledIds.joinToString(", ") { nameOf(state.members, it) } +
                            " — rolę wskazuje się w Harmonogramie"
                    RoleState.MISSING -> if (c.candidateIds.isEmpty()) {
                        "nikt w zespole nie ma tej umiejętności"
                    } else {
                        "mają uprawnienie: " +
                            c.candidateIds.take(3).joinToString(", ") { nameOf(state.members, it) }
                    }
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (summary?.kind == CoverageKind.NO_ROLES && selected.nodeIds.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Katalog nie ma zakresu montażowego dla tych węzłów — uzupełnia się go " +
                    "w karcie węzła Technologii, wtedy role pojawią się tu same.",
                style = MaterialTheme.typography.bodySmall,
                color = Orange600,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ekipę, osoby i role ustawia się wyłącznie w Harmonogramie w panelu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Materiał z magazynu ──────────────────────────────────────────────────────

/**
 * Lista wyjazdowa. Każda pozycja ma DWA niezależne stany: ptaszek pakowania
 * (lokalny, „leży w aucie") i wydanie z magazynu (zapis księgowy, idzie na
 * serwer). Mieszanie ich dałoby magazynowi fałszywy obraz stanu.
 */
@Composable
private fun MaterialCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val montaz = state.montaz

    SectionCard {
        SectionTitle(
            text = "Zabrać z magazynu — materiał",
            accent = if (!montaz.materialsLoaded) {
                "wczytuję…"
            } else {
                "${montaz.issued.size} wyd. · ${montaz.toIssue.size} do wyd."
            },
        )
        SectionGap()

        if (montaz.materialsLoaded && montaz.materials.isEmpty()) {
            Text(
                text = "Magazyn nie ma nic odłożonego pod tego deala. Rezerwacja powstaje sama " +
                    "przy podpisaniu umowy — sprawdź zakładkę „Zamówienie”.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        montaz.materials.forEach { m ->
            MaterialRow(
                material = m,
                packed = m.packKey in montaz.packed,
                enabled = !montaz.isSaving,
                issuedBy = m.issuedById?.let { nameOf(state.members, it) },
                onPack = { viewModel.toggleMontazPacked(m.packKey) },
                onIssue = { viewModel.issueMontazMaterials(listOf(m.id)) },
            )
        }

        if (montaz.toIssue.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.issueMontazMaterials(montaz.toIssue.map { it.id }) },
                enabled = !montaz.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Wydaj wszystko (${montaz.toIssue.size} poz.)") }
        }

        if (montaz.shortages.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            MontazBanner(
                text = "Brakuje na stanie: " +
                    montaz.shortages.joinToString(", ") { it.itemName } +
                    ". Wydać można mimo braku, ale ekipa musi o nim wiedzieć — " +
                    "zamówienia i terminy dostaw są w zakładce „Zamówienie”.",
                color = Red600,
            )
        }
    }
}

@Composable
private fun MaterialRow(
    material: MontazMaterial,
    packed: Boolean,
    enabled: Boolean,
    issuedBy: String?,
    onPack: () -> Unit,
    onIssue: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = packed,
            enabled = enabled,
            onCheckedChange = { onPack() },
            colors = CheckboxDefaults.colors(checkedColor = OkGreen),
        )
        Column(Modifier.weight(1f)) {
            Text(text = material.itemName, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = buildString {
                    append(formatQty(material.quantity))
                    append(" ")
                    append(material.unit)
                    if (material.missing > 0 && material.status == MaterialStatus.ACTIVE) {
                        append(" · brakuje ${formatQty(material.missing)}")
                    }
                    material.note?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (material.missing > 0 && material.status == MaterialStatus.ACTIVE) {
                    Red600
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        when {
            material.pending -> Text(
                text = "wydanie czeka",
                style = MaterialTheme.typography.labelSmall,
                color = SyncBlue,
            )

            material.status == MaterialStatus.DONE -> Text(
                text = listOfNotNull("wydane", issuedBy).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = OkGreen,
            )

            else -> TextButton(enabled = enabled, onClick = onIssue) { Text("Wydaj") }
        }
    }
}

// ── Sprzęt ───────────────────────────────────────────────────────────────────

/**
 * Sprzęt z karty „🧰 Narzędzia" węzłów zakresu, w kolejności PAKOWANIA AUTA.
 * Ptaszki są lokalne — to zamiennik panelowego wydruku listy wyjazdowej.
 */
@Composable
private fun SprzetCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val montaz = state.montaz
    val wszystkie = montaz.tools.sumOf { it.items.size }
    val spakowane = montaz.tools.sumOf { g -> g.items.count { it.key in montaz.packed } }

    SectionCard {
        SectionTitle(
            text = "Zabrać — narzędzia i sprzęt",
            accent = if (wszystkie == 0) null else "$spakowane / $wszystkie",
        )
        SectionGap()

        if (montaz.tools.isEmpty()) {
            Text(
                text = if (montaz.selected?.nodeIds.isNullOrEmpty()) {
                    "Najpierw ustal zakres montażu — sprzęt wynika z węzłów katalogu."
                } else {
                    "Katalog nie ma listy sprzętu dla tych węzłów — uzupełnia się ją w karcie " +
                        "węzła Technologii."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            montaz.tools.forEach { group ->
                Text(
                    text = group.group,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                group.items.forEach { tool ->
                    ToolRow(
                        tool = tool,
                        packed = tool.key in montaz.packed,
                        onPack = { viewModel.toggleMontazPacked(tool.key) },
                    )
                }
            }
        }

        montaz.toolNotes.forEach { note ->
            Spacer(Modifier.height(8.dp))
            MontazBanner(text = note, color = Orange600)
        }
    }
}

@Composable
private fun ToolRow(tool: MontazTool, packed: Boolean, onPack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onPack)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = packed,
            onCheckedChange = { onPack() },
            colors = CheckboxDefaults.colors(checkedColor = OkGreen),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (tool.required) FontWeight.SemiBold else FontWeight.Normal,
            )
            val detale = listOfNotNull(
                tool.qty?.let { "${formatQty(it)} ${tool.unit}" },
                tool.owner.takeIf { it.isNotBlank() },
                if (tool.beacon) "◉ beacon" else null,
                if (tool.required) null else "przydatne",
                tool.note.takeIf { it.isNotBlank() },
            )
            if (detale.isNotEmpty()) {
                Text(
                    text = detale.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Co wykonać ───────────────────────────────────────────────────────────────

/**
 * Rysunki, pętle i parametry z audytu — zawężone do węzłów TEGO montażu.
 * Rzuty schodzą przez wspólny magazyn plików deala, więc raz obejrzane zostają
 * na telefonie i wyświetlą się w piwnicy bez zasięgu.
 */
@Composable
private fun RobotaCard(state: DealDetailViewModel.UiState) {
    val montaz = state.montaz
    val dealId = state.detail?.deal?.id.orEmpty()
    val totalMb = montaz.robota.sumOf { it.totals.total }
    val totalLoops = montaz.robota.sumOf { it.totals.loops }

    SectionCard {
        SectionTitle(
            text = "Co wykonać — rysunki i pętle",
            accent = if (montaz.robota.isEmpty()) {
                null
            } else {
                "${fmtMb(totalMb)} mb · ${loopsLabel(totalLoops)}"
            },
        )
        SectionGap()

        if (montaz.robota.isEmpty()) {
            Text(
                text = "Brak audytu ogrzewania podłogowego dla węzłów tego montażu. Rysunki " +
                    "i pętle biorą się z zakładki „Audyt”.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        montaz.robota.forEach { r ->
            Text(
                text = montaz.catalog[r.categoryId]?.name ?: "Instalacja",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (r.pusty) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Audyt bez metrażu i pomiaru z rzutu — nie ma czego rysować.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Orange600,
                )
                return@forEach
            }

            r.floors.forEach { floor ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = floor.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${loopsLabel(floor.pipe.loops)} · ${fmtMb(floor.pipe.total)} mb · " +
                        if (floor.manifolds == 1) "1 rozdzielacz" else "${floor.manifolds} rozdz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (floor.planDocId != null) {
                    Spacer(Modifier.height(6.dp))
                    PlanImage(dealId = dealId, docId = floor.planDocId, name = floor.name)
                } else {
                    Text(
                        text = "bez rzutu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (floor.pipe.items.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    floor.pipe.items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${(item.spacing * 100).toInt()} cm · ${item.loops} p. · " +
                                    "${fmtMb(item.total)} mb",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            r.tech.forEach { section ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                section.rows.forEach { row -> SpecRow(row.label, row.value, row.note) }
            }
        }
    }
}

/**
 * Rzut kondygnacji. Korzystamy z magazynu plików deala, więc rysunek raz
 * pobrany zostaje na telefonie — na budowie bez zasięgu to jedyny rysunek,
 * jaki ekipa ma przy sobie.
 */
@Composable
private fun PlanImage(dealId: String, docId: String, name: String) {
    val document = remember(docId) { montazPlanDocument(dealId, docId, name) }
    DocumentThumb(
        document = document,
        targetPx = 900,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(10.dp)),
    )
}

// ── Zakres z umowy ───────────────────────────────────────────────────────────

@Composable
private fun UmowaZakresCard(state: DealDetailViewModel.UiState) {
    val zakres = state.montaz.zakres

    CollapsibleSectionCard(
        title = "Zakres z umowy — co jest w cenie",
        summary = zakres?.let { z ->
            listOfNotNull(z.numer, formatDate(z.podpisana)?.let { "podpisana $it" })
                .joinToString(" · ")
        } ?: "brak podpisanej umowy",
    ) {
        if (zakres == null) {
            Text(
                text = "Deal nie ma podpisanej umowy — zakres robót jest jeszcze nieustalony.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@CollapsibleSectionCard
        }

        zakres.pozycje.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    text = p.opis,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${formatQty(p.ilosc)} ${p.jm} · et. ${p.etap}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (zakres.wylaczony.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Poza zakresem — tego NIE robimy",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Red600,
            )
            zakres.wylaczony.forEach { w ->
                Text(
                    text = "• $w",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        MontazBanner(
            text = "Prośby o „drobne dorzucenie” poza tą listą — telefon do koordynatora, " +
                "nie ustalenia na budowie. Zmiana zakresu to aneks do umowy.",
            color = Orange600,
        )
    }
}

// ── Odprawa ──────────────────────────────────────────────────────────────────

@Composable
private fun OdprawaCard(
    state: DealDetailViewModel.UiState,
    selected: Montaz,
    viewModel: DealDetailViewModel,
) {
    val montaz = state.montaz

    SectionCard {
        SectionTitle(
            text = "Odprawa",
            accent = montaz.ack?.let { "potwierdzili ${it.acked} z ${it.total}" },
        )
        SectionGap()
        Text(
            text = selected.briefedAt?.let { "wysłana ${formatDateTime(it)}" }
                ?: "jeszcze nie poszła",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Komunikat idzie do OSÓB z obsady (nie do ekipy jako grupy) i wymaga " +
                "potwierdzenia — w treści lecą termin, skład, uwaga dla ekipy i braki " +
                "materiałowe. Publikacja wymaga prawa do odprawy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.sendMontazOdprawa() },
            enabled = !montaz.isSaving && selected.assignees.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (selected.briefedAt != null) "Wyślij ponownie" else "Wyślij odprawę ekipie") }
    }
}

// ── Po robocie ───────────────────────────────────────────────────────────────

@Composable
private fun PoRobocieCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val montaz = state.montaz
    val pickers = rememberFilePickers { picked ->
        picked.forEach { file -> viewModel.addMontazPhoto(file.bytes, file.name) }
    }

    SectionCard {
        SectionTitle(
            text = "Po robocie",
            accent = "${montaz.photos.size} zdj.",
        )
        SectionGap()

        if (montaz.photos.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                montaz.photos.forEach { photo -> PhotoThumb(photo, viewModel) }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { pickers.takePhoto() },
                enabled = !montaz.isSaving,
                modifier = Modifier.weight(1f),
            ) { Text("Zrób zdjęcie") }
            OutlinedButton(
                onClick = { pickers.pickFiles() },
                enabled = !montaz.isSaving,
                modifier = Modifier.weight(1f),
            ) { Text("Z galerii") }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Protokół odbioru sporządza się w module „Montaże” (formularz i podpis " +
                "inwestora). Punkty ekipy liczy zakładka „Rozliczenie”.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.selectTab(DealTab.ROZLICZENIE) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Zobacz rozliczenie") }
    }
}

@Composable
private fun PhotoThumb(photo: MontazPhoto, viewModel: DealDetailViewModel) {
    var bitmap by remember(photo.id) { mutableStateOf(viewModel.cachedMontazPhoto(photo)) }
    LaunchedEffect(photo.id) {
        if (bitmap == null) bitmap = viewModel.montazPhoto(photo, THUMB_PX)
    }

    Column {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            val image: ImageBitmap? = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = photo.caption ?: "zdjęcie z montażu",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (photo.pending) {
            Text(
                text = "czeka",
                style = MaterialTheme.typography.labelSmall,
                color = SyncBlue,
            )
        }
    }
}

// ── Uwaga dla ekipy ──────────────────────────────────────────────────────────

@Composable
private fun UwagaCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val montaz = state.montaz

    SectionCard {
        SectionTitle("Uwaga dla ekipy")
        SectionGap()
        Text(
            text = "To, czego nie widać w dokumentach: pies, wjazd, klucze.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = montaz.note,
            onValueChange = viewModel::editMontazNote,
            enabled = !montaz.isSaving,
            placeholder = { Text("np. Pies na posesji — uwiązany po 8:00. Wjazd od ogrodu.") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        if (montaz.noteDirty) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.saveMontazNote() },
                    enabled = !montaz.isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text("Zapisz uwagę") }
                OutlinedButton(
                    onClick = { viewModel.revertMontazNote() },
                    enabled = !montaz.isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text("Cofnij") }
            }
        }
    }
}

// ── Drobiazgi ────────────────────────────────────────────────────────────────

/**
 * Wiersz specyfikacji technicznej: etykieta NAD wartością, a nie obok.
 *
 * Wspólny [InfoRow] karty deala stawia je obok siebie i to działa wszędzie
 * indziej, bo tam etykiety są krótkie („Termin", „Adres"). Tutaj obie strony
 * bywają zdaniami („Planowane sterowanie temperaturą w pomieszczeniach" →
 * „tak — rozwiązanie…"), a wtedy na 360 dp etykieta zjada całą szerokość
 * i wartość schodzi do słupka po jednej literze. Sprawdzone na telefonie.
 */
@Composable
private fun SpecRow(label: String, value: String, note: String?) {
    if (value.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        note?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MontazBanner(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun nameOf(members: List<TaskMember>, userId: String): String =
    members.firstOrNull { it.id == userId }?.displayName ?: userId

/**
 * Rzut kondygnacji jako plik deala. Audyt trzyma samo ID dokumentu, a magazyn
 * plików potrzebuje metadanych — składamy je tutaj, zamiast ciągnąć całą listę
 * plików deala tylko po to, żeby znaleźć jeden rysunek. Nazwa i typ służą
 * wyłącznie dekodowaniu: rzuty w slotach są obrazami (odbicie strony PDF-a
 * robi zakładka „Pliki” przy przygotowaniu rzutu).
 */
internal fun montazPlanDocument(dealId: String, docId: String, name: String) = DealDocument(
    id = docId,
    dealId = dealId,
    name = "$name.jpg",
    size = 0,
    contentType = "image/jpeg",
    category = DocumentCategory.AUDYT,
)

/** Klucz pozycji materiału na liście pakowania — po nazwie, jak przy sprzęcie. */

private const val THUMB_PX = 240

/**
 * Nazwa montażu na pasku etapów: węzły zakresu po przecinku, a przy pustym
 * zakresie — sam termin. Tak samo skraca to panel: pełna ścieżka katalogu nie
 * mieści się nawet na biurku.
 */
fun montazJobLabel(montaz: Montaz, catalog: Map<String, Category>): String {
    val names = montaz.nodeIds.mapNotNull { catalog[it]?.name }
    return when {
        names.isEmpty() -> "Montaż"
        names.size <= 2 -> names.joinToString(", ")
        else -> names.take(2).joinToString(", ") + " +${names.size - 2}"
    }
}

/** Ścieżka węzła w katalogu (bez samego liścia) — „Ogrzewanie › Podłogówka". */
private fun montazPath(nodeId: String, catalog: Map<String, Category>): String {
    val parts = mutableListOf<String>()
    var cur = catalog[nodeId]?.parentId?.let { catalog[it] }
    var guard = 0
    while (cur != null && guard++ < 10) {
        parts += cur.name
        cur = cur.parentId?.let { catalog[it] }
    }
    return parts.reversed().joinToString(" › ")
}
