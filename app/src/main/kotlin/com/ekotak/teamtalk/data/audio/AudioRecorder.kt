package com.ekotak.teamtalk.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun start(): File {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                 else MediaRecorder()
        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mr
        currentFile = file
        return file
    }

    fun stop(): File? = try {
        recorder?.apply { stop(); release() }
        recorder = null
        currentFile.also { currentFile = null }
    } catch (_: Exception) {
        recorder?.release()
        recorder = null
        currentFile?.delete()
        currentFile = null
        null
    }

    fun cancel() {
        try { recorder?.apply { stop(); release() } } catch (_: Exception) {}
        recorder = null
        currentFile?.delete()
        currentFile = null
    }
}
