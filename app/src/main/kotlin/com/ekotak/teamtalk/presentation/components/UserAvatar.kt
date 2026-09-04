package com.ekotak.teamtalk.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.data.avatar.AvatarStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Awatar osoby: zdjęcie z board360 (`/api/users/:id/avatar`), a gdy konta nie
 * da się pokazać zdjęciem — kółko z inicjałami.
 *
 * Zdjęcie jest to samo co w panelu: obie aplikacje siedzą na jednym API.
 * Dopóki nie przyjdzie (albo gdy go nie ma), na ekranie stoją inicjały —
 * kółko nigdy nie mruga pustką i nie zmienia rozmiaru.
 */
@Composable
fun UserAvatar(
    userId: String?,
    initials: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val store = rememberAvatarStore()
    // Start od tego, co już wisi w pamięci — przewijanie listy nie mruga.
    var photo by remember(userId) { mutableStateOf(userId?.let(store::cached)) }

    LaunchedEffect(userId) {
        if (userId != null && photo == null) photo = store.avatar(userId)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap: ImageBitmap? = photo
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initials,
                style = if (size <= 24.dp) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Dostęp do [AvatarStore] z composable'a. Hilt wstrzykuje do ViewModeli, a to
 * jest komponent współdzielony przez kilka ekranów i nie ma własnego modelu —
 * stąd punkt wejścia zamiast przepychania zależności przez wszystkie stany.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AvatarStoreEntryPoint {
    fun avatarStore(): AvatarStore
}

@Composable
private fun rememberAvatarStore(): AvatarStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, AvatarStoreEntryPoint::class.java).avatarStore()
    }
}
