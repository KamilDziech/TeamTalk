package com.ekotak.teamtalk.domain.usecase.calllog

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MakeCallUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phoneNumber.trim()}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
