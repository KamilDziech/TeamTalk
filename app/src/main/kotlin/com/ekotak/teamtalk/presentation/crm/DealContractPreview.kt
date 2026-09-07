package com.ekotak.teamtalk.presentation.crm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.File

/**
 * Podgląd umowy — ten sam HTML, z którego serwer składa PDF (`.../preview`).
 *
 * Dlaczego strona, a nie PDF: handlowiec otwiera podgląd po to, żeby sprawdzić
 * treść ZANIM wyśle link klientowi, a PDF trzeba najpierw pobrać w całości.
 * HTML przychodzi razem z odczytem i zostaje w telefonie, więc dokument
 * obejrzany raz pokaże się także bez zasięgu. Wysyłką klientowi zajmuje się
 * „PDF — udostępnij" na karcie umowy.
 *
 * `WebView` ładujemy z `loadDataWithBaseURL(null, …)`, więc strona nie ma
 * dostępu do żadnego originu — dokument jest samowystarczalny, a i tak nie ma
 * powodu, żeby cokolwiek dociągał.
 */
@Composable
fun ContractPreviewDialog(
    preview: DealDetailViewModel.ContractPreviewState,
    signUrl: (String) -> String,
    onCopyLink: () -> Unit,
    onClose: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = preview.numer.ifBlank { "Podgląd umowy" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        preview.preview?.let { doc ->
                            Text(
                                text = listOfNotNull(
                                    if (doc.podpisana) "Podpisana przez klienta" else null,
                                    if (doc.fromCache) "kopia z telefonu" else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = onClose) { Text("Zamknij") }
                }

                preview.sciezkaPodpisu?.let { sciezka ->
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(signUrl(sciezka)))
                            onCopyLink()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Kopiuj link do podpisu") }
                }

                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxSize()) {
                    when {
                        preview.isLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        preview.preview != null -> AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = false
                                    // Dokument jest składany pod kartkę A4 —
                                    // bez tego na 360 dp czyta się go w poziomie.
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                }
                            },
                            update = { web ->
                                web.loadDataWithBaseURL(
                                    null,
                                    preview.preview.html,
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                            },
                        )

                        else -> Text(
                            text = preview.error ?: "Nie udało się wczytać dokumentu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Oddaje PDF umowy systemowi — stąd idzie mailem, WhatsAppem albo do chmury.
 * `FLAG_GRANT_READ_URI_PERMISSION` jest obowiązkowe: bez niego aplikacja
 * odbierająca dostanie `content://`, do którego nie ma prawa.
 */
fun Context.shareContractPdf(file: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(Intent.createChooser(intent, "Wyślij umowę").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Bez aplikacji do wysyłki plików nie ma co robić — PDF leży już
        // w pamięci podręcznej, a komunikat pokaże karta przy następnej akcji.
    }
}
