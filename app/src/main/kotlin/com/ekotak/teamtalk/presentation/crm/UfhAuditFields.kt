package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.UFH_BIOCIDE
import com.ekotak.teamtalk.domain.model.UFH_BOX_TYPES
import com.ekotak.teamtalk.domain.model.UFH_DESIGN_SCOPE
import com.ekotak.teamtalk.domain.model.UFH_HEAT_MEDIUM
import com.ekotak.teamtalk.domain.model.UFH_LEAD_IN_PIPE_MM
import com.ekotak.teamtalk.domain.model.UFH_LEAD_IN_ROUTING
import com.ekotak.teamtalk.domain.model.UFH_PIPE_SYSTEMS
import com.ekotak.teamtalk.domain.model.UFH_PRESSURE_TEST
import com.ekotak.teamtalk.domain.model.UFH_ROOM_CONTROLS
import com.ekotak.teamtalk.domain.model.UFH_SUBFLOOR_JOINTS
import com.ekotak.teamtalk.domain.model.UFH_SYSTEMS
import com.ekotak.teamtalk.domain.model.UFH_SYSTEM_FILLING
import com.ekotak.teamtalk.domain.model.UFH_WALL_CHASE
import com.ekotak.teamtalk.domain.model.UFH_WARRANTY_DOCS
import com.ekotak.teamtalk.domain.model.UFH_WASTE_REMOVAL
import com.ekotak.teamtalk.domain.model.PROPOSE_UFH_PIPE_SYSTEM
import com.ekotak.teamtalk.domain.model.PROPOSE_UFH_PIPE_SYSTEM_LABEL
import com.ekotak.teamtalk.domain.model.UfhAreaField
import com.ekotak.teamtalk.domain.model.UfhFloor
import com.ekotak.teamtalk.domain.model.UfhState
import com.ekotak.teamtalk.domain.model.toM2
import com.ekotak.teamtalk.domain.model.ufhAsksMedium
import com.ekotak.teamtalk.domain.model.ufhAsksWarrantyDocs
import com.ekotak.teamtalk.domain.model.ufhInstallProgress
import com.ekotak.teamtalk.domain.model.ufhMinSpacingCm
import com.ekotak.teamtalk.domain.model.ufhPipeSystemLabel
import kotlin.math.abs
import kotlin.math.round

/**
 * Pola audytu ogrzewania podłogowego — mobilny odpowiednik
 * `UnderfloorHeatingAuditFields`. Trzy sekcje, każda zwijana, bo pytań jest
 * ponad trzydzieści i rozwinięte naraz zamieniają zakładkę w nieskończony rolet:
 *
 *  • **Dane ogólne** — cztery wybory na cały budynek; sterują tym, o co pyta
 *    reszta formularza, więc idą pierwsze,
 *  • **Dane instalacji** — parametry robocizny i materiału (licznik „x / y"
 *    na belce mówi, ile z nich ma już odpowiedź),
 *  • **Kondygnacje** — po jednej karcie na kondygnację; tu audytor spędza
 *    najwięcej czasu, chodząc po domu.
 *
 * Sekcje są ROZWINIĘTE domyślnie tylko dla kondygnacji: dwie pierwsze wypełnia
 * się raz, a metraże poprawia przy każdym pomieszczeniu.
 */
