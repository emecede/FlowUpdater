package com.family.flowupdater

import org.json.JSONObject

object Config {
    /** Raw URL of your manifest.json on GitHub. CHANGE THIS. */
    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/manifest.json"

    const val FLOW_PACKAGE = "ar.com.flow.androidtv"
}

data class Release(
    val versionCode: Long,
    val versionName: String,
    val url: String,
    val sha256: String?,
    val notes: String?
)

data class RemoteManifest(val flow: Release?, val updater: Release?) {
    companion object {
        fun parse(json: String): RemoteManifest {
            val root = JSONObject(json)
            return RemoteManifest(
                release(root.optJSONObject("flow")),
                release(root.optJSONObject("updater"))
            )
        }

        private fun release(o: JSONObject?): Release? {
            if (o == null || !o.has("url")) return null
            return Release(
                versionCode = o.optLong("versionCode"),
                versionName = o.optString("versionName"),
                url = o.getString("url"),
                sha256 = o.optString("sha256").trim().ifBlank { null }?.lowercase(),
                notes = o.optString("notes").trim().ifBlank { null }
            )
        }
    }
}

/** All user-visible text (Spanish). Edit here to change wording or language. */
object Texts {
    const val TITLE = "Actualizador de Flow"
    const val FLOW_NAME = "Flow TV"
    const val SELF_NAME = "Este actualizador"
    const val NOT_INSTALLED = "No instalada"
    const val CHECKING = "Buscando actualizaciones…"
    const val UP_TO_DATE = "Todo al día ✓"
    const val DOWNLOADING = "Descargando… (puede tardar unos minutos)"
    const val PREPARING = "Preparando instalación…"
    const val CONFIRM = "Confirmá la instalación en la pantalla que aparece."
    const val NEED_PERMISSION =
        "Activá «Instalar apps desconocidas» para esta app y volvé a intentar."
    const val HASH_BAD = "El archivo descargado está dañado (checksum incorrecto)."
    const val NO_SPACE = "No hay espacio suficiente en el dispositivo."
    const val INSTALL_OK = "¡Instalación completada!"
    const val BTN_INSTALL = "Instalar"
    const val BTN_UPDATE = "Actualizar"
    const val BTN_OK = "Al día"
    const val BTN_NONE = "Sin información"
    const val BTN_REFRESH = "Buscar de nuevo"
    fun error(e: Exception) = "Error: ${e.message ?: e.javaClass.simpleName}"
    fun installed(name: String, code: Long) = "Instalada: $name ($code)"
    fun available(name: String, code: Long) = "Disponible: $name ($code)"
}
