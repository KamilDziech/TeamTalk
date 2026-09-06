package com.ekotak.teamtalk.domain.model

/**
 * Węzeł katalogu technologii (`GET /api/categories`). Instalacje deala
 * wskazują węzły dowolnej głębokości (kategoria → podkategoria → marka), więc
 * do opisania wyboru potrzebna jest cała ścieżka, nie sama nazwa liścia.
 */
data class Category(
    val id: String,
    val parentId: String? = null,
    val name: String = "",
    val position: Int = 0,
    /**
     * Szablon formularza audytu tego węzła (`Category.auditForm` w board360);
     * `null` = węzeł własnego szablonu nie ma. Dziś jedynym ustrukturyzowanym
     * formularzem w katalogu jest audyt ogrzewania podłogowego, więc szablon
     * przychodzi tu już rozłożony na pola — surowy JSON zostaje w warstwie
     * danych, tam gdzie jest kontrakt z panelem.
     */
    val auditForm: UfhState? = null,
)

/**
 * Węzeł, z którego dziedziczy się formularz audytu dla wskazanej instalacji:
 * najbliższy przodek (licząc od samego węzła) z niepustym `auditFormJson`.
 * Dzięki temu wybór marki albo produktu pyta o to samo, o co pyta technologia
 * nad nim. `null` = ta gałąź katalogu formularza audytu nie definiuje.
 */
fun resolveAuditForm(nodeId: String, byId: Map<String, Category>): Category? {
    var node = byId[nodeId]
    // Ten sam licznik kroków co w `categoryPath` — uszkodzone dane nie zapętlą
    // wspinaczki po rodzicach.
    var guard = 0
    while (node != null && guard < 16) {
        if (node.auditForm != null) return node
        node = node.parentId?.let { byId[it] }
        guard++
    }
    return null
}

/**
 * Ścieżka nazw od kategorii głównej do wskazanego węzła („Ogrzewanie ›
 * Pompa ciepła"). Nieznane id (np. węzeł usunięty z katalogu po zapisaniu
 * migawki) daje pustą listę — wywołujący pokazuje wtedy samo id albo pomija
 * pozycję, zamiast rysować pusty wiersz.
 */
/**
 * Ścieżka ID od korzenia do wskazanego węzła. Potrzebna wszędzie tam, gdzie coś
 * DZIEDZICZY się w dół drzewa katalogu — jak zestawy Warunków finansowych, które
 * cennik oferty zbiera z węzła i wszystkich jego przodków.
 */
fun categoryIdPath(id: String, byId: Map<String, Category>): List<String> {
    val path = ArrayList<String>()
    var node = byId[id]
    var guard = 0
    while (node != null && guard < 16) {
        path.add(0, node.id)
        node = node.parentId?.let { byId[it] }
        guard++
    }
    return path
}

fun categoryPath(id: String, byId: Map<String, Category>): List<String> {
    val path = ArrayList<String>()
    var node = byId[id]
    // Katalog jest drzewem, ale uszkodzone dane mogłyby zapętlić wspinaczkę —
    // licznik kroków jest tańszy niż zbiór odwiedzonych, a skutek ten sam.
    var guard = 0
    while (node != null && guard < 16) {
        path.add(0, node.name)
        node = node.parentId?.let { byId[it] }
        guard++
    }
    return path
}
