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
