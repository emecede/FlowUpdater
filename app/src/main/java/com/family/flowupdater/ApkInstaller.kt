package com.family.flowupdater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

data class Installed(val code: Long, val name: String)

fun installedVersion(ctx: Context, pkg: String): Installed? = try {
    val pi = ctx.packageManager.getPackageInfo(pkg, 0)
    @Suppress("DEPRECATION")
    val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    Installed(code, pi.versionName ?: "?")
} catch (e: PackageManager.NameNotFoundException) {
    null
}

object InstallEvents {
    @Volatile var listener: ((Boolean, String) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    fun post(ok: Boolean, msg: String) = main.post { listener?.invoke(ok, msg) }
}

object ApkInstaller {
    const val ACTION = "com.family.flowupdater.INSTALL_RESULT"

    private val supportedAbis: Set<String> =
        Build.SUPPORTED_ABIS.map { it.lowercase().replace('-', '_') }.toSet()

    // e.g. config.arm64_v8a.apk / split_config.armeabi_v7a.apk
    private val abiRegex = Regex(
        """(?i)config[._-](arm64[_-]v8a|armeabi[_-]v7a|armeabi|x86[_-]64|x86|mips64|mips)\.apk$"""
    )

    private fun abiAllowed(name: String): Boolean {
        val m = abiRegex.find(name) ?: return true
        return supportedAbis.contains(m.groupValues[1].lowercase().replace('-', '_'))
    }

    /**
     * Installs either an XAPK (zip with .apk files at its root) or a plain APK,
     * as one PackageInstaller session = one confirmation prompt.
     */
    fun install(ctx: Context, file: File, expectedPackage: String) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(expectedPackage)
        val id = installer.createSession(params)
        var session: PackageInstaller.Session? = null
        try {
            session = installer.openSession(id)
            ZipFile(file).use { zip ->
                val apks = zip.entries().asSequence()
                    .filter { !it.isDirectory && !it.name.contains('/') && it.name.endsWith(".apk", true) }
                    .filter { abiAllowed(it.name) }
                    .toList()
                if (apks.isEmpty()) {
                    // Plain APK
                    file.inputStream().use { write(session, "base.apk", it, file.length()) }
                } else {
                    for (e in apks) zip.getInputStream(e).use { write(session, e.name, it, e.size) }
                }
            }
            val intent = Intent(ctx, InstallReceiver::class.java).setAction(ACTION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(ctx, id, intent, flags)
            session.commit(pending.intentSender)
        } catch (e: Exception) {
            session?.abandon()
            throw e
        } finally {
            session?.close()
        }
    }

    private fun write(s: PackageInstaller.Session, name: String, input: InputStream, size: Long) {
        s.openWrite(name, 0, size).use { out ->
            input.copyTo(out)
            s.fsync(out)
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> InstallEvents.post(true, Texts.INSTALL_OK)
            else -> InstallEvents.post(false, describe(status, msg))
        }
    }

    private fun describe(status: Int, msg: String): String = when {
        msg.contains("UPDATE_INCOMPATIBLE") ->
            "La firma del APK no coincide con la app instalada. " +
                "Hay que desinstalar Flow TV manualmente (se pierden los datos) o usar un APK original."
        msg.contains("VERSION_DOWNGRADE") ->
            "La versión descargada es más antigua que la instalada."
        status == PackageInstaller.STATUS_FAILURE_STORAGE -> Texts.NO_SPACE
        status == PackageInstaller.STATUS_FAILURE_ABORTED -> "Instalación cancelada."
        status == PackageInstaller.STATUS_FAILURE_BLOCKED ->
            "Instalación bloqueada. Activá «Instalar apps desconocidas» para este actualizador."
        status == PackageInstaller.STATUS_FAILURE_INVALID ->
            "El archivo no es válido o está dañado."
        status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "Esta versión no es compatible con este dispositivo."
        status == PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Conflicto con la versión instalada."
        else -> "Falló la instalación (código $status). $msg"
    }
}
