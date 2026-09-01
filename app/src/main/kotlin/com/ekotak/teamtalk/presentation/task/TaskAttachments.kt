package com.ekotak.teamtalk.presentation.task

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ekotak.teamtalk.domain.model.TaskAttachment
import com.ekotak.teamtalk.presentation.crm.formatDateTime
import java.io.File

/**
 * Załączniki karty zadania: protokoły, oferty, zdjęcia z montażu.
 *
 * Plik dodaje się systemowym wyborem, który obejmuje i galerię, i menedżer
 * plików — dzięki temu nie prosimy o żadne uprawnienie do pamięci. Podgląd
 * pobiera treść do cache i oddaje ją systemowi przez `FileProvider`; otwiera
 * ją ta aplikacja, którą użytkownik i tak ma do PDF-ów czy zdjęć.
 */
@Composable
fun TaskAttachments(
    attachments: List<TaskAttachment>,
    uploading: Boolean,
    viewModel: TaskDetailViewModel,
) {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val name = resolver.displayName(uri) ?: "plik"
        val type = resolver.getType(uri) ?: "application/octet-stream"
        // Czytamy w całości: limit board360 to 25 MB, a strumieniowanie przez
        // Retrofit z `content://` wymagałoby własnego `RequestBody`.
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) return@rememberLauncherForActivityResult
        viewModel.addAttachment(name, type, bytes)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (attachments.isEmpty()) "Załączniki" else "Załączniki (${attachments.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (uploading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { picker.launch("*/*") }) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(" Dodaj", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (attachments.isEmpty()) {
            Text(
                text = "Brak plików. Dodaj protokół, ofertę albo zdjęcie z montażu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        attachments.forEach { attachment ->
            AttachmentRow(
                attachment = attachment,
                onOpen = {
                    viewModel.openAttachment(attachment, context.cacheDir) { file ->
                        context.openWithSystem(file, attachment.contentType)
                    }
                },
                onRemove = { viewModel.removeAttachment(attachment.id) },
            )
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: TaskAttachment,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = attachment.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    attachment.sizeLabel.ifBlank { null },
                    attachment.uploaderName,
                    formatDateTime(attachment.createdAt),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Usuń załącznik ${attachment.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Ikona po typie treści — zdjęcie, PDF, reszta jako zwykły plik. */
private fun TaskAttachment.icon() = when {
    contentType?.startsWith("image/") == true -> Icons.Filled.Image
    contentType == "application/pdf" -> Icons.Filled.PictureAsPdf
    else -> Icons.Filled.InsertDriveFile
}

/** Nazwa pliku spod `content://` — bez niej załącznik nazywałby się „plik". */
private fun android.content.ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

/**
 * Oddaje plik systemowi. `FLAG_GRANT_READ_URI_PERMISSION` jest tu obowiązkowe:
 * bez niego aplikacja otwierająca dostanie `content://`, do którego nie ma prawa.
 */
private fun android.content.Context.openWithSystem(file: File, contentType: String?) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, contentType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Bez aplikacji do tego typu pliku nie ma co robić — plik i tak jest
        // już w cache, a komunikat pokaże karta zadania przy następnej akcji.
    }
}
