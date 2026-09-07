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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.DealDocument
import com.ekotak.teamtalk.domain.model.DocumentCategory
import com.ekotak.teamtalk.domain.model.PlanSlot
import com.ekotak.teamtalk.domain.model.SLOT_SECTION
import com.ekotak.teamtalk.domain.model.isPdfPageCopy
import com.ekotak.teamtalk.domain.model.projektSlots
import com.ekotak.teamtalk.domain.model.slotLimit
import com.ekotak.teamtalk.domain.ufh.parsePlanPrep
import com.ekotak.teamtalk.domain.ufh.prepSummary

/**
 * Zakładka „Pliki" karty deala — mobilny odpowiednik `DealFilesPanel` panelu.
 *
 * Sekcje, ich kolejność i nazwy idą 1:1 z web: „Projekt domu" z nazwanymi
 * slotami rzutów, sekcje dokumentowe, wspólna grupa „Zdjęcia" z podgrupami
 * Audyt i Montaż, UMOWA i „Pozostałe". Handlowiec znający panel ma szukać
 * tego samego w tym samym miejscu.
 *
 * Dwie rzeczy działają inaczej niż w web, bo palec to nie mysz:
 *  • zamiast przeciągania miniatury na slot rzutu jest akcja „Przypisz do
 *    rzutu…" w menu pliku — z wyborem strony PDF-a, gdy stron jest więcej.
 *    Efekt jest ten sam (kopia z prefiksem `[[slot]]` w nazwie), a przeciąganie
 *    przez przewijaną listę na 360 dp byłoby loterią;
 *  • przy sekcjach zdjęciowych stoi aparat — panel z natury go nie ma, a
 *    dokumentacja montażu powstaje w kotłowni, nie przy biurku.
 */
@Composable
fun DealFilesTab(state: DealDetailViewModel.UiState, viewModel: DealDetailViewModel) {
    val files = state.files
    val canManage = state.canManage
    val busy = files.busy

    // Sekcja (i ewentualny slot) wybrane ZANIM otworzy się okno wyboru pliku.
    // Zwykły `remember` bez stanu: wynik wybieraka przychodzi asynchronicznie,
    // a rekompozycja nie może po drodze zgubić celu.
    val target = remember { arrayOfNulls<UploadTarget>(1) }
    val pickers = rememberFilePickers { picked ->
        val where = target[0]
        picked.forEach {
            viewModel.uploadFile(
                name = it.name,
                contentType = it.contentType,
                bytes = it.bytes,
                category = where?.category,
                slot = where?.slot,
            )
        }
    }

    var sectionPicker by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<DealDocument?>(null) }
    var toDelete by remember { mutableStateOf<DealDocument?>(null) }
    var moveTarget by remember { mutableStateOf<DealDocument?>(null) }
    var assignTarget by remember { mutableStateOf<DealDocument?>(null) }
    var prepTarget by remember { mutableStateOf<PrepTarget?>(null) }

    fun pick(category: DocumentCategory?, slot: String? = null, camera: Boolean = false) {
        target[0] = UploadTarget(category, slot)
        if (camera) pickers.takePhoto() else pickers.pickFiles()
    }

    val actions = remember(canManage, busy) {
        FileActions(
            onPreview = { preview = it },
            onMove = { moveTarget = it },
            onAssign = { assignTarget = it },
            onDelete = { toDelete = it },
            enabled = canManage && !busy,
        )
    }

    if (files.isLoading && files.documents.isEmpty()) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Wczytuję pliki…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    FilesToolbar(
        files = files,
        canManage = canManage,
        onUpload = { sectionPicker = true },
        onRetry = { viewModel.loadFiles(force = true) },
    )
    SectionGap()

    val byCategory = DocumentCategory.entries.associateWith { category ->
        files.documents.filter { it.category == category }
    }
    val projekt = byCategory[DocumentCategory.PROJEKT].orEmpty()

    ProjektSection(
        documents = projekt,
        slots = projektSlots(
            building = state.detail?.deal?.buildingData,
            leadBasement = files.leadBasement,
            leadGarage = files.leadGarage,
            documents = projekt,
        ),
        actions = actions,
        onAdd = { slot -> pick(DocumentCategory.PROJEKT, slot) },
        onCamera = { slot -> pick(DocumentCategory.PROJEKT, slot, camera = true) },
        onPrepare = { document, label -> prepTarget = PrepTarget(document, label) },
    )
    SectionGap()

    // Sekcje dokumentowe idą pojedynczo, w kolejności z panelu — „Zdjęcia"
    // wchodzą między nie jako jedna grupa, a UMOWA i „Pozostałe" na końcu.
    listOf(DocumentCategory.DOTACJA, DocumentCategory.PROTOKOL).forEach { category ->
        DocumentSection(
            category = category,
            documents = byCategory[category].orEmpty(),
            actions = actions,
            onAdd = { pick(category) },
        )
        SectionGap()
    }

    PhotosSection(
        audit = byCategory[DocumentCategory.AUDYT].orEmpty(),
        assembly = byCategory[DocumentCategory.MONTAZ].orEmpty(),
        actions = actions,
        onAdd = { category -> pick(category) },
        onCamera = { category -> pick(category, camera = true) },
    )
    SectionGap()

    listOf(DocumentCategory.UMOWA, DocumentCategory.INNE).forEach { category ->
        DocumentSection(
            category = category,
            documents = byCategory[category].orEmpty(),
            actions = actions,
            onAdd = { pick(category) },
        )
        SectionGap()
    }

    // ── Okna ─────────────────────────────────────────────────────────────────

    preview?.let { document ->
        DocumentViewer(document = document, onClose = { preview = null })
    }

    if (sectionPicker) {
        SectionPickerDialog(
            onDismiss = { sectionPicker = false },
            onPick = { category ->
                sectionPicker = false
                pick(category)
            },
            onCamera = { category ->
                sectionPicker = false
                pick(category, camera = true)
            },
        )
    }

    moveTarget?.let { document ->
        MoveDialog(
            document = document,
            onDismiss = { moveTarget = null },
            onPick = { category ->
                moveTarget = null
                viewModel.moveFile(document, category)
            },
        )
    }

    assignTarget?.let { document ->
        AssignToSlotDialog(
            document = document,
            slots = projektSlots(
                building = state.detail?.deal?.buildingData,
                leadBasement = files.leadBasement,
                leadGarage = files.leadGarage,
                documents = projekt,
            ),
            taken = projekt.mapNotNull { it.slot },
            onDismiss = { assignTarget = null },
            onAssign = { slot, page ->
                assignTarget = null
                viewModel.mirrorToSlot(slot, document, page)
            },
        )
    }

    toDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Usunięcie pliku") },
            text = { Text("Usunąć plik „${document.displayName}”?") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    viewModel.deleteFile(document)
                }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Anuluj") } },
        )
    }

    prepTarget?.let { prep ->
        PlanPrepEditor(
            document = prep.document,
            slotLabel = prep.slotLabel,
            saving = busy,
            onSave = { viewModel.savePlanPrep(prep.document, it) },
            onClose = { prepTarget = null },
        )
    }
}

