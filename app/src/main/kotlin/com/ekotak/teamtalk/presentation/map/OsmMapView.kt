package com.ekotak.teamtalk.presentation.map

import android.content.Context
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ekotak.teamtalk.BuildConfig
import com.ekotak.teamtalk.domain.model.MapPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File
import kotlin.math.abs

/** Środek Polski — kadr startowy, dopóki nie ma czego dopasować (jak w panelu). */
private val POLAND_CENTER = GeoPoint(52.0, 19.2)

/** Odległość w pikselach, poniżej której punkty łączą się w klaster. */
private const val CLUSTER_RADIUS_PX = 60

/** Kadr po dopasowaniu nie schodzi bliżej — inaczej pojedynczy punkt „wpada w dach". */
private const val MAX_FIT_ZOOM = 13.0

/**
 * Podkład OSM (osmdroid) z punktami mapy — te same kafelki co Leaflet w panelu,
 * bez klucza API i bez usług Google.
 *
 * Klastrowanie liczymy sami w pikselach ekranu zamiast dokładać bibliotekę:
 * punkty jednej organizacji to setki rekordów, a własny klaster rysuje się
 * dokładnie tak jak `markercluster` w panelu i nie wiąże nas z JitPackiem.
 */
@Composable
fun OsmMapView(
    points: List<MapPoint>,
    mode: MapMode,
    center: MapCenter?,
    radiusKm: Int,
    myLocation: Pair<Double, Double>?,
    fitRequest: Int,
    onSelect: (MapPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSelect by rememberUpdatedState(onSelect)
    val heat = remember { HeatOverlay() }
    // Ostatnio narysowany stan — pozwala odróżnić „zmieniły się dane"
    // od „użytkownik przesunął mapę" (przy przesunięciu przeliczamy same klastry).
    val drawn = remember { DrawnState() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            configureOsmdroid(context)
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER,
                )
                controller.setZoom(6.0)
                controller.setCenter(POLAND_CENTER)
                isTilesScaledToDpi = true
                // Przy przesuwaniu i zoomie klastry trzeba złożyć od nowa —
                // to, co przy jednym powiększeniu było kropką, przy innym jest grupą.
                addMapListener(
                    DelayedMapListener(
                        object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                redraw(this@apply, drawn, heat, currentSelect)
                                return false
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean {
                                redraw(this@apply, drawn, heat, currentSelect)
                                return false
                            }
                        },
                        200,
                    ),
                )
            }
        },
        update = { map ->
            drawn.points = points
            drawn.mode = mode
            drawn.center = center
            drawn.radiusKm = radiusKm
            drawn.myLocation = myLocation

            if (drawn.lastFitRequest != fitRequest) {
                drawn.lastFitRequest = fitRequest
                fitTo(map, points, center, radiusKm)
            }
            redraw(map, drawn, heat, currentSelect)
        },
    )

    DisposableEffect(Unit) {
        onDispose { /* MapView zwalnia kafelki sam przy odłączeniu widoku */ }
    }
}

/** Stan ostatniego rysowania — trzymany poza kompozycją, bo rysuje go widok. */
private class DrawnState {
    var points: List<MapPoint> = emptyList()
    var mode: MapMode = MapMode.PINS
    var center: MapCenter? = null
    var radiusKm: Int = 0
    var myLocation: Pair<Double, Double>? = null
    var lastFitRequest: Int = -1
}

/**
 * osmdroid wymaga własnego `User-Agent` (polityka użycia kafelków OSM zabrania
 * domyślnego) i katalogu na cache. Trzymamy go w pamięci podręcznej aplikacji,
 * więc mapa raz obejrzanej okolicy otwiera się bez zasięgu, a system może ją
 * skasować, gdy zabraknie miejsca.
 */
private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    // Własny plik ustawień zamiast domyślnych preferencji aplikacji: osmdroid
    // trzyma tam swoje liczniki i ścieżki cache, a nie ma powodu mieszać ich
    // z ustawieniami użytkownika.
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    config.userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
    val base = File(context.cacheDir, "osmdroid").apply { mkdirs() }
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
}

