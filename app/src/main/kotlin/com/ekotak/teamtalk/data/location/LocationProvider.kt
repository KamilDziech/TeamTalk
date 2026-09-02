package com.ekotak.teamtalk.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Pozycja telefonu dla filtra „moja lokalizacja" na mapie. Świadomie na czystym
 * `LocationManager`, bez usług Google — aplikacja ma działać także na telefonach
 * bez GMS, tak jak podkład OSM.
 *
 * Najpierw ostatnia znana pozycja (natychmiast, zwykle wystarcza do ustawienia
 * środka promienia), a gdy jej nie ma — jeden strzał do dostawcy z krótkim
 * limitem czasu. Brak zgody albo wyłączona lokalizacja → `null`; ekran mówi
 * wtedy, że trzeba wpisać miejscowość ręcznie.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Współrzędne albo `null` (brak zgody, wyłączony GPS, brak odczytu w czasie). */
    suspend fun currentLocation(): Pair<Double, Double>? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        lastKnown(manager)?.let { return it.latitude to it.longitude }

        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        // Dłuższe czekanie nie ma sensu: filtr promienia to nie nawigacja, a
        // użytkownik w tym czasie i tak może wpisać miejscowość ręcznie.
        val fix = withTimeoutOrNull(8_000) { singleFix(manager, provider) }
        return fix?.let { it.latitude to it.longitude }
    }

    @Suppress("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? = runCatching {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
            .mapNotNull { manager.getLastKnownLocation(it) }
            // Najświeższy odczyt; starszy niż 10 minut traktujemy jako brak —
            // pokazywanie klientów wokół miejsca sprzed dwóch dni myli bardziej,
            // niż pomaga.
            .filter { System.currentTimeMillis() - it.time < 10 * 60 * 1000 }
            .maxByOrNull { it.time }
    }.getOrNull()

    @Suppress("MissingPermission")
    private suspend fun singleFix(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val executor = Executor { it.run() }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, executor) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } else {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            manager.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }

                        @Deprecated("Wymagane na API < 29")
                        override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit
                        override fun onProviderDisabled(p: String) {
                            manager.removeUpdates(this)
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    cont.invokeOnCancellation { manager.removeUpdates(listener) }
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, context.mainLooper)
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
}
