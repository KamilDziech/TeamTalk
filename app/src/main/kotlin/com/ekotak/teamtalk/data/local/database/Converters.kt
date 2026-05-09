package com.ekotak.teamtalk.data.local.database

import androidx.room.TypeConverter

class Converters {

    /** Stores List<String> as comma-separated text. Safe for UUID values. */
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(separator = ",")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",")
}