/** Składa warstwy od nowa: heatmapa albo piny + klastry, okrąg promienia, pozycja. */
private fun redraw(
    map: MapView,
    state: DrawnState,
    heat: HeatOverlay,
    onSelect: (MapPoint) -> Unit,
) {
    val context = map.context
    map.overlays.clear()

    if (state.mode == MapMode.HEAT) {
        heat.setPoints(state.points.mapNotNull { it.geoPoint() })
        map.overlays.add(heat)
    } else {
        for (cluster in cluster(map, state.points)) {
            val marker = Marker(map).apply {
                position = cluster.position
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                infoWindow = null
            }
            if (cluster.points.size == 1) {
                val point = cluster.points.first()
                marker.icon = MapMarkers.pin(context, point.badge.colorArgb, point.badge.letter)
                marker.title = point.name
                marker.setOnMarkerClickListener { _, _ ->
                    onSelect(point)
                    true
                }
            } else {
                marker.icon = MapMarkers.cluster(context, cluster.points.size)
                marker.setOnMarkerClickListener { _, view ->
                    // Kliknięcie klastra przybliża — dokładnie jak w panelu.
                    view.controller.setZoom(view.zoomLevelDouble + 2)
                    view.controller.animateTo(cluster.position)
                    true
                }
            }
            map.overlays.add(marker)
        }
    }

    // Okrąg promienia „od lokalizacji" — rysowany, gdy jest środek i promień.
    val center = state.center
    if (center != null && state.radiusKm > 0) {
        val circle = Polygon(map).apply {
            points = Polygon.pointsAsCircle(
                GeoPoint(center.lat, center.lng),
                state.radiusKm * 1000.0,
            )
            fillPaint.color = Color.argb(20, 68, 214, 44)
            outlinePaint.color = Color.argb(220, 68, 214, 44)
            outlinePaint.strokeWidth = 3f
            infoWindow = null
        }
        map.overlays.add(0, circle)
    }

    state.myLocation?.let { (lat, lng) ->
        map.overlays.add(
            Marker(map).apply {
                position = GeoPoint(lat, lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MapMarkers.pin(context, 0xFF5B8DEF, "•")
                infoWindow = null
            },
        )
    }

    map.invalidate()
}

/** Grupa punktów zlepionych w jeden marker przy bieżącym powiększeniu. */
private class Cluster(val position: GeoPoint, val points: List<MapPoint>)

/**
 * Klastrowanie w pikselach ekranu: punkty bliższe niż [CLUSTER_RADIUS_PX]
 * lądują w jednej grupie. Liczone przy każdym przerysowaniu, bo zależy od zoomu.
 */
private fun cluster(map: MapView, points: List<MapPoint>): List<Cluster> {
    val projection = map.projection
    val buckets = HashMap<Long, MutableList<MapPoint>>()
    val screen = android.graphics.Point()
    for (point in points) {
        val geo = point.geoPoint() ?: continue
        projection.toPixels(geo, screen)
        val cx = Math.floorDiv(screen.x, CLUSTER_RADIUS_PX)
        val cy = Math.floorDiv(screen.y, CLUSTER_RADIUS_PX)
        val key = (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL)
        buckets.getOrPut(key) { mutableListOf() }.add(point)
    }
    return buckets.values.map { group ->
        if (group.size == 1) {
            Cluster(group.first().geoPoint()!!, group)
        } else {
            // Środek grupy = średnia współrzędnych; przy komórce 60 px różnica
            // wobec środka ciężkości jest niewidoczna, a liczy się szybciej.
            val lat = group.mapNotNull { it.lat }.average()
            val lng = group.mapNotNull { it.lng }.average()
            Cluster(GeoPoint(lat, lng), group)
        }
    }
}

/** Kadr do widocznych punktów (albo do okręgu promienia, gdy jest ustawiony). */
private fun fitTo(map: MapView, points: List<MapPoint>, center: MapCenter?, radiusKm: Int) {
    val run = {
        if (center != null && radiusKm > 0) {
            val circle = Polygon.pointsAsCircle(GeoPoint(center.lat, center.lng), radiusKm * 1000.0)
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(circle), false, 40)
        } else {
            val geo = points.mapNotNull { it.geoPoint() }
            when {
                geo.isEmpty() -> {
                    map.controller.setZoom(6.0)
                    map.controller.setCenter(POLAND_CENTER)
                }
                // Jeden punkt (albo wszystkie w tym samym miejscu) nie ma
                // ramki do dopasowania — ustawiamy stały, sensowny zoom.
                geo.size == 1 || sameSpot(geo) -> {
                    map.controller.setZoom(MAX_FIT_ZOOM)
                    map.controller.setCenter(geo.first())
                }
                else -> map.zoomToBoundingBox(BoundingBox.fromGeoPoints(geo), false, 48)
            }
            if (map.zoomLevelDouble > MAX_FIT_ZOOM) map.controller.setZoom(MAX_FIT_ZOOM)
        }
    }
    // Przed pierwszym pomiarem widoku mapa nie zna swoich wymiarów i kadrowanie
    // kończy się „skokiem" na środek świata — stąd odłożenie do kolejki widoku.
    if (map.width == 0 || map.height == 0) map.post { run() } else run()
}

private fun sameSpot(points: List<GeoPoint>): Boolean {
    val first = points.first()
    return points.all {
        abs(it.latitude - first.latitude) < 1e-6 && abs(it.longitude - first.longitude) < 1e-6
    }
}

private fun MapPoint.geoPoint(): GeoPoint? {
    val lat = lat ?: return null
    val lng = lng ?: return null
    return GeoPoint(lat, lng)
}
