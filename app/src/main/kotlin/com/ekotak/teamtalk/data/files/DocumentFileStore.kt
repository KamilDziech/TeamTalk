package com.ekotak.teamtalk.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.domain.model.DealDocument
import com.ekotak.teamtalk.domain.model.DocumentCategory
import com.ekotak.teamtalk.domain.model.SLOT_SECTION
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TREŚĆ plików deala — pobieranie, pamięć podręczna, miniatury i strony PDF.
 *
 * Bez biblioteki do obrazów (Coil/Glide) i bez zewnętrznego czytnika PDF-ów:
 * projekt buduje się offline, a wszystko, czego tu trzeba, Android ma u siebie
 * ([BitmapFactory] i [PdfRenderer]). Ten sam wybór co przy `AvatarStore`.
 *
 * Trzy katalogi, każdy z inną obietnicą trwałości:
 *  • `cacheDir/deal-docs` — pobrane pliki; system może je skasować, wtedy
 *    ściągniemy je ponownie,
 *  • `filesDir/deal-docs/outbox` — KOPIE plików czekających w kolejce. To
 *    jedyna kopia zdjęcia zrobionego bez zasięgu, więc nie leży w cache i nie
 *    zależy od tego, czy człowiek skasował oryginał z galerii,
 *  • `filesDir/deal-docs/plans/<dealId>` — RZUTY KONDYGNACJI (pliki w slotach
 *    „Projekt domu"). Na nich audytor rysuje w domu w budowie, bez zasięgu,
 *    więc system nie może ich wymieść jak zwykłego cache'u. Trafiają tu same
 *    przy pierwszym obejrzeniu ([content]) albo z wyprzedzenia ([pinPlans]).
 *
 * Strony PDF-a renderujemy U SIEBIE, a nie przez `/preview/<n>` serwera:
 * w piwnicy bez zasięgu pasek miniatur ma działać tak samo jak na biurku, a raz
 * pobrany PDF wystarcza do wszystkich stron.
 */
@Singleton
class DocumentFileStore @Inject constructor(
    private val api: TeamTalkApi,
    @ApplicationContext private val context: Context,
) {

    private val memory = LruCache<String, ImageBitmap>(MEMORY_ENTRIES)
    private val locks = mutableMapOf<String, Mutex>()
    private val locksGuard = Mutex()

    private val cacheDir: File by lazy { File(context.cacheDir, "deal-docs").apply { mkdirs() } }
    private val outboxDir: File by lazy {
        File(File(context.filesDir, "deal-docs"), "outbox").apply { mkdirs() }
    }
    private val plansRoot: File by lazy {
        File(File(context.filesDir, "deal-docs"), "plans").apply { mkdirs() }
    }

    /** Miejsce trwałej kopii rzutu; katalog per deal, żeby dało się sprzątać po dealu. */
    private fun planFile(document: DealDocument): File =
        File(File(plansRoot, safeSegment(document.dealId)), safeSegment(document.id))

    private fun safeSegment(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "_" }

    /** Miniatura z pamięci, jeśli już jest — pierwsza klatka bez migotania. */
    fun cachedThumb(key: String): ImageBitmap? = memory.get(key)

    // ── Treść ────────────────────────────────────────────────────────────────

    /**
     * Plik z treścią dokumentu albo `null`, gdy nie da się jej teraz zdobyć.
     * Dla wiersza z kolejki oddaje kopię z `outbox` — bez ruchu po sieci, bo
     * serwer o tym pliku jeszcze nie wie.
     */
    suspend fun content(document: DealDocument): File? {
        document.localPath?.let { path ->
            val staged = File(path)
            return if (staged.isFile) staged else null
        }
        val plan = isFloorPlanFile(document)
        val pinned = planFile(document)
        if (plan && pinned.isFile && pinned.length() > 0) return pinned
        val cached = File(cacheDir, document.id)
        if (cached.isFile && cached.length() > 0) {
            // Rzut obejrzany przed wprowadzeniem trwałego katalogu leży jeszcze
            // w cache — przenosimy go, zanim system go wymiecie.
            return if (plan) promote(cached, pinned) ?: cached else cached
        }

        return withLockFor(document.id) {
            if (plan && pinned.isFile && pinned.length() > 0) return@withLockFor pinned
            if (cached.isFile && cached.length() > 0) return@withLockFor cached
            val target = if (plan) pinned else cached
            withContext(Dispatchers.IO) {
                val bytes = runCatching {
                    api.downloadDocument(document.id).use { it.bytes() }
                }.getOrNull() ?: return@withContext null
                runCatching {
                    target.parentFile?.mkdirs()
                    target.writeBytes(bytes)
                }.getOrNull() ?: return@withContext null
                target
            }
        }
    }

    /** Treść leżąca już na telefonie albo `null` — bez ruchu po sieci. */
    fun localFile(document: DealDocument): File? {
        document.localPath?.let { path ->
            return File(path).takeIf { it.isFile }
        }
        planFile(document).takeIf { it.isFile && it.length() > 0 }?.let { return it }
        return File(cacheDir, document.id).takeIf { it.isFile && it.length() > 0 }
    }

    // ── Rzuty kondygnacji offline ────────────────────────────────────────────

    /**
     * Czy plik jest rzutem kondygnacji, który audyt OP może wziąć pod rysowanie:
     * sekcja „Projekt domu" + slot w nazwie, bez „Przekroju". PDF-y też — telefon
     * pozwala odbić na slot cały PDF (panel bierze wyłącznie zdjęcia, patrz
     * `buildFloorPlans`), a trzymanie go na dysku niczego nie psuje.
     */
    fun isFloorPlanFile(document: DealDocument): Boolean =
        document.category == DocumentCategory.PROJEKT &&
            document.slot != null &&
            document.slot != SLOT_SECTION

    /** Czy treść rzutu leży już w trwałym katalogu (albo w kolejce). */
    fun isPinned(document: DealDocument): Boolean =
        document.localPath?.let { File(it).isFile }
            ?: planFile(document).let { it.isFile && it.length() > 0 }

    /**
     * Pobiera do trwałego katalogu wszystkie rzuty deala, których jeszcze tam
     * nie ma, i sprząta kopie rzutów, które z deala zniknęły (plik usunięty albo
     * przepięty na inny slot). Woła to `FloorPlanPrefetchWorker` w zasięgu.
     *
     * @param documents WSZYSTKIE pliki deala — ze zbioru wybieramy rzuty sami,
     *   a pełna lista jest potrzebna do sprzątania.
     * @param prune `false`, gdy lista pochodzi z cache (serwer nieosiągalny).
     * @return ile rzutów deala leży lokalnie i ile nie udało się pobrać.
     */
    suspend fun pinPlans(
        dealId: String,
        documents: List<DealDocument>,
        prune: Boolean = true,
    ): PinResult {
        val plans = documents.filter { it.dealId == dealId && isFloorPlanFile(it) }
        var ready = 0
        var failed = 0
        for (plan in plans) {
            if (content(plan) != null) ready++ else failed++
        }
        // Sprzątamy tylko na liście prosto z serwera — kopia z cache mogłaby nie
        // znać rzutu wgranego przed chwilą w panelu i skasować jego treść.
        if (prune) withContext(Dispatchers.IO) {
            val keep = plans.mapTo(HashSet()) { safeSegment(it.id) }
            File(plansRoot, safeSegment(dealId)).listFiles()
                ?.filter { it.name !in keep }
                ?.forEach { runCatching { it.delete() } }
        }
        return PinResult(ready = ready, failed = failed)
    }

    data class PinResult(val ready: Int, val failed: Int)

    /**
     * Plik z kolejki właśnie wyszedł na serwer. Rzut zostaje na telefonie pod
     * id nadanym przez serwer — to zwykle zdjęcie zrobione na budowie bez
     * zasięgu, a audytor rysuje po nim zaraz potem. Pozostałe pliki sprzątamy
     * jak dotąd.
     */
    fun adoptStaged(path: String?, uploaded: DealDocument) {
        if (path == null) return
        val staged = File(path)
        if (!staged.isFile || !isFloorPlanFile(uploaded)) {
            dropStaged(path)
            return
        }
        if (promote(staged, planFile(uploaded)) == null) dropStaged(path)
    }

    /** Przeniesienie w obrębie pamięci aplikacji; gdy `rename` zawiedzie — kopia. */
    private fun promote(source: File, target: File): File? = runCatching {
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        target.takeIf { it.isFile && it.length() > 0 }
    }.getOrNull()

    /**
     * Kopia pliku pod jego WŁAŚCIWĄ nazwą, gotowa do oddania innej aplikacji
     * przez `FileProvider`. W cache'u nazwą jest id dokumentu, więc czytnik PDF
     * pokazywałby „a3f2…" zamiast „Rzut parter.pdf".
     */
    fun exportCopy(document: DealDocument): File? {
        val source = localFile(document) ?: return null
        val safe = document.displayName.substringAfterLast('/').ifBlank { "plik" }
        val target = File(File(cacheDir, "open"), safe)
        return runCatching {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }.getOrNull()
    }

    /**
     * Sprząta po usuniętym pliku — treść i jego miniatury. Kasujemy wyłącznie
     * klucze TEGO dokumentu: skasowanie jednego zdjęcia nie może wybielić całej
     * siatki kafelków, które właśnie stoją na ekranie.
     */
    fun forget(documentId: String) {
        runCatching { File(cacheDir, documentId).delete() }
        // Trwała kopia rzutu leży w katalogu deala, którego tu nie znamy.
        runCatching {
            plansRoot.listFiles()?.forEach { File(it, safeSegment(documentId)).delete() }
        }
        synchronized(memory) {
            memory.snapshot().keys
                .filter { it.startsWith("$documentId#") }
                .forEach { memory.remove(it) }
        }
    }

    // ── Kolejka ──────────────────────────────────────────────────────────────

    /**
     * Odkłada treść pliku do `outbox` i oddaje ścieżkę kopii. Wywołujący zapisuje
     * ją w wierszu `deal_documents`, więc po restarcie telefonu kolejka wciąż wie,
     * co ma wysłać.
     */
    fun stage(bytes: ByteArray, fileName: String): File? {
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(48)
        val target = File(outboxDir, "${System.currentTimeMillis()}-$safe")
        return runCatching { target.writeBytes(bytes); target }.getOrNull()
    }

    /** Kopia wysłana (albo odrzucona przez serwer) — nie ma po co jej trzymać. */
    fun dropStaged(path: String?) {
        if (path == null) return
        runCatching { File(path).delete() }
    }

    // ── Obrazy ───────────────────────────────────────────────────────────────

    /**
     * Podgląd dokumentu jako bitmapa: zdjęcie wprost z pliku, PDF przez
     * wyrenderowanie strony. `page` liczy się od 1 i dotyczy wyłącznie PDF-ów.
     *
     * @param targetPx dłuższy bok wyniku; miniatura sekcji potrzebuje ~200 px,
     *   podgląd pełnoekranowy — kilkuset. Zmniejszamy przy dekodowaniu, bo
     *   panel przyjmuje pliki do 25 MB i pełna bitmapa nie zmieściłaby się
     *   w pamięci przy kilkunastu kafelkach naraz.
     */
    suspend fun image(document: DealDocument, page: Int = 1, targetPx: Int = THUMB_PX): ImageBitmap? {
        val key = "${document.id}#$page#$targetPx"
        memory.get(key)?.let { return it }
        return withLockFor(key) {
            memory.get(key)?.let { return@withLockFor it }
            val file = content(document) ?: return@withLockFor null
            val bitmap = withContext(Dispatchers.IO) {
                if (document.isPdf) {
                    // PDF-a zaszyfrowanego albo z egzotycznym kodowaniem
                    // `PdfRenderer` nie otworzy — wtedy prosimy serwer o gotowy
                    // JPEG strony, tak jak robi to panel.
                    renderPdfPage(file, page, targetPx) ?: serverPage(document, page, targetPx)
                } else {
                    decode(file, targetPx)
                }
            } ?: return@withLockFor null
            val image = bitmap.asImageBitmap()
            memory.put(key, image)
            image
        }
    }

    /**
     * Ile stron ma PDF. Liczymy z PLIKU, więc bez zasięgu też wiadomo; dopiero
     * gdy telefon nie umie go otworzyć, pytamy serwer. `1` znaczy „jedna strona
     * albo nie wiadomo" — pasek miniatur i tak pokazuje się dopiero od dwóch.
     */
    suspend fun pageCount(document: DealDocument): Int {
        if (!document.isPdf) return 1
        val file = content(document) ?: return 1
        val local = withContext(Dispatchers.IO) {
            runCatching { openPdf(file).use { it.pageCount } }.getOrNull()
        }
        if (local != null) return local
        // Plik wgrany bez zasięgu nie ma jeszcze id na serwerze — nie ma kogo pytać.
        if (document.localPath != null) return 1
        return runCatching { api.getDocumentPreview(document.id) }
            .getOrNull()
            ?.takeIf { it.pdf }
            ?.pages
            ?.coerceAtLeast(1)
            ?: 1
    }

    /** Strona PDF-a wyrenderowana przez serwer — awaryjnie, gdy telefon nie umie. */
    private suspend fun serverPage(document: DealDocument, page: Int, targetPx: Int): Bitmap? {
        if (document.localPath != null) return null
        val bytes = runCatching {
            api.getDocumentPage(document.id, page).use { it.bytes() }
        }.getOrNull() ?: return null
        return decodeBytes(bytes, targetPx)
    }

    /**
     * Strona PDF-a jako JPEG — treść „odbicia" strony na slot rzutu. Panel robi
     * to samo, tylko pobiera gotowy JPEG z `/preview/<n>`; telefon renderuje
     * u siebie, żeby odbicie działało też bez zasięgu.
     */
    suspend fun pageJpeg(document: DealDocument, page: Int): ByteArray? {
        val file = content(document) ?: return null
        return withContext(Dispatchers.IO) {
            val bitmap = renderPdfPage(file, page, MIRROR_PX) ?: return@withContext null
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
        }
    }

    private fun decode(file: File, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = sampleFor(bounds, targetPx) ?: return null
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun decodeBytes(bytes: ByteArray, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = sampleFor(bounds, targetPx) ?: return null
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    /**
     * Krotność zmniejszenia przy dekodowaniu. Panel przyjmuje pliki do 25 MB,
     * a kafelek ma kilkadziesiąt punktów — pełna bitmapa byłaby czystą stratą
     * pamięci przy kilkunastu miniaturach naraz.
     */
    private fun sampleFor(bounds: BitmapFactory.Options, targetPx: Int): Int? {
        val longer = maxOf(bounds.outWidth, bounds.outHeight)
        if (longer <= 0) return null
        var sample = 1
        while (longer / sample > targetPx * 2) sample *= 2
        return sample
    }

    /**
     * Strona PDF-a na białym tle. [PdfRenderer] rysuje po PRZEZROCZYSTYM
     * płótnie, więc bez zamalowania go na biało rzut w jasnym motywie wychodzi
     * czarną plamą — czarny tusz na przezroczystym tle.
     */
    private fun renderPdfPage(file: File, page: Int, targetPx: Int): Bitmap? = runCatching {
        openPdf(file).use { renderer ->
            val index = (page - 1).coerceIn(0, maxOf(0, renderer.pageCount - 1))
            renderer.openPage(index).use { pdfPage ->
                val longer = maxOf(pdfPage.width, pdfPage.height).coerceAtLeast(1)
                val scale = (targetPx.toFloat() / longer).coerceAtMost(MAX_PDF_SCALE)
                val width = (pdfPage.width * scale).toInt().coerceAtLeast(1)
                val height = (pdfPage.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }.getOrNull()

    private fun openPdf(file: File): PdfRenderer =
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))

    /** Jedno pobranie/renderowanie na klucz — siatka kafelków prosi o to samo. */
    private suspend fun <T> withLockFor(key: String, block: suspend () -> T): T {
        val lock = locksGuard.withLock { locks.getOrPut(key) { Mutex() } }
        return lock.withLock { block() }
    }

    private companion object {
        const val MEMORY_ENTRIES = 48
        const val THUMB_PX = 240
        /** Odbicie strony na slot rzutu obrysowuje się potem palcem — stąd zapas. */
        const val MIRROR_PX = 1600
        /** Rzut A3 w 1:1 zjadłby pamięć; powyżej tej skali nic już nie widać lepiej. */
        const val MAX_PDF_SCALE = 4f
    }
}
