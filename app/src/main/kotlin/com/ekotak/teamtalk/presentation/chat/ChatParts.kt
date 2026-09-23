package com.ekotak.teamtalk.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.ChatDelivery
import com.ekotak.teamtalk.presentation.theme.EkotakGreen

/**
 * Pasek ekranów Komunikatora.
 *
 * ŚWIADOMY WYJĄTEK od decyzji z 2026-09-07, która zdjęła nagłówek w całej
 * aplikacji (patrz `AppTopBar`). Rozmowa bez paska nie ma gdzie pokazać, Z KIM
 * się pisze — a to jedyna informacja, bez której czat przestaje być czatem.
 * Wyjątek kończy się na tym module: reszta TeamTalka nadal zaczyna się tuż pod
 * paskiem statusu.
 */
@Composable
fun ChatTopBar(
    title: String,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onNavigateBack != null) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        actions?.invoke()
    }
}

/** Kółko z inicjałami — zdjęć profilowych w rozmowie nie pokazujemy, bo grupa
 *  i wątek zadania żadnego nie mają, a dwa różne awatary obok siebie robią
 *  z listy szachownicę. */
@Composable
fun ChatAvatar(seed: String, initials: String, size: Int = 46) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(chatAvatarColor(seed), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = androidx.compose.ui.graphics.Color.White,
            style = if (size >= 40) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Ptaszki: zegar = czeka w kolejce, ✓ = zapisana, ✓✓ = doszła, ✓✓ na niebiesko
 *  = przeczytana. Rysujemy je znakami, bo ikonki Material nie mają podwójnego
 *  ptaszka, a własny zestaw wektorów dla trzech stanów to przerost formy. */
@Composable
fun ChatTicks(delivery: ChatDelivery?, modifier: Modifier = Modifier) {
    if (delivery == null) return
    val (mark, color) = when (delivery) {
        ChatDelivery.PENDING -> "🕓" to MaterialTheme.colorScheme.onSurfaceVariant
        ChatDelivery.SENT -> "✓" to MaterialTheme.colorScheme.onSurfaceVariant
        ChatDelivery.DELIVERED -> "✓✓" to MaterialTheme.colorScheme.onSurfaceVariant
        ChatDelivery.READ -> "✓✓" to EkotakGreen
    }
    Text(
        text = mark,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
    )
}

/** Plakietka z liczbą nieprzeczytanych. */
@Composable
fun ChatUnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .background(EkotakGreen, RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = androidx.compose.ui.graphics.Color.Black,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Plakietka dnia / komunikat systemowy — wyśrodkowana pigułka nad dymkami. */
@Composable
fun ChatCenterChip(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
