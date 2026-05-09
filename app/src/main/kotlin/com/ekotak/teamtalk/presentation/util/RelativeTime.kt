package com.ekotak.teamtalk.presentation.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val isoParser = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

fun String.toRelativeTime(): String {
    val ms = try { isoParser.get()!!.parse(this)?.time ?: return this } catch (_: Exception) { return this }
    val diffMs = System.currentTimeMillis() - ms
    val diffMin = diffMs / 60_000
    return when {
        diffMin < 1    -> "Przed chwilą"
        diffMin < 60   -> "$diffMin min temu"
        diffMin < 1440 -> "${diffMin / 60} godz. temu"
        diffMin < 2880 -> "Wczoraj"
        diffMin < 10_080 -> "${diffMin / 1440} dni temu"
        else -> {
            val fmt = SimpleDateFormat("dd.MM.yyyy", Locale("pl", "PL")).apply {
                timeZone = TimeZone.getDefault()
            }
            try { fmt.format(isoParser.get()!!.parse(this) ?: return this) } catch (_: Exception) { this }
        }
    }
}
