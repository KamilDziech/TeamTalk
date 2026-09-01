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
)

/**
 * Ścieżka nazw od kategorii głównej do wskazanego węzła („Ogrzewanie ›
 * Pompa ciepła"). Nieznane id (np. węzeł usunięty z katalogu po zapisaniu
 * migawki) daje pustą listę — wywołujący pokazuje wtedy samo id albo pomija
 * pozycję, zamiast rysować pusty wiersz.
 */
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
