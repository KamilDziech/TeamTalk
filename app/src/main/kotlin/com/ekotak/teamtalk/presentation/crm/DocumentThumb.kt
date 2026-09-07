package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.data.files.DocumentFileStore
import com.ekotak.teamtalk.domain.model.DealDocument
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Miniatura pliku deala: zdjęcie wprost z treści, PDF przez wyrenderowanie
 * strony, reszta — ikona pliku.
 *
 * Tło jest zawsze BIAŁE, nie `surface`: rzuty i skany to czarny tusz na białym
 * papierze, więc kafelek w ciemnym motywie musi mieć własne, jasne podłoże,
 * inaczej po wczytaniu obraz „wskakuje" na inny kolor niż miejsce po nim.
 *
 * Nic tu nie mruga: dopóki bitmapa się nie pojawi, w kafelku stoi ikona pliku
 * tego samego rozmiaru.
 */
@Composable
fun DocumentThumb(
    document: DealDocument,
    modifier: Modifier = Modifier,
    page: Int = 1,
    targetPx: Int = 240,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val store = rememberDocumentFileStore()
    val key = "${document.id}#$page#$targetPx"
    var bitmap by remember(key) { mutableStateOf(store.cachedThumb(key)) }

    LaunchedEffect(key) {
        if (bitmap == null) bitmap = store.image(document, page, targetPx)
    }

    Box(
        modifier = modifier.background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val image: ImageBitmap? = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = document.displayName,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Dostęp do [DocumentFileStore] z composable'a. Hilt wstrzykuje do ViewModeli,
 * a miniatura jest komponentem bez własnego modelu — stąd punkt wejścia, tak
 * samo jak przy awatarach zespołu (`AvatarStoreEntryPoint`).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DocumentFileStoreEntryPoint {
    fun documentFileStore(): DocumentFileStore
}

@Composable
fun rememberDocumentFileStore(): DocumentFileStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors
            .fromApplication(context, DocumentFileStoreEntryPoint::class.java)
            .documentFileStore()
    }
}
