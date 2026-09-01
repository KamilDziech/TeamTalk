package com.ekotak.teamtalk.domain.model

/** Szansa klienta pokazywana w zakładce „Deale" karty kartoteki. */
data class ClientDeal(
    val id: String,
    val stage: DealStage,
    val value: Double,
    val createdAt: String?,
    val updatedAt: String?,
)

/**
 * Wiersz kartoteki gotowy do wyświetlenia: klient wzbogacony o dane z lejka —
 * główny etap (najświeższa szansa), instalacje (unia z wszystkich deali) oraz
 * współkontakty deali wspólnych. Panel liczy to samo w `ClientsPage`.
 */
data class ClientListEntry(
    val client: Client,
    val deals: List<ClientDeal> = emptyList(),
    val installations: List<String> = emptyList(),
    val sharedWith: List<String> = emptyList(),
) {
    /** Najświeższa szansa (po dacie aktualizacji, potem utworzenia). */
    val freshestDeal: ClientDeal?
        get() = deals.maxByOrNull { (it.updatedAt ?: it.createdAt).orEmpty() }

    val mainStage: DealStage? get() = freshestDeal?.stage
}

/**
 * Grupa duplikatów kartoteki: rekordy o tym samym imieniu i nazwisku, telefonie
 * lub e-mailu. board360 nie ma na to endpointu — panel wykrywa je u siebie, więc
 * mobilna kartoteka liczy dokładnie tak samo (union-find po wspólnych kluczach).
 */
data class DuplicateGroup(val clients: List<ClientListEntry>)

/**
 * Grupuje duplikaty z pełnej (niezawężonej wyszukiwarką) listy kartoteki.
 * Zwraca wyłącznie grupy ≥2, posortowane rekordami z największą liczbą deali
 * na przodzie — to one są domyślnym celem scalenia.
 */
fun duplicateGroups(entries: List<ClientListEntry>): List<DuplicateGroup> {
    val parent = HashMap<String, String>()
    entries.forEach { parent[it.client.id] = it.client.id }

    fun find(id: String): String {
        var root = id
        while (parent[root] != root) root = parent[root] ?: root
        return root
    }

    fun union(a: String, b: String) {
        parent[find(a)] = find(b)
    }

    val byKey = HashMap<String, String>()
    for (entry in entries) {
        val c = entry.client
        val name = "${c.firstName} ${c.lastName}".trim().lowercase()
        val phone = (c.phone ?: "").filter { it.isDigit() }
        val email = (c.email ?: "").trim().lowercase()
        val keys = buildList {
            if (name.length > 1) add("n:$name")
            if (phone.length >= 6) add("p:$phone")
            if (email.isNotBlank()) add("e:$email")
        }
        for (key in keys) {
            val previous = byKey[key]
            if (previous != null) union(previous, c.id) else byKey[key] = c.id
        }
    }

    return entries
        .groupBy { find(it.client.id) }
        .values
        .filter { it.size >= 2 }
        .map { group -> DuplicateGroup(group.sortedByDescending { it.deals.size }) }
}

/** Wiadomość w rozmowie z asystentem karty klienta. */
data class AssistantMessage(val role: String, val content: String) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/**
 * Odpowiedź asystenta. `configured = false` oznacza brak klucza LLM po stronie
 * serwera — wtedy tekst jest informacyjny, a nie oparty na historii klienta.
 */
data class AssistantReply(
    val text: String,
    val configured: Boolean,
    val commsCount: Int,
    val dealCount: Int,
)