/** Sekcja i slot wybrane przed otwarciem wybieraka plików. */
private data class UploadTarget(val category: DocumentCategory?, val slot: String?)

/** Rzut otwarty w oknie „Przygotuj rzut". */
private data class PrepTarget(val document: DealDocument, val slotLabel: String)

/** Akcje dostępne na pojedynczym pliku — jeden zestaw dla wszystkich sekcji. */
private class FileActions(
    val onPreview: (DealDocument) -> Unit,
    val onMove: (DealDocument) -> Unit,
    val onAssign: (DealDocument) -> Unit,
    val onDelete: (DealDocument) -> Unit,
    val enabled: Boolean,
)

// ── Pasek nad sekcjami ───────────────────────────────────────────────────────

/**
 * Stan zakładki i jedno wejście do wgrywania. Kolejka i brak zasięgu mają tu
 * osobne komunikaty: „czeka na wysyłkę" to normalna praca w terenie, a „lista
 * z telefonu" ostrzega, że czyjegoś świeżego pliku może tu jeszcze nie być.
 */
@Composable
private fun FilesToolbar(
    files: DealDetailViewModel.FilesState,
    canManage: Boolean,
    onUpload: () -> Unit,
    onRetry: () -> Unit,
) {
    SectionCard {
        SectionTitle(
            text = "Pliki",
            accent = files.documents.size.takeIf { it > 0 }?.let { "$it" },
        )

        if (files.offline || files.pendingCount > 0 || files.error != null) {
            SectionGap()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (files.pendingCount > 0) {
                    StatusLine(
                        icon = Icons.Default.Schedule,
                        text = "${files.pendingCount} ${filesWord(files.pendingCount)} " +
                            "czeka na wysyłkę — polecą same, gdy wróci zasięg.",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (files.offline) {
                    StatusLine(
                        icon = Icons.Default.CloudOff,
                        text = "Lista z telefonu — bez zasięgu nie widać plików " +
                            "dodanych w międzyczasie w panelu.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                files.error?.let { error ->
                    StatusLine(
                        icon = Icons.Default.CloudOff,
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
                }
            }
        }

        if (canManage) {
            SectionGap()
            OutlinedButton(onClick = onUpload, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Wgraj plik")
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

private fun filesWord(count: Int): String = when {
    count == 1 -> "plik"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "pliki"
    else -> "plików"
}

// ── Sekcja „Projekt domu" ────────────────────────────────────────────────────

/**
 * Nazwane sloty rzutów. Slot to prefiks `[[klucz]]` w nazwie pliku — ta sama
 * konwencja co w panelu, więc rzut wgrany tu pojawia się w audycie zrobionym
 * w web i odwrotnie.
 *
 * Pod slotami leżą „Pozostałe pliki projektu": wszystko, co jest w sekcji, a nie
 * siedzi w slocie. Auto-kopie stron PDF-a są stamtąd wycięte — tak jak w panelu,
 * bo powstały właśnie z przypisania strony do rzutu.
 */
@Composable
private fun ProjektSection(
    documents: List<DealDocument>,
    slots: List<PlanSlot>,
    actions: FileActions,
    onAdd: (String?) -> Unit,
    onCamera: (String?) -> Unit,
    onPrepare: (DealDocument, String) -> Unit,
) {
    val bySlot = documents.filter { it.slot != null }.groupBy { it.slot!! }
    val rest = documents.filter { it.slot == null && !isPdfPageCopy(it, documents) }

    SectionCard {
        SectionHeaderRow(
            icon = Icons.Default.Home,
            label = DocumentCategory.PROJEKT.label,
            count = documents.size,
            enabled = actions.enabled,
            onAdd = { onAdd(null) },
        )
        SectionGap()

        // Dwa sloty w rzędzie: na 360 dp trzeci byłby miniaturą znaczka
        // pocztowego, a rzut ma dać się rozpoznać bez powiększania.
        slots.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { slot ->
                    Box(Modifier.weight(1f)) {
                        SlotTile(
                            slot = slot,
                            items = bySlot[slot.key].orEmpty(),
                            actions = actions,
                            onAdd = { onAdd(slot.key) },
                            onCamera = { onCamera(slot.key) },
                            onPrepare = onPrepare,
                        )
                    }
                }
                // Nieparzysta liczba slotów: ostatni kafelek zostaje przy lewej
                // krawędzi zamiast rozciągać się na całą szerokość.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (rest.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                text = "Pozostałe pliki projektu",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Menu pliku → „Przypisz do rzutu…” tworzy kopię w slocie; " +
                    "z PDF-a można wybrać pojedynczą stronę.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            rest.forEach { document ->
                DocumentRow(document = document, actions = actions, assignable = true)
            }
        }
    }
}

/** Pojedynczy slot rzutu: miniatura albo zaproszenie do wgrania. */
@Composable
private fun SlotTile(
    slot: PlanSlot,
    items: List<DealDocument>,
    actions: FileActions,
    onAdd: () -> Unit,
    onCamera: () -> Unit,
    onPrepare: (DealDocument, String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val full = items.size >= slotLimit(slot.key)
    // Rzut przygotowuje się na ZDJĘCIU — PDF idzie najpierw na slot jako
    // odbicie strony, dokładnie jak w panelu.
    val drawable = items.firstOrNull { it.isImage }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = slot.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "brak rzutu",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallAction("Wgraj", actions.enabled, onAdd)
                    SmallAction("Aparat", actions.enabled, onCamera)
                }
            }
        } else {
            items.forEach { document ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DocumentThumb(
                        document = document,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.4f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { actions.onPreview(document) },
                        contentScale = ContentScale.Crop,
                    )
                    FileMenu(document = document, actions = actions, assignable = false)
                }
            }
            if (!full) SmallAction("Dodaj", actions.enabled, onAdd)
        }

        // „Przekrój" nie jest rzutem kondygnacji, więc nie ma czego przygotować.
        if (slot.key != SLOT_SECTION && drawable != null) {
            val prep = parsePlanPrep(drawable.planData)
            Text(
                text = if (prep != null) "przygotowany · ${prepSummary(prep)}" else "nieprzygotowany",
                style = MaterialTheme.typography.bodySmall,
                color = if (prep != null) colors.primary else colors.onSurfaceVariant,
            )
            SmallAction(
                label = if (prep != null) "Popraw rzut" else "Przygotuj rzut",
                enabled = actions.enabled,
                onClick = { onPrepare(drawable, slot.label) },
            )
        }
    }
}

// ── Sekcje dokumentowe i zdjęciowe ───────────────────────────────────────────

@Composable
private fun DocumentSection(
    category: DocumentCategory,
    documents: List<DealDocument>,
    actions: FileActions,
    onAdd: () -> Unit,
) {
    SectionCard {
        SectionHeaderRow(
            icon = category.icon(),
            label = category.label,
            count = documents.size,
            enabled = actions.enabled,
            onAdd = onAdd,
        )
        SectionGap()
        if (documents.isEmpty()) {
            Text(
                text = "brak plików",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            documents.forEach { DocumentRow(document = it, actions = actions) }
        }
    }
}

/**
 * Zdjęcia audytu i montażu pod jednym nagłówkiem, jak w panelu — to jedna
 * dokumentacja fotograficzna dealu, tylko z dwóch momentów procesu.
 */
@Composable
private fun PhotosSection(
    audit: List<DealDocument>,
    assembly: List<DealDocument>,
    actions: FileActions,
    onAdd: (DocumentCategory) -> Unit,
    onCamera: (DocumentCategory) -> Unit,
) {
    SectionCard {
        SectionHeaderRow(
            icon = Icons.Default.PhotoLibrary,
            label = "Zdjęcia",
            count = audit.size + assembly.size,
            enabled = false,
            onAdd = null,
        )
        SectionGap()
        PhotoGroup(DocumentCategory.AUDYT, audit, actions, onAdd, onCamera)
        Spacer(Modifier.height(12.dp))
        PhotoGroup(DocumentCategory.MONTAZ, assembly, actions, onAdd, onCamera)
    }
}

@Composable
private fun PhotoGroup(
    category: DocumentCategory,
    documents: List<DealDocument>,
    actions: FileActions,
    onAdd: (DocumentCategory) -> Unit,
    onCamera: (DocumentCategory) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = category.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${category.label} (${documents.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onCamera(category) },
            enabled = actions.enabled,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Zrób zdjęcie: ${category.label}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        AddBadge(enabled = actions.enabled, label = category.label) { onAdd(category) }
    }
    Spacer(Modifier.height(6.dp))

    if (documents.isEmpty()) {
        Text(
            text = "brak zdjęć",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    documents.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { document ->
                Box(Modifier.weight(1f)) { PhotoTile(document, actions) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PhotoTile(document: DealDocument, actions: FileActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
    ) {
        DocumentThumb(
            document = document,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clickable { actions.onPreview(document) },
            contentScale = ContentScale.Crop,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = document.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = document.metaLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            FileMenu(document = document, actions = actions, assignable = false)
        }
    }
}

/**
 * Wiersz pliku: miniatura, nazwa z rozmiarem i datą, menu akcji. PDF
 * wielostronicowy dostaje pod spodem przewijalny pasek stron — ten sam skrót co
 * w panelu, tylko strony renderuje telefon, więc widać je i bez zasięgu.
 */
@Composable
private fun DocumentRow(
    document: DealDocument,
    actions: FileActions,
    assignable: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DocumentThumb(
                document = document,
                modifier = Modifier
                    .size(width = 52.dp, height = 46.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { actions.onPreview(document) },
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = document.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = document.metaLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FileMenu(document = document, actions = actions, assignable = assignable)
        }
        if (document.isPdf) {
            PdfPagesStrip(document = document, onOpen = { actions.onPreview(document) })
        }
    }
}

/** Pasek miniatur stron PDF-a; pokazuje się dopiero od dwóch stron, jak w web. */
@Composable
private fun PdfPagesStrip(document: DealDocument, onOpen: () -> Unit) {
    val store = rememberDocumentFileStore()
    var pages by remember(document.id) { mutableStateOf(1) }

    LaunchedEffect(document.id) {
        pages = store.pageCount(document)
    }
    if (pages <= 1) return

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 62.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items((1..pages).toList()) { page ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DocumentThumb(
                    document = document,
                    page = page,
                    targetPx = 160,
                    modifier = Modifier
                        .size(width = 34.dp, height = 44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .clickable(onClick = onOpen),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = "$page",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Drobne części wspólne ────────────────────────────────────────────────────

@Composable
private fun SectionHeaderRow(
    icon: ImageVector,
    label: String,
    count: Int,
    enabled: Boolean,
    onAdd: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        if (onAdd != null) {
            Spacer(Modifier.width(6.dp))
            AddBadge(enabled = enabled, label = label, onClick = onAdd)
        }
    }
}

/** Zielony znaczek „+" przy nagłówku — dodanie pliku wprost do tej sekcji. */
@Composable
private fun AddBadge(enabled: Boolean, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.primary.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Dodaj plik: $label",
            tint = colors.onPrimary,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun SmallAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun FileMenu(document: DealDocument, actions: FileActions, assignable: Boolean) {
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Akcje pliku ${document.displayName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Podgląd") },
                onClick = {
                    open = false
                    actions.onPreview(document)
                },
            )
            if (assignable) {
                DropdownMenuItem(
                    text = { Text("Przypisz do rzutu…") },
                    enabled = actions.enabled,
                    onClick = {
                        open = false
                        actions.onAssign(document)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Przenieś do sekcji…") },
                enabled = actions.enabled,
                onClick = {
                    open = false
                    actions.onMove(document)
                },
            )
            DropdownMenuItem(
                text = { Text("Usuń") },
                enabled = actions.enabled,
                onClick = {
                    open = false
                    actions.onDelete(document)
                },
            )
        }
    }
}

// ── Okna wyboru ──────────────────────────────────────────────────────────────

/**
 * „Wybierz sekcję pliku" — ta sama lista co rozwijane menu przycisku „Wgraj
 * plik" w panelu, plus „wykryj automatycznie" na końcu. Przy każdej sekcji stoi
 * aparat: wybór sekcji i wybór źródła to na telefonie jedna decyzja.
 */
@Composable
private fun SectionPickerDialog(
    onDismiss: () -> Unit,
    onPick: (DocumentCategory?) -> Unit,
    onCamera: (DocumentCategory?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz sekcję pliku") },
        text = {
            Column {
                DocumentCategory.entries.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPick(category) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = category.icon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(category.label, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(
                            onClick = { onCamera(category) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Zrób zdjęcie: ${category.label}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(null) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "automatycznie (wykryj sekcję)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun MoveDialog(
    document: DealDocument,
    onDismiss: () -> Unit,
    onPick: (DocumentCategory) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Przenieś do sekcji") },
        text = {
            Column {
                DocumentCategory.entries.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = category != document.category) { onPick(category) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = category.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (category == document.category) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = if (category == document.category) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

/**
 * Mobilny odpowiednik przeciągnięcia miniatury na slot: wybór strony (dla
 * wielostronicowego PDF-a) i wolnego slotu. Powstaje KOPIA — oryginał zostaje,
 * bo ten sam projekt bywa źródłem kilku rzutów.
 */
@Composable
private fun AssignToSlotDialog(
    document: DealDocument,
    slots: List<PlanSlot>,
    taken: List<String>,
    onDismiss: () -> Unit,
    onAssign: (slot: String, page: Int?) -> Unit,
) {
    val store = rememberDocumentFileStore()
    var pages by remember(document.id) { mutableStateOf(1) }
    var page by remember(document.id) { mutableStateOf<Int?>(null) }

    LaunchedEffect(document.id) {
        pages = store.pageCount(document)
        if (document.isPdf && pages == 1) page = 1
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Przypisz do rzutu") },
        text = {
            Column {
                if (document.isPdf && pages > 1) {
                    Text(
                        text = "Strona PDF-a:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((1..pages).toList()) { n ->
                            ChoicePill(
                                label = "$n",
                                selected = page == n,
                                onClick = { page = n },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    text = "Slot:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                slots.forEach { slot ->
                    val used = taken.count { it == slot.key }
                    val free = used < slotLimit(slot.key)
                    val ready = free && (!document.isPdf || pages == 1 || page != null)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = ready) {
                                onAssign(slot.key, page.takeIf { document.isPdf })
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = slot.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ready) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (!free) {
                            Text(
                                text = "zajęty",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// ── Pomocnicze ───────────────────────────────────────────────────────────────

/** Ikona sekcji — te same znaczenia co ikony `CATEGORIES` w panelu. */
private fun DocumentCategory.icon(): ImageVector = when (this) {
    DocumentCategory.PROJEKT -> Icons.Default.Home
    DocumentCategory.DOTACJA -> Icons.Default.Payments
    DocumentCategory.PROTOKOL -> Icons.AutoMirrored.Filled.FactCheck
    DocumentCategory.AUDYT -> Icons.Default.Search
    DocumentCategory.MONTAZ -> Icons.Default.Build
    DocumentCategory.UMOWA -> Icons.Default.Description
    DocumentCategory.INNE -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/** Druga linia wiersza: rozmiar, data i — gdy trzeba — stan kolejki. */
private fun DealDocument.metaLine(): String = listOfNotNull(
    sizeLabel,
    formatDateTime(createdAt),
    "czeka na wysyłkę".takeIf { pending },
).joinToString(" · ")
