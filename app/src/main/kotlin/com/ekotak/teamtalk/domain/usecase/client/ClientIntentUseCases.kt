package com.ekotak.teamtalk.domain.usecase.client

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Wiadomość SMS do klienta — otwiera systemową aplikację wiadomości. */
@Singleton
class SendSmsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phoneNumber.trim()}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/**
 * Nawigacja do adresu klienta. Mamy współrzędne z geokodowania — wtedy jedziemy
 * po nich (adres bywa niejednoznaczny); bez nich zostaje szukanie po tekście.
 */
@Singleton
class NavigateToClientUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(address: String?, lat: Double?, lng: Double?) {
        val uri = when {
            lat != null && lng != null -> Uri.parse("geo:$lat,$lng?q=$lat,$lng")
            !address.isNullOrBlank() -> Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            else -> return
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
