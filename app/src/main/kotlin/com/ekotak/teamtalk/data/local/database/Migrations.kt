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
        db.execSQL("ALTER TABLE `inventory_orders` ADD COLUMN `reservationId` TEXT")
        // Migawka instalacji deala dostaje etap „sold" — z niego zakładka
        // „Zamówienie" rysuje zakres kupiony przez klienta. Pusty domyślnie:
        // dopisze go pierwszy udany odczyt karty.
        db.execSQL(
            "ALTER TABLE `audit_installations` " +
                "ADD COLUMN `soldStageCategoryIds` TEXT NOT NULL DEFAULT ''",
        )
    }
}
