package com.ekotak.teamtalk.presentation.email

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.EmailDraftAttachment
import com.ekotak.teamtalk.presentation.service.sheetBottomPadding

/**
 * Okno pisania wiadomości — jedno na nową pocztę, odpowiedź i przekazanie.
 *
 * Pole „Od" jest tylko do odczytu: adres bierze się z zakładki, w której
 * człowiek jest. To nie oszczędność, tylko zabezpieczenie — wysyłka z cudzej
 * skrzynki i tak kończy się na serwerze kodem 403, a picker nadawcy sugerowałby
 * wybór, którego nie ma.
 *
 * Załączniki wybieramy systemowym `OpenDocument` i BIERZEMY TRWAŁE PRAWO do
 * pliku. Zwykły `GetContent` daje dostęp tylko do końca ekranu, a wiadomość
 * napisana bez zasięgu czeka w kolejce do powrotu łączności — z wygasłym
 * uprawnieniem załącznik nie miałby jak polecieć.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailComposeSheet(
    title: String,
    fromAddress: String,
    to: String,
    cc: String,
    subject: String,
    body: String,
    attachments: List<EmailDraftAttachment>,
    sending: Boolean,
    onToChange: (String) -> Unit,
    onCcChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onAddAttachment: (EmailDraftAttachment) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onSaveDraft: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val resolver = context.contentResolver
        var name = "plik"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 }
                    ?.let { name = cursor.getString(it) ?: name }
                cursor.getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 }
                    ?.let { size = cursor.getLong(it) }
            }
        }
        onAddAttachment(
            EmailDraftAttachment(
                uri = uri.toString(),
                filename = name,
                mimeType = resolver.getType(uri) ?: "application/octet-stream",
                sizeBytes = size,
            ),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = sheetBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Zamknij")
                }
            }

            Text(
                text = "Od: $fromAddress",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = to,
                onValueChange = onToChange,
                label = { Text("Do") },
                placeholder = { Text("adres@…  (rozdziel przecinkiem)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cc,
                onValueChange = onCcChange,
                label = { Text("DW (opcjonalnie)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = subject,
                onValueChange = onSubjectChange,
                label = { Text("Temat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = onBodyChange,
                label = { Text("Treść") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
            )

            attachments.forEach { file ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp),
                    ) {
                        Text(
                            text = "📎 ${file.filename} · ${formatFileSize(file.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemoveAttachment(file.uri) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Usuń załącznik",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !sending) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(" Załącz")
                }
                TextButton(onClick = onSaveDraft, enabled = !sending) { Text("Zapisz roboczą") }
                Spacer()
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onSend) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(" Wyślij")
                    }
                }
            }

            Text(
                text = "Bez zasięgu wiadomość czeka w kolejce i poleci po powrocie łączności.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Spacer() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
}
