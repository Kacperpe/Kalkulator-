package pl.kacper.kalkulator

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aktualizacje z publicznych wydań GitHuba (bez tokena).
 * CI ustawia versionCode = 100 + numer budowania, a wydanie ma tag "v<numer budowania>".
 */
object Updater {
    private const val API = "https://api.github.com/repos/Kacperpe/Kalkulator-/releases/latest"
    private const val AUTO_EVERY_MS = 6L * 60 * 60 * 1000
    @Volatile private var busy = false

    private class Release(val build: Int, val tag: String, val notes: String, val apkUrl: String, val size: Long)

    private fun myCode(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
    }

    private fun myName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Kalkulator-Android")
        return c
    }

    private fun fetchLatest(): Release? {
        val c = open(API)
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode != 200) return null
            val js = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = js.optString("tag_name")
            val build = tag.trimStart('v', 'V').toIntOrNull() ?: return null
            val assets = js.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return Release(build, tag, js.optString("body"), a.getString("browser_download_url"), a.optLong("size"))
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    /** manual = z menu (pokazuje też "masz najnowszą"); inaczej cicho, najwyżej co kilka godzin. */
    fun check(act: Activity, manual: Boolean, note: (String) -> Unit) {
        if (busy) return
        val prefs = act.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("last", 0) < AUTO_EVERY_MS) return
        busy = true
        if (manual) note("Sprawdzam aktualizacje…")
        Thread {
            val rel = try { fetchLatest() } catch (e: Exception) { null }
            act.runOnUiThread {
                busy = false
                if (act.isFinishing) return@runOnUiThread
                if (rel == null) {
                    if (manual) note("Nie udało się sprawdzić aktualizacji")
                    return@runOnUiThread
                }
                prefs.edit().putLong("last", now).apply()
                val newer = 100L + rel.build > myCode(act)
                val skipped = prefs.getInt("skip", -1) == rel.build
                if (!newer) {
                    if (manual) note("Masz najnowszą wersję (" + myName(act) + ")")
                } else if (manual || !skipped) {
                    offer(act, rel, prefs, note)
                }
            }
        }.start()
    }

    private fun offer(act: Activity, rel: Release, prefs: android.content.SharedPreferences, note: (String) -> Unit) {
        val mb = if (rel.size > 0) " (" + (rel.size / 1048576) + " MB)" else ""
        var msg = "Dostępna jest nowa wersja " + rel.tag + mb + "."
        val notes = rel.notes.lines()
            .filterNot { it.startsWith("Co-Authored-By") || it.startsWith("Claude-Session") }
            .joinToString("\n").trim()
        if (notes.isNotEmpty()) msg += "\n\n" + notes.take(600)
        AlertDialog.Builder(act)
            .setTitle("Aktualizacja")
            .setMessage(msg)
            .setPositiveButton("Zaktualizuj") { _, _ -> download(act, rel, note) }
            .setNeutralButton("Pomiń tę wersję") { _, _ -> prefs.edit().putInt("skip", rel.build).apply() }
            .setNegativeButton("Później", null)
            .show()
    }

    private fun download(act: Activity, rel: Release, note: (String) -> Unit) {
        val d = act.resources.displayMetrics.density
        val tv = TextView(act)
        tv.text = "Pobieranie… 0%"
        tv.textSize = 16f
        tv.setPadding((24 * d).toInt(), (16 * d).toInt(), (24 * d).toInt(), (8 * d).toInt())
        val dlg = AlertDialog.Builder(act).setTitle("Aktualizacja").setView(tv).setCancelable(false).show()
        val dir = File(act.cacheDir, "updates")
        dir.mkdirs()
        val out = File(dir, "Kalkulator.apk")
        Thread {
            var ok = false
            try {
                val c = open(rel.apkUrl)
                try {
                    if (c.responseCode == 200) {
                        val total = if (c.contentLengthLong > 0) c.contentLengthLong else rel.size
                        var done = 0L
                        var lastPct = -1
                        c.inputStream.use { inp ->
                            out.outputStream().use { o ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = inp.read(buf)
                                    if (n < 0) break
                                    o.write(buf, 0, n)
                                    done += n
                                    if (total > 0) {
                                        val pct = (done * 100 / total).toInt()
                                        if (pct != lastPct) {
                                            lastPct = pct
                                            act.runOnUiThread { tv.text = "Pobieranie… $pct%" }
                                        }
                                    }
                                }
                            }
                        }
                        ok = total <= 0 || done == total
                    }
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                ok = false
            }
            act.runOnUiThread {
                try { dlg.dismiss() } catch (e: Exception) { }
                if (ok) install(act, out, note) else note("Nie udało się pobrać aktualizacji")
            }
        }.start()
    }

    private fun install(act: Activity, f: File, note: (String) -> Unit) {
        try {
            val uri = FileProvider.getUriForFile(act, act.packageName + ".files", f)
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, "application/vnd.android.package-archive")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            act.startActivity(i)
        } catch (e: Exception) {
            note("Nie udało się uruchomić instalacji")
        }
    }
}
