package com.ekotak.teamtalk.presentation.crm

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plik wybrany przez człowieka — treść wczytana w całości, gotowa do kolejki. */
data class PickedFile(val name: String, val contentType: String, val bytes: ByteArray) {
    // `equals`/`hashCode` po tablicy bajtów nie mają tu sensu (i myliłyby przy
    // porównaniach w Compose), więc liczymy je z metadanych.
    override fun equals(other: Any?): Boolean =
        other is PickedFile && name == other.name && bytes.size == other.bytes.size

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.size
}

/**
 * Dwa źródła plików dla zakładki „Pliki": systemowy wybór (galeria + menedżer
 * plików) i aparat.
 *
 * Wybór systemowy nie wymaga ŻADNEGO uprawnienia — dostęp dostajemy do jednego
 * wskazanego pliku. Aparat też nie: uruchamiamy cudzą aplikację przez intencję,
 * a zdjęcie ląduje pod `content://`, które sami jej podajemy. Dlatego
 * w manifeście nie ma ani `READ_MEDIA_*`, ani `CAMERA` — zadeklarowanie
 * `CAMERA` wręcz zaczęłoby wymagać zgody, której dziś nie potrzeba.
 */
class FilePickers internal constructor(
    private val openFiles: () -> Unit,
    private val openCamera: () -> Unit,
) {
    /** Systemowy wybór — wiele plików naraz, jak przeciągnięcie kilku w panelu. */
    fun pickFiles() = openFiles()

    /** Aparat — zdjęcie prosto do sekcji, czego panel z natury nie umie. */
    fun takePhoto() = openCamera()
}

/**
 * Buduje oba wybieraki. [onPicked] dostaje komplet wczytanych plików; sekcję
 * (i ewentualny slot rzutu) zna wywołujący, bo wybrał ją, zanim otworzył okno.
 */
@Composable
fun rememberFilePickers(onPicked: (List<PickedFile>) -> Unit): FilePickers {
    val context = LocalContext.current

    val documents = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        onPicked(uris.mapNotNull { context.contentResolver.read(it) })
    }

    // Ścieżkę zdjęcia trzeba znać PRZED uruchomieniem aparatu (to my dajemy mu
    // miejsce zapisu), więc trzymamy ją między wywołaniami.
    val pending = remember { arrayOfNulls<File>(1) }
    val camera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { saved: Boolean ->
        val file = pending[0]
        pending[0] = null
        if (!saved || file == null || !file.isFile) return@rememberLauncherForActivityResult
        val bytes = runCatching { file.readBytes() }.getOrNull()
        // Kopię w kolejce robi repozytorium — plik z aparatu jest już zbędny.
        runCatching { file.delete() }
        if (bytes != null) {
            onPicked(listOf(PickedFile(cameraFileName(), "image/jpeg", bytes)))
        }
    }

    return remember(context) {
        FilePickers(
            openFiles = { documents.launch(arrayOf("image/*", "application/pdf")) },
            openCamera = {
                val file = context.newCameraFile() ?: return@FilePickers
                pending[0] = file
                camera.launch(context.uriFor(file))
            },
        )
    }
}

/** Nazwa zdjęcia z aparatu — data i godzina, żeby seria kadrów była w kolejności. */
private fun cameraFileName(): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale("pl")).format(Date())
    return "Zdjęcie $stamp.jpg"
}

private fun Context.newCameraFile(): File? = runCatching {
    val dir = File(cacheDir, "deal-camera").apply { mkdirs() }
    File(dir, "shot-${System.currentTimeMillis()}.jpg")
}.getOrNull()

private fun Context.uriFor(file: File): Uri =
    FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

/**
 * Nazwa i treść spod `content://`. Bez odczytania nazwy plik nazywałby się
 * „plik", a to właśnie po nazwie panel rozpoznaje slot rzutu i sekcję.
 */
private fun ContentResolver.read(uri: Uri): PickedFile? {
    val name = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "plik"
    val type = getType(uri) ?: "application/octet-stream"
    // Czytamy w całości: limit board360 to 25 MB, a strumieniowanie przez
    // Retrofit z `content://` wymagałoby własnego `RequestBody`.
    val bytes = runCatching { openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        ?: return null
    return PickedFile(name, type, bytes)
}