@Composable
fun UfhAuditForm(
    form: UfhState,
    enabled: Boolean,
    hasHeatPump: Boolean,
    onEdit: ((UfhState) -> UfhState) -> Unit,
) {
    val (filled, total) = ufhInstallProgress(form)

    UfhSection(title = "Dane ogólne") {
        AuditChoiceField(
            label = "System rur i rozdzielaczy",
            // „Zaproponuj" to nie pozycja katalogu, tylko brak decyzji klienta —
            // stoi pierwsza, bo tak najczęściej kończy się rozmowa w terenie.
            options = listOf(PROPOSE_UFH_PIPE_SYSTEM) + UFH_PIPE_SYSTEMS.map { it.code },
            selected = form.pipeSystem.takeIf { it.isNotBlank() },
            optionLabel = { code ->
                if (code == PROPOSE_UFH_PIPE_SYSTEM) PROPOSE_UFH_PIPE_SYSTEM_LABEL
                else ufhPipeSystemLabel(code)
            },
            onSelect = { code -> onEdit { it.copy(pipeSystem = code.orEmpty()) } },
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        AuditChoiceField(
            label = "Sterowanie temperaturą w pomieszczeniach",
            options = UFH_ROOM_CONTROLS,
            selected = form.roomControl.takeIf { it.isNotBlank() },
            onSelect = { v -> onEdit { it.copy(roomControl = v.orEmpty()) } },
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        AuditChoiceField(
            label = "Napełnienie i odpowietrzenie układu",
            options = UFH_SYSTEM_FILLING,
            selected = form.systemFilling.takeIf { it.isNotBlank() },
            onSelect = { v -> onEdit { it.copy(systemFilling = v.orEmpty()) } },
            enabled = enabled,
        )
        // Chłodzenie podłogówką ma sens tylko przy pompie ciepła — bez niej
        // pytanie znika, tak jak w panelu, a zapis wymusza `false`.
        if (hasHeatPump) {
            Spacer(Modifier.height(8.dp))
            FormSwitch(
                label = "Planowane chłodzenie instalacją podłogową",
                checked = form.cooling,
            ) { v -> if (enabled) onEdit { it.copy(cooling = v) } }
        }
    }

    Spacer(Modifier.height(8.dp))

    UfhSection(title = "Dane instalacji", badge = "$filled / $total") {
        AuditChoiceField(
            label = "Wkuwanie dobiegów w ściany nośne",
            options = UFH_WALL_CHASE,
            selected = form.install.wallChase.takeIf { it.isNotBlank() },
            onSelect = { v -> onEdit { s -> s.copy(install = s.install.copy(wallChase = v.orEmpty())) } },
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        AuditChoiceField(
            label = "Rura dobiegowa do rozdzielacza (mm)",
            options = UFH_LEAD_IN_PIPE_MM,
            selected = form.install.leadInPipeMm.takeIf { it.isNotBlank() },
            onSelect = { v ->
                onEdit { s -> s.copy(install = s.install.copy(leadInPipeMm = v.orEmpty())) }
            },
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        AuditChoiceField(
            label = "Dobiegi do rozdzielaczy — sposób prowadzenia",
            options = UFH_LEAD_IN_ROUTING,
            selected = form.install.leadInRouting.takeIf { it.isNotBlank() },
            onSelect = { v ->
                onEdit { s -> s.copy(install = s.install.copy(leadInRouting = v.orEmpty())) }
            },
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        AuditChoiceField(
            label = "Łączenie rur pe-rt pod posadzką",
            options = UFH_SUBFLOOR_JOINTS,
            selected = form.install.subfloorJoints.takeIf { it.isNotBlank() },
            onSelect = { v ->
                onEdit { s -> s.copy(install = s.install.copy(subfloorJoints = v.orEmpty())) }
            },
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        AuditChoiceField(
            label = "Projekt instalacji ogrzewania",
            options = UFH_DESIGN_SCOPE,
            selected = form.install.designScope.takeIf { it.isNotBlank() },
            onSelect = { v ->
                onEdit { s -> s.copy(install = s.install.copy(designScope = v.orEmpty())) }
            },
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        AuditChoiceField(
            label = "Dokumentacja i próba szczelności",
            options = UFH_PRESSURE_TEST,
            selected = form.install.pressureTest.takeIf { it.isNotBlank() },
            onSelect = { v ->
                onEdit { s -> s.copy(install = s.install.copy(pressureTest = v.orEmpty())) }
            },
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        AuditChoiceField(
            label = "Usunięcie odpadów montażowych",
            options = UFH_WASTE_REMOVAL,
            selected = form.install.wasteRemoval.takeIf { it.isNotBlank() },
            onSelect = { v ->
                onEdit { s -> s.copy(install = s.install.copy(wasteRemoval = v.orEmpty())) }
            },
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        FormNumberField(
            label = "Płyta systemowa z wypustkami (m²)",
            text = form.install.systemPlateM2,
            onTextChange = { v ->
                onEdit { s -> s.copy(install = s.install.copy(systemPlateM2 = v)) }
            },
            decimal = true,
        )

        // Pytania warunkowe — dokładnie te same warunki co w panelu, bo zapis
        // kasuje odpowiedzi na pytania, których audytor nie widział.
        if (ufhAsksMedium(form)) {
            Spacer(Modifier.height(8.dp))
            AuditChoiceField(
                label = "Rodzaj medium grzewczego",
                options = UFH_HEAT_MEDIUM,
                selected = form.install.heatMedium.takeIf { it.isNotBlank() },
                onSelect = { v ->
                    onEdit { s -> s.copy(install = s.install.copy(heatMedium = v.orEmpty())) }
                },
                enabled = enabled,
            )
            Spacer(Modifier.height(8.dp))
            AuditChoiceField(
                label = "Inhibitor biobójczy",
                options = UFH_BIOCIDE,
                selected = form.install.biocide.takeIf { it.isNotBlank() },
                onSelect = { v ->
                    onEdit { s -> s.copy(install = s.install.copy(biocide = v.orEmpty())) }
                },
                enabled = enabled,
            )
        }
        if (ufhAsksWarrantyDocs(form)) {
            Spacer(Modifier.height(8.dp))
            AuditChoiceField(
                label = "Dokumentacja do wydłużonej 10-letniej gwarancji",
                options = UFH_WARRANTY_DOCS,
                selected = form.install.warrantyDocs.takeIf { it.isNotBlank() },
                onSelect = { v ->
                    onEdit { s -> s.copy(install = s.install.copy(warrantyDocs = v.orEmpty())) }
                },
                enabled = enabled,
            )
        }

        Spacer(Modifier.height(8.dp))
        FormSwitch(
            label = "Dobiegi ze skrzynkami wykonane na etapie wod-kan",
            checked = form.install.leadInByWodKan,
        ) { v -> if (enabled) onEdit { s -> s.copy(install = s.install.copy(leadInByWodKan = v)) } }
        FormSwitch(
            label = "Rozdzielacze zamontowane na etapie wod-kan",
            checked = form.install.manifoldByWodKan,
        ) { v ->
            if (enabled) onEdit { s -> s.copy(install = s.install.copy(manifoldByWodKan = v)) }
        }
    }

    Spacer(Modifier.height(8.dp))

    UfhSection(
        title = "Kondygnacje",
        badge = form.floors.size.toString(),
        initiallyExpanded = true,
    ) {
        form.floors.forEachIndexed { index, floor ->
            UfhFloorCard(
                index = index,
                floor = floor,
                pipeSystem = form.pipeSystem,
                enabled = enabled,
                canRemove = enabled && form.floors.size > 1,
                onEditFloor = { edit ->
                    onEdit { s ->
                        s.copy(floors = s.floors.mapIndexed { i, f -> if (i == index) edit(f) else f })
                    }
                },
                onRemove = {
                    onEdit { s -> s.copy(floors = s.floors.filterIndexed { i, _ -> i != index }) }
                },
            )
            Spacer(Modifier.height(12.dp))
        }
        if (enabled) {
            OutlinedButton(
                onClick = { onEdit { s -> s.copy(floors = s.floors + UfhFloor()) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Dodaj kondygnację") }
        }
    }
}

/**
 * Jedna kondygnacja. Metraże wg rozstawu stoją razem z sumą i porównaniem
 * z projektem — audytor mierzy pomieszczenie po pomieszczeniu, a rozjazd sumy
 * z metrażem projektowym jest jedynym sygnałem, że któreś pominął.
 */
@Composable
private fun UfhFloorCard(
    index: Int,
    floor: UfhFloor,
    pipeSystem: String,
    enabled: Boolean,
    canRemove: Boolean,
    onEditFloor: ((UfhFloor) -> UfhFloor) -> Unit,
    onRemove: () -> Unit,
) {
    val projectM2 = floor.projectM2.toM2()
    val sum = floor.totalArea

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Kondygnacja ${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canRemove) {
                TextButton(onClick = onRemove) { Text("Usuń") }
            }
        }
        Spacer(Modifier.height(8.dp))

        AuditTextField(
            label = "Nazwa kondygnacji",
            value = floor.name,
            enabled = enabled,
            onValueChange = { v -> onEditFloor { it.copy(name = v) } },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormNumberField(
                label = "Metraż wg projektu (m²)",
                text = floor.projectM2,
                onTextChange = { v -> onEditFloor { it.copy(projectM2 = v) } },
                modifier = Modifier.weight(1f),
                decimal = true,
            )
            FormNumberField(
                label = "Rozdzielacze",
                text = floor.manifolds,
                onTextChange = { v -> onEditFloor { it.copy(manifolds = v) } },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        AuditChoiceField(
            label = "System ogrzewania",
            options = UFH_SYSTEMS,
            selected = floor.system.takeIf { it.isNotBlank() },
            onSelect = { v -> onEditFloor { it.copy(system = v.orEmpty()) } },
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        AuditChoiceField(
            label = "Skrzynka rozdzielacza",
            options = UFH_BOX_TYPES,
            selected = floor.boxType.takeIf { it.isNotBlank() },
            onSelect = { v -> onEditFloor { it.copy(boxType = v.orEmpty()) } },
            enabled = enabled,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Powierzchnie (m²)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        val minSpacing = ufhMinSpacingCm(pipeSystem)
        UfhAreaField.entries.forEach { field ->
            // ⌀18 nie da się wygiąć co 5 cm — pole tego rozstawu przy takiej
            // rurze po prostu znika, zamiast przyjmować wartość nie do ułożenia.
            if (field.spacingCm != null && field.spacingCm < minSpacing) return@forEach
            Spacer(Modifier.height(6.dp))
            FormNumberField(
                label = field.label,
                text = floor.area(field),
                onTextChange = { v -> onEditFloor { it.withArea(field, v) } },
                decimal = true,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = buildString {
                append("Razem: ${fmtM2(sum)} m²")
                append(" (OP ${fmtM2(floor.ufhAreaSum)} m²)")
                if (projectM2 != null && projectM2 > 0) {
                    val diff = sum - projectM2
                    append(" · projekt ${fmtM2(projectM2)} m²")
                    // Pół metra różnicy to zaokrąglenia pomiaru, nie pominięte
                    // pomieszczenie — o takim rozjeździe nie ma po co mówić.
                    if (abs(diff) >= 0.5) {
                        append(if (diff > 0) " · o ${fmtM2(diff)} m² więcej" else " · brakuje ${fmtM2(-diff)} m²")
                    }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        AuditTextField(
            label = "Komentarz",
            value = floor.comment,
            singleLine = false,
            minLines = 2,
            enabled = enabled,
            onValueChange = { v -> onEditFloor { it.copy(comment = v) } },
        )
    }
}

/** Jedno miejsce po przecinku — metraż z pomiaru nie bywa dokładniejszy. */
private fun fmtM2(value: Double): String {
    val rounded = round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/**
 * Zwijana sekcja formularza z licznikiem odpowiedzi na belce. Osobna od
 * `CollapsibleSectionCard`, bo tamta rysuje własną ramkę — a te sekcje siedzą
 * już w karcie „Audyt instalacji" i druga obwódka robiłaby z nich pudełka
 * w pudełku.
 */
@Composable
private fun UfhSection(
    title: String,
    badge: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = listOfNotNull(badge, if (expanded) "zwiń" else "rozwiń").joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Pole tekstowe audytu. Osobne od wspólnego `FormTextField`, bo tamto robi
 * `trim()` przy KAŻDYM znaku — a wtedy spacji nie da się wpisać (zjada ją
 * zanim padnie następna litera). Do nazwy kondygnacji („1. piętro") i do
 * komentarza to dyskwalifikacja, więc tu trzymamy tekst surowy, a przycinamy
 * dopiero przy zapisie (`ufhToFormData`).
 */
@Composable
fun AuditTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Wybór z listy jako rozwijane pole. Chipy (`FormChoiceRow`) odpadają:
 * odpowiedzi audytu to całe zdania („położone w izolacji na chudziaku"),
 * a cztery takie chipy zajmują pół ekranu telefonu.
 *
 * Wartość spoza listy (starszy zapis, import) pokazujemy dosłownie zamiast
 * czyścić pole — inaczej zapis z telefonu po cichu kasowałby cudzą odpowiedź.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AuditChoiceField(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    enabled: Boolean = true,
    optionLabel: (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = selected?.let(optionLabel).orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text("wybierz…") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
