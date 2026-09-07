package com.ekotak.teamtalk.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Baza jest cache'em, więc zwykle podnosimy wersję i pozwalamy ją skasować.
 * Od wersji 6 leży w niej jednak kolejka niewysłanych zmian zadań
 * (`task_mutations`) — jedyna kopia decyzji podjętych bez zasięgu. Dlatego
 * dokładanie kolejnych tabel cache'u idzie migracją, a nie kasowaniem bazy.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Cache punktów mapy (moduł Mapa). Kształt musi zgadzać się co do znaku
        // z `MapPointEntity` — Room porównuje schemat przy otwarciu bazy.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `map_points` (
                `id` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `lat` REAL,
                `lng` REAL,
                `name` TEXT NOT NULL,
                `city` TEXT,
                `address` TEXT,
                `phone` TEXT,
                `installs` TEXT NOT NULL,
                `ownerId` TEXT,
                `ownerLabel` TEXT,
                `stageOwnerId` TEXT,
                `stageOwnerLabel` TEXT,
                `technicianId` TEXT,
                `technicianLabel` TEXT,
                `badgeKey` TEXT NOT NULL,
                `badgeLabel` TEXT NOT NULL,
                `badgeColor` INTEGER NOT NULL,
                `badgeOrder` INTEGER NOT NULL,
                `badgeLetter` TEXT NOT NULL,
                `dealId` TEXT,
                `clientId` TEXT,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Wersja 8 — cache i kolejka modułu Serwis. Znów migracja, nie kasowanie:
 * w `service_mutations` leżą zgłoszenia awarii spisane bez zasięgu, a to
 * jedyna ich kopia do czasu wysłania.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `service_jobs` (
                `id` TEXT NOT NULL,
                `clientId` TEXT,
                `dealId` TEXT,
                `type` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `priority` TEXT NOT NULL,
                `technicianId` TEXT,
                `scheduledAt` TEXT,
                `note` TEXT,
                `slaHours` INTEGER,
                `slaDueAt` TEXT,
                `slaBreached` INTEGER NOT NULL,
                `localOnly` INTEGER NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `warranty_cards` (
                `id` TEXT NOT NULL,
                `brand` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `location` TEXT,
                `commissionedAt` TEXT,
                `status` TEXT NOT NULL,
                `outdoorModel` TEXT,
                `outdoorSerial` TEXT,
                `indoorModel` TEXT,
                `indoorSerial` TEXT,
                `note` TEXT,
                `inspectionsJson` TEXT NOT NULL,
                `doneCount` INTEGER NOT NULL,
                `overdueCount` INTEGER NOT NULL,
                `suspectCount` INTEGER NOT NULL,
                `nextPlannedAt` TEXT,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `service_clients` (
                `id` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `city` TEXT,
                `phone` TEXT,
                `address` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `service_technicians` (
                `id` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `firstName` TEXT,
                `lastName` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `service_mutations` (
                `jobId` TEXT NOT NULL,
                `field` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`jobId`, `field`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Wersja 9: cache modułu Kalendarz. Kolejka `calendar_mutations` trzyma terminy
 * ustalone bez zasięgu, więc od tej wersji baza ma już trzy kolejki decyzji
 * człowieka (zadania, serwis, kalendarz) — kasowanie jej przy podniesieniu
 * wersji jest tym bardziej wykluczone.
 *
 * Kształt tabel musi zgadzać się co do znaku z encjami z `CalendarEntities.kt`
 * — Room porównuje schemat przy otwarciu bazy.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calendars` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `description` TEXT,
                `ownerId` TEXT NOT NULL,
                `ownerEmail` TEXT,
                `isArchived` INTEGER NOT NULL,
                `effectiveLevel` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calendar_events` (
                `id` TEXT NOT NULL,
                `calendarId` TEXT NOT NULL,
                `calendarColor` TEXT,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `location` TEXT,
                `color` TEXT,
                `startAt` TEXT NOT NULL,
                `endAt` TEXT,
                `allDay` INTEGER NOT NULL,
                `assigneeId` TEXT,
                `assigneeEmail` TEXT,
                `attendeesJson` TEXT NOT NULL,
                `recurrenceGroupId` TEXT,
                `recurrenceRule` TEXT,
                `localOnly` INTEGER NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calendar_members` (
                `id` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `firstName` TEXT,
                `lastName` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calendar_mutations` (
                `eventId` TEXT NOT NULL,
                `field` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`eventId`, `field`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * 9 → 10: prywatna zajętość zespołu (szare pola „Zajęte").
 *
 * Kolega podpina w panelu sekretny adres iCal swojego prywatnego kalendarza,
 * a board360 wyciąga z niego SAME GODZINY. Cache jest po to, żeby te szare pola
 * były widoczne także bez zasięgu — bez nich monter w aucie widziałby wolne
 * popołudnie, na które i tak nie da się nic zaplanować (serwer odbija to 409).
 *
 * Tabela jest czysto odtwarzalna (kasowalna bez straty): nie ma tu decyzji
 * człowieka, tylko kopia tego, co i tak przyjdzie z serwera przy najbliższej
 * synchronizacji. Kolumn na tytuł/opis nie ma CELOWO — nie mamy ich skąd wziąć.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calendar_private_busy` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `startAt` TEXT NOT NULL,
                `endAt` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * 10 → 11: `calendar_members` → `team_members`, z rolą i funkcjami.
 *
 * Filtr osoby dostał drzewo działów (Biuro / Serwis / Montaż / Pozostali,
 * ustalenie 2026-09-04). Dział liczy się z ROLI i FUNKCJI, a tych w cache nie
 * było — bez zasięgu cały zespół wpadałby do „Pozostali". Przy okazji książka
 * przestała należeć do kalendarza: to samo drzewo rysuje Mapa, więc tabela
 * dostała nazwę bez modułu w środku.
 *
 * Wiersze przepisujemy, żeby po aktualizacji nazwiska były na miejscu od razu;
 * rola i funkcje dojdą przy najbliższej synchronizacji (do tego czasu osoba
 * siedzi w „Pozostali"). Listy idą tekstem po przecinku — jak każde
 * `List<String>` w tej bazie (patrz `Converters`).
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `team_members` (
                `id` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `firstName` TEXT,
                `lastName` TEXT,
                `role` TEXT,
                `additionalRoles` TEXT NOT NULL DEFAULT '',
                `functions` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO `team_members` (`id`, `email`, `firstName`, `lastName`, `role`, `additionalRoles`, `functions`)
            SELECT `id`, `email`, `firstName`, `lastName`, NULL, '', '' FROM `calendar_members`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE IF EXISTS `calendar_members`")
    }
}

/**
 * 11 → 12: cache modułu Magazyn (etap E1) — kartoteki, rezerwacje pod klientów
 * i zapotrzebowanie zakupowe.
 *
 * Trzy nowe tabele, zero ruchu w istniejących: to czysty cache odczytu, więc
 * dokładamy je migracją zamiast kasować bazę (leżą w niej kolejki niewysłanych
 * zmian zadań, serwisu i kalendarza — tego skasować nie wolno).
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inventory_products` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `code` TEXT,
                `installation` TEXT,
                `stockType` TEXT NOT NULL,
                `dealId` TEXT,
                `stock` REAL NOT NULL,
                `minQty` REAL NOT NULL,
                `targetQty` REAL NOT NULL,
                `price` REAL,
                `producer` TEXT,
                `distributor` TEXT,
                `distributors` TEXT NOT NULL,
                `distributorPricesJson` TEXT NOT NULL,
                `packaging` TEXT,
                `notes` TEXT,
                `storageZone` TEXT,
                `storageShelf` TEXT,
                `stockPolicy` TEXT,
                `imageUrl` TEXT,
                `leadTimeDays` INTEGER,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inventory_reservations` (
                `id` TEXT NOT NULL,
                `dealId` TEXT NOT NULL,
                `productId` TEXT,
                `itemName` TEXT NOT NULL,
                `itemCode` TEXT,
                `clientLabel` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `unit` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `neededBy` TEXT,
                `note` TEXT,
                `covered` REAL NOT NULL,
                `missing` REAL NOT NULL,
                `productName` TEXT,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inventory_orders` (
                `id` TEXT NOT NULL,
                `productId` TEXT NOT NULL,
                `dealId` TEXT,
                `quantity` REAL NOT NULL,
                `receivedQty` REAL NOT NULL,
                `status` TEXT NOT NULL,
                `distributor` TEXT,
                `unitPrice` REAL,
                `expectedAt` TEXT,
                `note` TEXT,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Zakładka „Audyt" offline: cache audytów, katalogu technologii i migawki
 * instalacji etapu „Audyt" oraz kolejka niewysłanych zapisów.
 *
 * Migracja, a nie skasowanie bazy: w `task_mutations`, `service_mutations`
 * i `calendar_mutations` leżą decyzje zrobione bez zasięgu, a od teraz też
 * w `audit_mutations` — czyli formularz wypełniony u klienta, którego nie da
 * się odtworzyć bez drugiego dojazdu.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `audits` (
                `id` TEXT NOT NULL,
                `dealId` TEXT NOT NULL,
                `heatloadMode` TEXT,
                `heatloadKw` REAL,
                `formData` TEXT,
                `createdAt` TEXT NOT NULL,
                `pendingSince` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `catalog_categories` (
                `id` TEXT NOT NULL,
                `parentId` TEXT,
                `name` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `auditForm` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `audit_installations` (
                `dealId` TEXT NOT NULL,
                `categoryIds` TEXT NOT NULL,
                `allStageCategoryIds` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`dealId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `audit_mutations` (
                `auditId` TEXT NOT NULL,
                `field` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `dealId` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`auditId`, `field`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Moduł Projekty na telefonie (E5). Cztery tabele: projekty, kamienie milowe,
 * zadania z karty projektu i kolejka zmian zrobionych bez zasięgu.
 *
 * `project_tasks` jest CELOWO osobne od `tasks`: tam trzymamy zadania przekazane
 * do realizacji, a z karty projektu przychodzą także `planned` — wrzucenie ich
 * razem pokazałoby ludziom w „Zadaniach" robotę, której nikt im nie zlecił.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `projects` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `color` TEXT,
                `stage` TEXT NOT NULL,
                `department` TEXT,
                `managerEmail` TEXT,
                `sponsorEmail` TEXT,
                `memberCount` INTEGER NOT NULL,
                `taskCount` INTEGER NOT NULL,
                `doneCount` INTEGER NOT NULL,
                `dueAt` TEXT,
                `problemStatement` TEXT,
                `metricName` TEXT,
                `metricBaseline` TEXT,
                `metricTarget` TEXT,
                `membersJson` TEXT,
                `localOnly` INTEGER NOT NULL,
                `cachedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `project_milestones` (
                `id` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `dueAt` TEXT,
                `acceptanceCriteria` TEXT,
                `ownerEmail` TEXT,
                `doneAt` TEXT,
                `position` INTEGER NOT NULL,
                `taskCount` INTEGER NOT NULL,
                `doneCount` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `project_tasks` (
                `id` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `assigneeId` TEXT,
                `assigneeEmail` TEXT,
                `startAt` TEXT,
                `dueAt` TEXT,
                `status` TEXT NOT NULL,
                `lifecycle` TEXT NOT NULL,
                `milestoneId` TEXT,
                `estimatedMinutes` INTEGER,
                `actualMinutes` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `project_mutations` (
                `targetId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`targetId`, `kind`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * 14 → 15: zakładka „Zamówienie" karty deala — zamówienia deala, jego oferty
 * i wspólna kolejka zmian zrobionych bez zasięgu.
 *
 * Przy okazji `inventory_orders` dostaje `reservationId`: po nim wiersz
 * rezerwacji poznaje, że jego brak ktoś już kupuje. Kolumnę dokładamy przez
 * `ALTER TABLE`, a nie przez odtworzenie tabeli — leżą w niej pozycje zakupowe
 * założone offline (`local:…`), których nie wolno zgubić.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `deal_orders` (
                `id` TEXT NOT NULL,
                `dealId` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `contractId` TEXT,
                `installationId` TEXT,
                `installationName` TEXT,
                `source` TEXT NOT NULL,
                `createdAt` TEXT NOT NULL,
                `itemsJson` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `deal_offers` (
                `id` TEXT NOT NULL,
                `dealId` TEXT NOT NULL,
                `number` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `netTotal` REAL NOT NULL,
                `grossTotal` REAL NOT NULL,
                `margin` REAL NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `order_mutations` (
                `targetId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `dealId` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`targetId`, `kind`)
            )
            """.trimIndent(),
        )
        db.addColumnIfMissing("inventory_orders", "reservationId", "TEXT")
        // Migawka instalacji deala dostaje etap „sold" — z niego zakładka
        // „Zamówienie" rysuje zakres kupiony przez klienta. Pusty domyślnie:
        // dopisze go pierwszy udany odczyt karty.
        db.addColumnIfMissing(
            table = "audit_installations",
            column = "soldStageCategoryIds",
            type = "TEXT NOT NULL DEFAULT ''",
        )
    }
}

/**
 * `ALTER TABLE … ADD COLUMN`, które przeżywa telefon deweloperski.
 *
 * Zwykłe `ADD COLUMN` wywraca migrację na „duplicate column name", gdy kolumna
 * już jest — a na telefonach zespołu JEST, i to bez żadnej winy użytkownika:
 * `fallbackToDestructiveMigration` przy COFNIĘCIU wersji (starszy build wgrany
 * po nowszym) kasuje tabele, które zna JEGO schemat, a te z nowszej wersji
 * zostawia w pliku nietknięte — razem z dopisanymi już kolumnami. Potem numer
 * wersji mówi „11", a `inventory_orders` ma komplet kolumn z piętnastki.
 *
 * Wyjątek z `migrate()` jest śmiertelny (destrukcyjny fallback dotyczy braku
 * ŚCIEŻKI migracji, nie jej awarii), więc taka baza blokuje otwarcie Rooma na
 * zawsze — aż do wyczyszczenia danych aplikacji. Sprawdzenie `PRAGMA table_info`
 * kosztuje jedno zapytanie i zdejmuje całą tę klasę awarii.
 */
private fun SupportSQLiteDatabase.addColumnIfMissing(
    table: String,
    column: String,
    type: String,
) {
    val exists = query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
            .any { it == column }
    }
    if (!exists) execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $type")
}

/**
 * Moduł Urlop (zakładka „Urlop" modułu HR panelu). Trzy tabele: własne wnioski
 * i skrzynka zwierzchnika, cudze nieobecności jako tło kalendarza oraz kolejka
 * zapisów czekających na zasięg.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `leave_requests` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `mine` INTEGER NOT NULL,
                `employeeName` TEXT,
                `employeeEmail` TEXT,
                `employeeRole` TEXT,
                `type` TEXT NOT NULL,
                `startDate` TEXT NOT NULL,
                `endDate` TEXT NOT NULL,
                `workingDays` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `reason` TEXT,
                `decisionNote` TEXT,
                `decidedAt` TEXT,
                `canDecide` INTEGER NOT NULL,
                `awaitingName` TEXT,
                `awaitingIsBackup` INTEGER NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `leave_absences` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `employeeName` TEXT,
                `employeeRole` TEXT,
                `startDate` TEXT NOT NULL,
                `endDate` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `leave_balance` (
                `year` INTEGER NOT NULL,
                `mode` TEXT NOT NULL,
                `entitled` INTEGER NOT NULL,
                `used` INTEGER NOT NULL,
                `planned` INTEGER NOT NULL,
                `pending` INTEGER NOT NULL,
                `remaining` INTEGER NOT NULL,
                `onDemandUsed` INTEGER NOT NULL,
                `onDemandTotal` INTEGER NOT NULL,
                `specialDays` INTEGER NOT NULL,
                `unpaidDays` INTEGER NOT NULL,
                `unpaidTotal` INTEGER NOT NULL,
                `employmentType` TEXT,
                `managerId` TEXT,
                `backupDecisionId` TEXT,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`year`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `leave_mutations` (
                `targetId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`targetId`, `kind`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * v17 — zakładka „Oferta" karty deala.
 *
 * Kartoteka Magazynu dostaje dwa pola, których wymaga wycena: `comparisonSize`
 * (wielkość opakowania, np. „50 m²" — bez niej nie da się zejść z ceny rolki na
 * cenę metra) i `defaultChoice` (★ z Magazynu, czyli materiał domyślny, gdy
 * Technologia nic nie wskazała).
 *
 * Migawka instalacji deala dostaje `stagesJson` — wszystkie etapy naraz. Oferta
 * schodzi po nich kaskadą (angebot → audit → sold → montaz → edukacja → lead),
 * dokładnie jak panel; osobne kolumny per etap dokładałyby migrację przy każdym
 * kolejnym czytelniku.
 *
 * Kolumny dochodzą z wartościami domyślnymi, więc stare wiersze zostają —
 * kasować cache Magazynu nie ma po co, świeże pola dojdą przy najbliższym
 * odświeżeniu.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("inventory_products", "comparisonSize", "TEXT")
        db.addColumnIfMissing(
            table = "inventory_products",
            column = "defaultChoice",
            type = "INTEGER NOT NULL DEFAULT 0",
        )
        db.addColumnIfMissing(
            table = "audit_installations",
            column = "stagesJson",
            type = "TEXT NOT NULL DEFAULT '{}'",
        )
    }
}

/**
 * v18 — moduł Email (poczta w układzie Gmaila).
 *
 * Siedem tabel dochodzi migracją, a nie kasowaniem bazy, bo od wersji 6 leży
 * w niej kolejka niewysłanych zmian — jedyna kopia decyzji podjętych bez
 * zasięgu. Nowa kolejka poczty (`email_mutations`) dołącza do tego zbioru.
 *
 * Klucze złożone są tu celowo. `email_threads` trzyma ten sam wątek osobno dla
 * widoku „Moje" i „Wszystkie" (kolumna `scope`), bo wycinek opiekuna liczy
 * serwer i telefon nie ma z czego go odtworzyć; jeden worek oznaczałby, że po
 * wejściu w „Moje" bez zasięgu widać cudzą korespondencję pobraną wcześniej
 * w widoku „Wszystkie". Tak samo `email_folders`: liczniki są inne w każdym
 * widoku, więc para (skrzynka, widok) wchodzi do klucza.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `email_accounts` (
                `id` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `displayName` TEXT,
                `kind` TEXT NOT NULL,
                `canViewAll` INTEGER NOT NULL,
                `unread` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `email_folders` (
                `accountId` TEXT NOT NULL,
                `scope` TEXT NOT NULL,
                `folder` TEXT NOT NULL,
                `total` INTEGER NOT NULL,
                `unread` INTEGER NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`accountId`, `scope`, `folder`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `email_threads` (
                `id` TEXT NOT NULL,
                `accountId` TEXT NOT NULL,
                `scope` TEXT NOT NULL,
                `subject` TEXT NOT NULL,
                `folder` TEXT NOT NULL,
                `lastAt` TEXT NOT NULL,
                `unread` INTEGER NOT NULL,
                `starred` INTEGER NOT NULL,
                `dealId` TEXT,
                `dealLabel` TEXT,
                `fromName` TEXT,
                `fromAddr` TEXT NOT NULL,
                `snippet` TEXT NOT NULL,
                `messageCount` INTEGER NOT NULL,
                `hasAttachment` INTEGER NOT NULL,
                `labelsRaw` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`, `accountId`, `scope`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `email_messages` (
                `id` TEXT NOT NULL,
                `threadId` TEXT NOT NULL,
                `outbound` INTEGER NOT NULL,
                `fromAddr` TEXT NOT NULL,
                `fromName` TEXT,
                `toAddrs` TEXT NOT NULL,
                `ccAddrs` TEXT NOT NULL,
                `subject` TEXT NOT NULL,
                `bodyText` TEXT,
                `status` TEXT NOT NULL,
                `createdAt` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `email_attachments` (
                `id` TEXT NOT NULL,
                `messageId` TEXT NOT NULL,
                `filename` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `sizeBytes` INTEGER NOT NULL,
                `localUri` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `email_labels` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `email_mutations` (
                `targetId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`targetId`, `kind`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * v19 — zakładka „Pliki" karty deala.
 *
 * Dwie tabele: cache metadanych plików i kolejka niewysłanych decyzji. Wchodzą
 * migracją, a nie kasowaniem bazy, bo od wersji 6 leży w niej kolejka zmian
 * zrobionych bez zasięgu — jedyna kopia takich decyzji.
 *
 * `deal_documents` trzyma także pliki wgrane offline (id z prefiksem `local-`,
 * `pending = 1`): treść siedzi wtedy w pamięci aplikacji pod `localPath`,
 * a wiersz jest jedynym śladem, że zdjęcie w ogóle zrobiono. Dlatego kolejka
 * NIE dubluje nazwy ani sekcji pliku — bierze je z tego wiersza przy wysyłce.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `deal_documents` (
                `id` TEXT NOT NULL,
                `dealId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `size` INTEGER NOT NULL,
                `contentType` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `planDataJson` TEXT,
                `createdAt` TEXT NOT NULL,
                `pending` INTEGER NOT NULL,
                `localPath` TEXT,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `document_mutations` (
                `targetId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `dealId` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`targetId`, `kind`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Wersja 20 — cache i kolejka zakładki „Rozliczenie". Migracja, nie kasowanie:
 * w `settlement_mutations` leżą zatwierdzenia rozliczeń podjęte bez zasięgu
 * (rozliczenie domyka się na budowie, po odbiorze), a to jedyna ich kopia do
 * czasu wysłania.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `deal_settlements` (
                `dealId` TEXT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `totalPoints` REAL NOT NULL,
                `breakdownJson` TEXT NOT NULL,
                `approvedById` TEXT,
                `approvedAt` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`dealId`, `categoryId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `settlement_mutations` (
                `dealId` TEXT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`dealId`, `categoryId`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Wersja 21 — zakładka „Harmonogram" karty deala. Dwie kolumny w `projects`:
 * `dealId` (po czym zakładka wybiera projekty tego deala) i `status` (panel
 * dopisuje przy nazwie „· archiwum").
 *
 * `ALTER TABLE`, a nie odtworzenie tabeli: w `projects` leżą pomysły zgłoszone
 * bez zasięgu (`localOnly = 1`) czekające w `project_mutations`, a to jedyna
 * ich kopia do czasu wysłania.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing(table = "projects", column = "dealId", type = "TEXT")
        db.addColumnIfMissing(table = "projects", column = "status", type = "TEXT")
    }
}

/**
 * Wersja 22 — cache i kolejka karty deala (zakładka „Remarketing").
 * W `deal_mutations` leżą zakresy instalacji i ustalenia spotkania zapisane bez
 * zasięgu, u klienta — to jedyna ich kopia do czasu wysłania, więc migracja,
 * a nie skasowanie bazy.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `deal_installations` (
                `dealId` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL,
                PRIMARY KEY(`dealId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `deal_mutations` (
                `dealId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`dealId`, `kind`)
            )
            """.trimIndent(),
        )
    }
}
