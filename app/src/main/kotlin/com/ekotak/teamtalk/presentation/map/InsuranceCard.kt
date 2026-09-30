package com.ekotak.teamtalk.presentation.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.InsurancePolicy
import com.ekotak.teamtalk.domain.model.current
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val EXPIRING_MS = 30L * 24 * 3_600_000L

/**
 * Ubezpieczenie auta w jednym wierszu nad mapą: zakres, ubezpieczyciel, do
 * kiedy. Dotknięcie rozwija numer polisy i kontakt z przyciskiem „Zadzwoń" —
 * to jest ekran, który kierowca otwiera PO stłuczce, więc numer ma być pod
 * palcem, a nie w panelu. Brak polis = uczciwy komunikat, żeby ktoś je dopisał.
 */
@Composable
fun InsuranceCard(policies: List<InsurancePolicy>) {
    val now = System.currentTimeMillis()
    val policy = policies.current(now)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val (tone, status) = when {
        policy == null -> Color(0xFF8A8A8A) to "brak polisy w systemie"
        policy.isExpired(now) -> Color(0xFFC62828) to "WYGASŁA ${formatDate(policy.validToMillis)}"
        policy.isUpcoming(now) -> Color(0xFF8A8A8A) to "od ${formatDate(policy.validFromMillis)}"
        policy.validToMillis - now <= EXPIRING_MS -> Color(0xFFE08A00) to "do ${formatDate(policy.validToMillis)}"
        else -> Color(0xFF2E7D32) to "do ${formatDate(policy.validToMillis)}"
    }

    Surface(
        color = tone.copy(alpha = 0.10f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = policy != null) { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append("🛡 ")
                        if (policy != null) append("${policy.coverageLabel} · ${policy.insurer} · ")
                        append(status)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tone,
                    modifier = Modifier.weight(1f),
                )
                if (policy != null) {
                    Text(if (expanded) "▲" else "▼", color = tone, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (expanded && policy != null) {
                policy.policyNumber?.let { DetailLine("Nr polisy", it) }
                DetailLine("Okres", "${formatDate(policy.validFromMillis)} – ${formatDate(policy.validToMillis)}")
                policy.agentName?.let { DetailLine("Agent", it) }
                policy.agentContact?.let { DetailLine("Kontakt", it) }
                policy.notes?.let { DetailLine("Uwagi", it) }
                policy.phone?.let { phone ->
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))
                        },
                    ) { Text("Zadzwoń $phone") }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 3.dp),
    )
}

/** Daty polis to dni kalendarzowe zapisane jako północ UTC — formatujemy w UTC. */
private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale("pl", "PL"))
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))
