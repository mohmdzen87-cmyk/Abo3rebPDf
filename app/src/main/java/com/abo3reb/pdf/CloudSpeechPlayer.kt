package com.abo3reb.pdf

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Streams generated Arabic speech from the Abo3reb PDF backend one paragraph at a time.
 * No Google Cloud credentials are stored in the APK; the backend uses server-side identity.
 */
class CloudSpeechPlayer(
    context: Context,
    private val onStateChanged: (active: Boolean, error: String?) -> Unit
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile var isActive: Boolean = false
        private set
    private var generation = 0
    private var chunks: List<String> = emptyList()
    private var nextIndex = 0
    private var baseUrl = ""
    private var voice = "male"
    private var player: MediaPlayer? = null
    private var currentFile: File? = null

    fun start(text: String, serviceUrl: String, selectedVoice: String) {
        stop(notify = false)
        val normalizedUrl = serviceUrl.trim().trimEnd('/')
        if (!normalizedUrl.startsWith("https://", ignoreCase = true)) {
            onStateChanged(false, "Cloud speech service URL must use HTTPS")
            return
        }
        chunks = splitText(text)
        if (chunks.isEmpty()) {
            onStateChanged(false, "No readable text")
            return
        }
        baseUrl = normalizedUrl
        voice = if (selectedVoice == "female") "female" else "male"
        nextIndex = 0
        generation++
        isActive = true
        onStateChanged(true, null)
        fetchAndPlay(generation)
    }

    fun stop(notify: Boolean = true) {
        generation++
        isActive = false
        chunks = emptyList()
        nextIndex = 0
        player?.setOnPreparedListener(null)
        player?.setOnCompletionListener(null)
        player?.setOnErrorListener(null)
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        currentFile?.delete()
        currentFile = null
        if (notify) onStateChanged(false, null)
    }

    private fun fetchAndPlay(session: Int) {
        if (!isActive || session != generation) return
        if (nextIndex >= chunks.size) {
            finish(session, null)
            return
        }
        val text = chunks[nextIndex]
        executor.execute {
            var audioFile: File? = null
            try {
                val connection = (URL("$baseUrl/v1/synthesize").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val body = JSONObject().put("text", text).put("voice", voice).toString()
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    if (status !in 200..299) throw IllegalStateException("Speech service returned HTTP $status")
                    val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val encoded = JSONObject(response).getString("audio_base64")
                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                    if (bytes.isEmpty()) throw IllegalStateException("Speech service returned empty audio")
                    audioFile = File.createTempFile("abo3reb-speech-", ".mp3", appContext.cacheDir)
                    FileOutputStream(audioFile).use { it.write(bytes) }
                } finally {
                    connection.disconnect()
                }
                val readyFile = audioFile
                main.post {
                    if (!isActive || session != generation || readyFile == null) {
                        readyFile?.delete()
                    } else {
                        playFile(readyFile, session)
                    }
                }
            } catch (error: Exception) {
                audioFile?.delete()
                main.post {
                    if (isActive && session == generation) {
                        finish(session, error.message ?: "Cloud speech request failed")
                    }
                }
            }
        }
    }

    private fun playFile(file: File, session: Int) {
        if (!isActive || session != generation) {
            file.delete()
            return
        }
        currentFile?.delete()
        currentFile = file
        val media = MediaPlayer()
        player = media
        try {
            media.setDataSource(file.absolutePath)
            media.setOnPreparedListener {
                if (isActive && session == generation) it.start() else {
                    try { it.release() } catch (_: Exception) {}
                    file.delete()
                }
            }
            media.setOnCompletionListener {
                it.release()
                if (player === it) player = null
                file.delete()
                if (currentFile == file) currentFile = null
                nextIndex++
                fetchAndPlay(session)
            }
            media.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (player === mp) player = null
                file.delete()
                if (currentFile == file) currentFile = null
                finish(session, "Could not play generated audio")
                true
            }
            media.prepareAsync()
        } catch (error: Exception) {
            try { media.release() } catch (_: Exception) {}
            if (player === media) player = null
            file.delete()
            finish(session, error.message ?: "Could not play generated audio")
        }
    }

    private fun finish(session: Int, error: String?) {
        if (session != generation) return
        isActive = false
        try { player?.release() } catch (_: Exception) {}
        player = null
        currentFile?.delete()
        currentFile = null
        onStateChanged(false, error)
    }

    private fun splitText(source: String): List<String> {
        val normalized = source.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("(?<!\n)\n(?!\n)"), " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" *\n *"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (normalized.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var remaining = normalized
        while (remaining.length > 4000) {
            val limit = 4000
            val lower = limit / 2
            val cut = sequenceOf(
                remaining.lastIndexOf("\n\n", limit),
                remaining.lastIndexOf('\n', limit),
                remaining.lastIndexOfAny(charArrayOf('؟', '!', '.', '؛', ':', '،'), limit),
                remaining.lastIndexOf(' ', limit)
            ).firstOrNull { it >= lower } ?: limit
            result += remaining.substring(0, cut + 1).trim()
            remaining = remaining.substring(cut + 1).trim()
        }
        if (remaining.isNotBlank()) result += remaining
        return result.filter { it.isNotBlank() }
    }
}
