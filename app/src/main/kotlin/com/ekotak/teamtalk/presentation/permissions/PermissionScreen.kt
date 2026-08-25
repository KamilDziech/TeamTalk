package com.ekotak.teamtalk.presentation.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ekotak.teamtalk.presentation.theme.Green600

/** Pojedyncze uprawnienie runtime z opisem, po co jest potrzebne. */
data class AppPermission(
    val permission: String,
    val label: String,
    val rationale: String,
)

/** Lista uprawnień runtime wymaganych przez aplikację (zależna od wersji Androida). */
fun requiredAppPermissions(): List<AppPermission> = buildList {
    add(AppPermission(Manifest.permission.READ_PHONE_STATE, "Stan telefonu", "Wykrywanie zakończonych połączeń"))
    add(AppPermission(Manifest.permission.READ_CALL_LOG, "Historia połączeń", "Odczyt numeru i czasu rozmowy"))
    add(AppPermission(Manifest.permission.READ_CONTACTS, "Kontakty", "Pokazywanie nazwy dzwoniącego zamiast numeru"))
    add(AppPermission(Manifest.permission.RECORD_AUDIO, "Mikrofon", "Nagrywanie notatek głosowych po rozmowie"))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(AppPermission(Manifest.permission.POST_NOTIFICATIONS, "Powiadomienia", "Przypomnienie o notatce po połączeniu"))
    }
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun allPermissionsGranted(context: Context): Boolean =
    requiredAppPermissions().all { isGranted(context, it.permission) }

/**
 * Ekran onboardingowy proszący o wszystkie potrzebne uprawnienia. Przy wejściu
 * automatycznie wywołuje systemowe okno prośby; po przyznaniu wszystkich znika
 * sam (onContinue). Dla trwale odrzuconych — przycisk do ustawień aplikacji.
 */
@Composable
fun PermissionScreen(
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val permissions = remember { requiredAppPermissions() }
    var refresh by remember { mutableIntStateOf(0) }

    // Odśwież stan po powrocie z ustawień systemowych.
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val statuses = remember(refresh) { permissions.map { it to isGranted(context, it.permission) } }
    val missing = statuses.filterNot { it.second }.map { it.first.permission }
    val allGranted = missing.isEmpty()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    // Automatyczna prośba przy pierwszym wejściu.
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (missing.isNotEmpty() && !asked) {
            asked = true
            launcher.launch(missing.toTypedArray())
        }
    }

    // Wszystko przyznane → przejdź dalej.
    LaunchedEffect(allGranted) {
        if (allGranted) onContinue()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Uprawnienia aplikacji",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "TeamTalk potrzebuje poniższych dostępów, aby wykrywać połączenia i pokazywać dane dzwoniących.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            statuses.forEach { (perm, granted) ->
                PermissionRow(perm = perm, granted = granted)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))

            if (!allGranted) {
                Button(
                    onClick = { launcher.launch(missing.toTypedArray()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green600),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Przyznaj brakujące", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openAppSettings(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Otwórz ustawienia aplikacji")
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pomiń na razie")
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(perm: AppPermission, granted: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (granted) Green600 else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    perm.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    perm.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (granted) "Przyznano" else "Wymagane",
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) Green600 else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
