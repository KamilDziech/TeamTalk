package com.ekotak.teamtalk.presentation.crm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.ekotak.teamtalk.domain.model.FloorPlanDoc

/**
 * Stan ładowania obrazu rzutu kondygnacji dla edytora w audycie OP.
 *
 * [missing] = nie udało się zdobyć treści: rzutu nie ma na telefonie, a zasięgu
 * brak (albo plik jest uszkodzony). Ekran powinien wtedy powiedzieć, że rzut
 * trzeba raz otworzyć w zasięgu, zamiast kręcić spinnerem w nieskończoność.
 */
data class FloorPlanImageState(
    val bitmap: ImageBitmap? = null,
    val loading: Boolean = true,
) {
    val missing: Boolean get() = !loading && bitmap == null
}

/** Dłuższy bok obrazu pod rysowanie — ten sam zapas co w `PlanPrepEditor`. */
const val FLOOR_PLAN_EDITOR_PX = 1800

/**
 * Bitmapa rzutu, najpierw z dysku telefonu: kopia z kolejki (`outbox`), trwały
 * katalog rzutów (`filesDir/deal-docs/plans`), cache, dopiero na końcu sieć.
 * Pobrany z sieci rzut ląduje od razu w trwałym katalogu, więc raz obejrzany
 * zostaje na telefonie. `null` w [plan] = kondygnacja bez rzutu.
 */
@Composable
fun rememberFloorPlanImage(
    plan: FloorPlanDoc?,
    targetPx: Int = FLOOR_PLAN_EDITOR_PX,
): FloorPlanImageState {
    val store = rememberDocumentFileStore()
    var state by remember(plan?.docId, targetPx) {
        mutableStateOf(FloorPlanImageState(loading = plan != null))
    }
    LaunchedEffect(plan?.docId, targetPx) {
        if (plan == null) {
            state = FloorPlanImageState(loading = false)
            return@LaunchedEffect
        }
        val bitmap = store.image(plan.document, plan.page, targetPx)
        state = FloorPlanImageState(bitmap = bitmap, loading = false)
    }
    return state
}
