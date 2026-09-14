package com.ekotak.teamtalk.presentation.assistant

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ekotak.teamtalk.domain.model.CardField
import com.ekotak.teamtalk.domain.model.CardQuestions
import com.ekotak.teamtalk.domain.model.ContactKind
import com.ekotak.teamtalk.domain.model.ContactRole
import com.ekotak.teamtalk.domain.model.missingForSave
import java.io.File

/** Aparat i galeria dla wizytówki — bez uprawnień, jak w zakładce „Pliki" (patrz `FilePickers`). */
class CardPhotoPickers internal constructor(
    val takePhoto: () -> Unit,
    val pickFromGallery: () -> Unit,
)

@Composable
fun rememberCardPhotoPickers(onPhoto: (ByteArray) -> Unit): CardPhotoPickers {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<File>(1) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pending[0]
        pending[0] = null
        if (!saved || file == null || !file.isFile) return@rememberLauncherForActivityResult
        val bytes = runCatching { file.readBytes() }.getOrNull()
        runCatching { file.delete() }
        if (bytes != null) onPhoto(bytes)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            .getOrNull()
            ?.let(onPhoto)
    }

    return remember(context) {
        CardPhotoPickers(
            takePhoto = {
                context.newCardShot()?.let { file ->
                    pending[0] = file
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                }
            },
            pickFromGallery = { gallery.launch("image/*") },
        )
    }
}

/** Ten sam katalog co zdjęcia z zakładki „Pliki" — jest już w `file_paths.xml`. */
private fun Context.newCardShot(): File? = runCatching {
    val dir = File(cacheDir, "deal-camera").apply { mkdirs() }
    File(dir, "card-${System.currentTimeMillis()}.jpg")
}.getOrNull()

/**
 * Zeskanowana wizytówka w wątku: odczytane pola (do poprawienia), ostrzeżenie
 * o duplikacie i kolejne pytania asystenta jako kafelki do kliknięcia.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardScanCard(
    scan: CardScanState,
    onKind: (ContactKind) -> Unit,
    onLead: (Boolean) -> Unit,
    onRole: (ContactRole) -> Unit,
    onOtherRole: () -> Unit,
    onOtherRoleText: (String) -> Unit,
    onToggleEdit: () -> Unit,
    onField: (CardField, String) -> Unit,
    onSave: () -> Unit,
    onOpenLead: () -> Unit,
    onOpenClient: () -> Unit,
) {
    val locked = scan.status == CardScanState.Status.RUNNING || scan.status == CardScanState.Status.DONE
    val missing = missingForSave(scan.card, scan.kind)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("📇 ${scan.card.displayName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Odczytane ${scan.result.sourceLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!locked) {
                    TextButton(onClick = onToggleEdit) { Text(if (scan.editing) "Gotowe" else "Popraw dane") }
                }
            }

            if (scan.editing && !locked) {
                CardField.entries.forEach { field ->
                    OutlinedTextField(
                        value = scan.card.get(field),
                        onValueChange = { onField(field, it) },
                        label = { Text(field.label) },
                        singleLine = true,
                        keyboardOptions = keyboardFor(field),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                CardField.entries.filter { scan.card.get(it).isNotBlank() }.forEach { field ->
                    Row {
                        Text(
                            field.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(96.dp),
                        )
                        Text(scan.card.get(field), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (scan.result.duplicates.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                "  Ten kontakt może już być w kartotece:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        scan.result.duplicates.forEach { d ->
                            Text(
                                "• ${d.name}${d.companyName?.let { " ($it)" } ?: ""} · ${d.category.tabLabel}" +
                                    (d.phone?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (!scan.result.canSave) {
                Text(
                    "Nie masz uprawnień do dodawania kontaktów — przekaż dane biuru.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Question(CardQuestions.KIND)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactKind.entries.forEach { kind ->
                    Choice(kind.label, kind.hint, scan.kind == kind, !locked) { onKind(kind) }
                }
            }

            if (scan.kind?.isClient == true) {
                Question(CardQuestions.MODE)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Założyć lead", "kreator z danymi", scan.lead == true, !locked) { onLead(true) }
                    Choice("Tylko karta", "zakładka Klienci", scan.lead == false, !locked) { onLead(false) }
                }
            }

            if (scan.kind == ContactKind.INNY) {
                Question(CardQuestions.ROLE)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContactRole.entries.forEach { role ->
                        Choice(role.label, null, scan.role == role.wire, !locked) { onRole(role) }
                    }
                    Choice("Inna rola", null, scan.otherRole, !locked) { onOtherRole() }
                }
                if (scan.otherRole) {
                    OutlinedTextField(
                        value = scan.role.orEmpty(),
                        onValueChange = onOtherRoleText,
                        label = { Text("Jaka rola? (trafi do zakładki Inne)") },
                        placeholder = { Text("np. architekt, deweloper") },
                        singleLine = true,
                        enabled = !locked,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when (scan.step) {
                CardScanState.Step.LEAD -> ActionBox("Otwórz kreator LEAD z danymi ${if (scan.kind == ContactKind.B2B) "klienta i firmy" else "klienta"}") {
                    val b2bMissing = missing.takeIf { scan.kind == ContactKind.B2B }
                    if (b2bMissing != null) ErrorText(b2bMissing) else Button(onClick = onOpenLead) { Text("Otwórz kreator") }
                }

                CardScanState.Step.CONFIRM -> {
                    val roleSuffix = scan.roleValue.takeIf { scan.kind == ContactKind.INNY && it.isNotBlank() }?.let { " · $it" } ?: ""
                    ActionBox("Dodaj do zakładki ${scan.tab}: ${scan.card.displayName}$roleSuffix") {
                        when {
                            scan.status == CardScanState.Status.DONE -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text("  ${scan.message ?: "Zapisano."}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (scan.clientId != null) TextButton(onClick = onOpenClient) { Text("Otwórz kartę kontaktu") }
                            }
                            scan.status == CardScanState.Status.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("  Zapisuję…", style = MaterialTheme.typography.bodySmall)
                            }
                            missing != null -> ErrorText(missing)
                            scan.otherRole && scan.roleValue.isBlank() -> ErrorText("Wpisz rolę kontaktu.")
                            else -> {
                                if (scan.status == CardScanState.Status.ERROR) ErrorText(scan.message ?: "Nie udało się zapisać.")
                                Button(onClick = onSave) {
                                    Text(if (scan.status == CardScanState.Status.ERROR) "Spróbuj ponownie" else "Zatwierdź")
                                }
                            }
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}

private fun keyboardFor(field: CardField): KeyboardOptions = when (field) {
    CardField.PHONE, CardField.PHONE2 -> KeyboardOptions(keyboardType = KeyboardType.Phone)
    CardField.EMAIL, CardField.EMAIL2 -> KeyboardOptions(keyboardType = KeyboardType.Email)
    CardField.WEBSITE -> KeyboardOptions(keyboardType = KeyboardType.Uri)
    CardField.NIP, CardField.POSTAL_CODE -> KeyboardOptions(keyboardType = KeyboardType.Number)
    else -> KeyboardOptions(capitalization = KeyboardCapitalization.Words)
}

@Composable
private fun Question(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Choice(label: String, hint: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActionBox(label: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            content()
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
