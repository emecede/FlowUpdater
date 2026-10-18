package com.family.flowupdater

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Net {
    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true   // GitHub release links redirect
            useCaches = false
        }

    fun fetchManifest(): RemoteManifest {
        val conn = open(Config.MANIFEST_URL + "?t=" + System.currentTimeMillis())
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return RemoteManifest.parse(text)
        } finally {
            conn.disconnect()
        }
    }

    fun download(url: String, dest: File, sha256: String?, onProgress: (Long, Long) -> Unit) {
        val conn = open(url)
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLength.toLong()
            // Need room for the download plus the installer's copy.
            if (total > 0 && dest.parentFile!!.usableSpace < total * 2) {
                throw IOException(Texts.NO_SPACE)
            }
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastReport = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (done - lastReport > 256 * 1024) {
                            onProgress(done, total); lastReport = done
                        }
                    }
                }
            }
            onProgress(done, total)
            if (sha256 != null) {
                val actual = md.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(sha256, ignoreCase = true)) throw IOException(Texts.HASH_BAD)
            }
        } finally {
            conn.disconnect()
        }
    }
}
