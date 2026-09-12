package com.ekotak.teamtalk.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ekotak.teamtalk.domain.model.RouteHistory
import com.ekotak.teamtalk.domain.model.RoutePalette
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Ślad trasy na podkładzie OSM — mobilny odpowiednik warstwy historii z panelu.
 *
 * Rysujemy ODCINKAMI pokolorowanymi prędkością, a nie jedną linią, bo kolor
 * niesie tu treść: po jednolitej zielonej trasie nie widać, gdzie auto jechało
 * 40, a gdzie 120. Odcinki o tym samym kolorze sklejamy w jedną polilinię —
 * przy kilku tysiącach punktów osobny obiekt na każdą parę zadławiłby telefon,
 * a wizualnie nie dałby nic.
 */
@Composable
fun RouteTrackMap(
    history: RouteHistory?,
    focus: Pair<Double, Double>?,
    fitRequest: Int,
    modifier: Modifier = Modifier,
) {
    val drawn = remember { TrackState() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            configureOsmdroidForTrack(context)
            MapView(context).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER,
                )
                isTilesScaledToDpi = true
                controller.setZoom(6.0)
                controller.setCenter(GeoPoint(52.0, 19.2))
            }
        },
        update = { map ->
            if (drawn.history !== history) {
                drawn.history = history
                redrawTrack(map, history)
            }
            if (drawn.lastFit != fitRequest) {
                drawn.lastFit = fitRequest
                fitToTrack(map, history)
            }
            // Dosunięcie do wybranego punktu osi czasu (postój, przekroczenie).
            focus?.let { (lat, lng) ->
                if (drawn.focus != focus) {
                    drawn.focus = focus
                    map.controller.animateTo(GeoPoint(lat, lng))
                    if (map.zoomLevelDouble < 15.0) map.controller.setZoom(15.0)
                }
            }
        },
    )
}

private class TrackState {
    var history: RouteHistory? = null
    var lastFit: Int = -1
    var focus: Pair<Double, Double>? = null
}

private fun configureOsmdroidForTrack(context: android.content.Context) {
    // Ta sama konfiguracja co dla mapy zleceń (User-Agent + cache kafelków);
    // trzymana osobno, żeby ekran historii dał się otworzyć bez mapy zleceń.
    val config = org.osmdroid.config.Configuration.getInstance()
    config.load(context, context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
    config.userAgentValue = context.packageName
    val base = java.io.File(context.cacheDir, "osmdroid").apply { mkdirs() }
    config.osmdroidBasePath = base
    config.osmdroidTileCache = java.io.File(base, "tiles").apply { mkdirs() }
}

private fun redrawTrack(map: MapView, history: RouteHistory?) {
    map.overlays.clear()
    val points = history?.points.orEmpty()
    if (history == null || points.size < 2) {
        map.invalidate()
        return
    }

    // — ślad w odcinkach jednego koloru —
    var runStart = 0
    var runColor = RoutePalette.segment(points[1].speedKmh, history.speedLimitKmh)
    var runGap = points[1].gap
    fun flush(endIdx: Int) {
        if (endIdx <= runStart) return
        val line = Polyline(map).apply {
            setPoints((runStart..endIdx).map { GeoPoint(points[it].lat, points[it].lng) })
            outlinePaint.color = (if (runGap) RoutePalette.GAP else runColor).toInt()
            outlinePaint.strokeWidth = if (runGap) 6f else 10f
            // Luka w sygnale to domysł, a nie przejechana droga — przerywana
            // linia mówi to bez legendy.
            if (runGap) {
                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 18f), 0f)
            }
            infoWindow = null
        }
        map.overlays.add(line)
    }
    for (i in 2 until points.size) {
        val color = RoutePalette.segment(points[i].speedKmh, history.speedLimitKmh)
        if (color != runColor || points[i].gap != runGap) {
            flush(i - 1)
            runStart = i - 1
            runColor = color
            runGap = points[i].gap
        }
    }
    flush(points.size - 1)

    // — postoje —
    for (stop in history.stops) {
        map.overlays.add(
            Marker(map).apply {
                position = GeoPoint(stop.lat, stop.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MapMarkers.pin(
                    map.context,
                    if (stop.idling) RoutePalette.IDLE else RoutePalette.PARKED,
                    "P",
                )
                title = "Postój ${formatClock(stop.fromMillis)}–" +
                    (if (stop.open) "nadal" else formatClock(stop.toMillis)) +
                    " · ${stop.minutes} min"
                infoWindow = null
            },
        )
    }

    // — wyruszenia (początek każdego kursu) —
    for (trip in history.trips) {
        map.overlays.add(
            Marker(map).apply {
                position = GeoPoint(trip.fromLat, trip.fromLng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MapMarkers.pin(map.context, RoutePalette.SLOW, "▶")
                title = "Wyruszenie ${formatClock(trip.departedMillis)}"
                infoWindow = null
            },
        )
    }

    // — przekroczenia prędkości —
    for (run in history.speeding) {
        map.overlays.add(
            Marker(map).apply {
                position = GeoPoint(run.lat, run.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MapMarkers.pin(map.context, RoutePalette.OVER_LIMIT, "!")
                title = "${run.maxSpeed.toInt()} km/h przy progu ${run.limit.toInt()}"
                infoWindow = null
            },
        )
    }

    map.invalidate()
}

private fun fitToTrack(map: MapView, history: RouteHistory?) {
    val geo = history?.points.orEmpty().map { GeoPoint(it.lat, it.lng) }
    val run = {
        when {
            geo.isEmpty() -> {
                map.controller.setZoom(6.0)
                map.controller.setCenter(GeoPoint(52.0, 19.2))
            }
            geo.size == 1 -> {
                map.controller.setZoom(14.0)
                map.controller.setCenter(geo.first())
            }
            else -> map.zoomToBoundingBox(BoundingBox.fromGeoPoints(geo), false, 48)
        }
    }
    // Przed pierwszym pomiarem widok nie zna swoich wymiarów i kadrowanie kończy
    // się skokiem na środek świata — stąd odłożenie do kolejki widoku.
    if (map.width == 0 || map.height == 0) map.post { run() } else run()
}

private fun formatClock(millis: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale("pl")).format(java.util.Date(millis))
