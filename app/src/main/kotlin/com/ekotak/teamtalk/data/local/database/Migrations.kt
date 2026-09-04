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
