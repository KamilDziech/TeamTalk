package com.ekotak.teamtalk.domain.model

/**
 * Katalog technologii jako drzewo. `GET /api/categories` zwraca płaską listę
 * z `parentId`; zakładka LEAD pokazuje zakres instalacji jako rozwijane gałęzie
 * z licznikiem wyboru, więc raz, przy wczytaniu zakładki, składamy z niej drzewo.
 *
 * Uwaga: wybór instalacji wskazuje węzły DOWOLNEJ głębokości — klient może
 * zaznaczyć samo „Ogrzewanie", ale i konkretną „Powietrzną, split". Dlatego
 * zaznaczenie gałęzi nie jest pochodną zaznaczeń dzieci: to osobny fakt.
 */
data class CategoryNode(
    val id: String,
    val name: String,
    val depth: Int,
    val children: List<CategoryNode> = emptyList(),
) {
    val isLeaf: Boolean get() = children.isEmpty()
}

/**
 * Drzewo katalogu posortowane tak jak w panelu (`position`, potem nazwa) —
 * kolejność musi być ta sama, bo handlowiec zna katalog z panelu i szuka
 * pozycji „na pamięć".
 *
 * Węzły osierocone (rodzic zniknął z katalogu) trafiają na najwyższy poziom
 * zamiast wypaść z drzewa — inaczej zaznaczona instalacja przestałaby być
 * widoczna, mimo że wciąż siedzi w migawce deala.
 */
fun buildCategoryTree(categories: List<Category>): List<CategoryNode> {
    val byParent = categories.groupBy { it.parentId }
    val known = categories.mapTo(HashSet()) { it.id }
    val roots = categories.filter { it.parentId == null || it.parentId !in known }

    fun node(category: Category, depth: Int): CategoryNode = CategoryNode(
        id = category.id,
        name = category.name,
        depth = depth,
        // Głębokość katalogu to dziś trzy poziomy; limit chroni przed
        // zapętleniem na uszkodzonych danych, tak jak `categoryPath`.
        children = if (depth >= 8) {
            emptyList()
        } else {
            byParent[category.id]
                .orEmpty()
                .sortedWith(compareBy({ it.position }, { it.name }))
                .map { node(it, depth + 1) }
        },
    )

    return roots
        .sortedWith(compareBy({ it.position }, { it.name }))
        .map { node(it, 0) }
}

/** Id węzła i wszystkich jego potomków — do liczenia wyboru w gałęzi. */
fun CategoryNode.subtreeIds(): List<String> =
    listOf(id) + children.flatMap { it.subtreeIds() }

/** Ile pozycji z `selected` leży w tej gałęzi (razem z samą gałęzią). */
fun CategoryNode.selectedCount(selected: Set<String>): Int =
    subtreeIds().count { it in selected }

/**
 * Gałęzie, które trzeba rozwinąć, żeby każdy zaznaczony węzeł był widoczny od
 * razu po wejściu w zakładkę. Bez tego wybór schowany dwa poziomy w głąb
 * wyglądałby jak brak wyboru — a to główna informacja tej sekcji.
 */
fun ancestorsOfSelected(nodes: List<CategoryNode>, selected: Set<String>): Set<String> {
    val open = HashSet<String>()

    fun walk(node: CategoryNode, path: List<String>) {
        if (node.id in selected) open.addAll(path)
        node.children.forEach { walk(it, path + node.id) }
    }

    nodes.forEach { walk(it, emptyList()) }
    return open
}
