package com.family.flowupdater

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var remote: RemoteManifest? = null
    private var busy = false

    private lateinit var footer: TextView
    private lateinit var refreshBtn: Button
    private lateinit var flowRow: Row
    private lateinit var selfRow: Row

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121212.toInt())
            setPadding(dp(64), dp(40), dp(64), dp(40))
        }
        root.addView(label(Texts.TITLE, 34f, Color.WHITE))

        flowRow = Row(Texts.FLOW_NAME, Config.FLOW_PACKAGE) { remote?.flow }
        selfRow = Row(Texts.SELF_NAME, packageName) { remote?.updater }
        root.addView(flowRow.view)
        root.addView(selfRow.view)

        footer = label("", 20f, 0xFFFFB300.toInt()).apply { setPadding(0, dp(28), 0, dp(12)) }
        root.addView(footer)

        refreshBtn = styledButton(Texts.BTN_REFRESH).apply {
            setOnClickListener { busy = false; refresh() }
        }
        root.addView(refreshBtn)
        setContentView(root)

        InstallEvents.listener = { ok, msg ->
            busy = false
            flowRow.bar.visibility = View.GONE
            selfRow.bar.visibility = View.GONE
            refresh(msg.also { footerKeep = it })
        }
        refresh()
    }

    private var footerKeep: String? = null

    override fun onDestroy() {
        InstallEvents.listener = null
        super.onDestroy()
    }

    // ---------- logic ----------

    private fun refresh(message: String? = null) {
        footer.text = message ?: Texts.CHECKING
        render()
        io.execute {
            try {
                val m = Net.fetchManifest()
                ui.post {
                    remote = m
                    render()
                    footer.text = message ?: ""
                }
            } catch (e: Exception) {
                ui.post { footer.text = Texts.error(e); render() }
            }
        }
    }

    private fun render() {
        for (row in listOf(flowRow, selfRow)) {
            val inst = installedVersion(this, row.pkg)
            val rel = row.release()
            val sb = StringBuilder()
            sb.append(inst?.let { Texts.installed(it.name, it.code) } ?: Texts.NOT_INSTALLED)
            if (rel != null) {
                sb.append("   |   ").append(Texts.available(rel.versionName, rel.versionCode))
                rel.notes?.let { sb.append("\n").append(it) }
            }
            row.info.text = sb.toString()

            val (label, enabled) = when {
                rel == null -> Texts.BTN_NONE to false
                inst == null -> Texts.BTN_INSTALL to true
                rel.versionCode > inst.code -> Texts.BTN_UPDATE to true
                else -> Texts.BTN_OK to false
            }
            row.button.text = label
            row.button.isEnabled = enabled && !busy
        }
    }

    private fun canInstall() =
        Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()

    private fun openInstallSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            footer.text = Texts.NEED_PERMISSION +
                " (Ajustes > Mi Fire TV > Opciones para desarrolladores > Instalar apps desconocidas)"
        }
    }

    private fun startInstall(row: Row) {
        val rel = row.release() ?: return
        if (!canInstall()) {
            footer.text = Texts.NEED_PERMISSION
            openInstallSettings()
            return
        }
        busy = true
        render()
        row.bar.progress = 0
        row.bar.visibility = View.VISIBLE
        footer.text = Texts.DOWNLOADING

        val appCtx = applicationContext
        io.execute {
            val file = File(cacheDir, "download.bin")
            try {
                file.delete()
                Net.download(rel.url, file, rel.sha256) { done, total ->
                    if (total > 0) ui.post { row.bar.progress = (done * 100 / total).toInt() }
                }
                ui.post { footer.text = Texts.PREPARING }
                ApkInstaller.install(appCtx, file, row.pkg)
                ui.post { footer.text = Texts.CONFIRM }
            } catch (e: Exception) {
                ui.post {
                    busy = false
                    row.bar.visibility = View.GONE
                    footer.text = Texts.error(e)
                    render()
                }
            } finally {
                file.delete()
            }
        }
    }

    // ---------- UI helpers ----------

    private inner class Row(val name: String, val pkg: String, val release: () -> Release?) {
        val info = label("", 20f, 0xFFBDBDBD.toInt())
        val bar = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal)
            .apply { max = 100; visibility = View.GONE }
        val button = styledButton("…").also { b -> b.setOnClickListener { startInstall(this) } }
        val view = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(28), 0, 0)
            addView(label(name, 28f, Color.WHITE))
            addView(info)
            addView(bar)
            addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) })
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, size: Float, color: Int) =
        TextView(this).apply { text = t; textSize = size; setTextColor(color) }

    private fun rect(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(12).toFloat()
    }

    private fun styledButton(t: String) = Button(this).apply {
        text = t
        textSize = 22f
        isAllCaps = false
        setTextColor(Color.WHITE)
        isFocusable = true
        background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), rect(0xFF424242.toInt()))
            addState(intArrayOf(android.R.attr.state_focused), rect(0xFFFF6F00.toInt()))
            addState(intArrayOf(), rect(0xFF1565C0.toInt()))
        }
        setPadding(dp(32), dp(12), dp(32), dp(12))
    }
}
