package com.ekotak.teamtalk.presentation.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekotak.teamtalk.domain.model.MapPoint
import com.ekotak.teamtalk.domain.model.haversineKm
import kotlin.math.roundToInt

/**
 * Karta punktu — odpowiednik dymka z panelu. Panel daje w nim jeden odnośnik
 * („Otwórz kartę deala"); na telefonie dochodzą dwie rzeczy terenowe: nawigacja
 * do klienta (intencja `geo:`, obsłuży ją każda mapa w telefonie) i telefon,
 * bo numer i tak leży w kartotece.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapPointSheet(
    point: MapPoint,
    myLocation: Pair<Double, Double>?,
    onOpenDeal: (String) -> Unit,
    onOpenClient: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = Color(point.badge.colorArgb),
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            point.badge.letter,
                            color = Color(0xFF0B0B0B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        point.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        subtitle(point, myLocation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (point.installs.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    point.installs.forEach { name ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            val person = point.technicianLabel ?: point.ownerLabel
            if (person != null) {
                InfoRow(if (point.technicianLabel != null) "Serwisant" else "Opiekun", person)
            }
            point.address?.let { InfoRow("Adres", it) }

            point.dealId?.let { dealId ->
                SheetAction(
                    label = "Otwórz kartę deala",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    highlighted = true,
                ) { onOpenDeal(dealId) }
            }
            // Karta gwarancyjna nie ma deala; klient bywa jednak w kartotece.
            if (point.dealId == null && point.clientId != null) {
                SheetAction(label = "Otwórz kartę klienta", icon = Icons.AutoMirrored.Filled.OpenInNew) {
                    onOpenClient(point.clientId)
                }
            }
            if (point.hasGeo || !point.address.isNullOrBlank()) {
                SheetAction(label = "Nawiguj", icon = Icons.Default.Navigation) {
                    context.startActivity(navigationIntent(point))
                }
            }
            point.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                SheetAction(label = "Zadzwoń · $phone", icon = Icons.Default.Call) {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")),
                    )
                }
            }
        }
    }
}

/** „Etap · miasto · 12 km od Ciebie" — ostatni człon tylko po użyciu GPS. */
private fun subtitle(point: MapPoint, myLocation: Pair<Double, Double>?): String {
    val parts = mutableListOf(point.badge.label)
    point.city?.takeIf { it.isNotBlank() }?.let { parts += it }
    val lat = point.lat
    val lng = point.lng
    if (myLocation != null && lat != null && lng != null) {
        val km = haversineKm(myLocation.first, myLocation.second, lat, lng).roundToInt()
        parts += "$km km od Ciebie"
    }
    return parts.joinToString(" · ")
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = 78.dp, height = 20.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Nawigacja: `geo:lat,lng?q=…` otwiera dowolną mapę zainstalowaną w telefonie
 * (Google Maps, Waze, mapy offline). Gdy punkt nie ma współrzędnych, zostaje
 * samo zapytanie adresowe — mapa poszuka po adresie.
 */
private fun navigationIntent(point: MapPoint): Intent {
    val label = Uri.encode(point.address ?: point.name)
    val uri = if (point.hasGeo) {
        Uri.parse("geo:${point.lat},${point.lng}?q=${point.lat},${point.lng}($label)")
    } else {
        Uri.parse("geo:0,0?q=$label")
    }
    return Intent(Intent.ACTION_VIEW, uri)
}
