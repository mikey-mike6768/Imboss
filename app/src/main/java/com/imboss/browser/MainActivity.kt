package com.imboss.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

class MainActivity : Activity() {
    private data class Tab(val web: WebView, var url: String = "imboss://home", var title: String = "New tab")
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var tabStrip: LinearLayout
    private lateinit var content: FrameLayout
    private val tabs = mutableListOf<Tab>()
    private var active = 0
    private var dark = true
    private var privateMode = false
    private var accent = 0xFF86A8FF.toInt()
    private var engine = "Google"
    private val prefs by lazy { getSharedPreferences("imboss_settings", Context.MODE_PRIVATE) }
    private val services = linkedMapOf(
        "Spotify" to "https://open.spotify.com",
        "Apple Music" to "https://music.apple.com",
        "Netflix" to "https://www.netflix.com",
        "Prime Video" to "https://www.primevideo.com",
        "Telegram" to "https://web.telegram.org",
        "WhatsApp" to "https://web.whatsapp.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark = prefs.getBoolean("dark", true)
        accent = prefs.getInt("accent", accent)
        engine = prefs.getString("engine", "Google") ?: "Google"
        buildUi()
        newTab()
    }

    private fun bg() = if (dark) Color.rgb(10, 15, 29) else Color.rgb(232, 238, 248)
    private fun fg() = if (dark) Color.WHITE else Color.rgb(23, 32, 50)
    private fun muted() = if (dark) 0xFF9EABC2.toInt() else 0xFF66748B.toInt()
    private fun glass(radius: Float = 25f) = GradientDrawable().apply {
        setColor(if (dark) 0xD927354D.toInt() else 0xEFFFFFFF.toInt())
        cornerRadius = radius
        setStroke(1, if (dark) 0x55FFFFFF else 0x667D8DA8)
    }

    private fun txt(value: String, size: Float = 14f, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(fg()); gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun btn(value: String, action: () -> Unit) = TextView(this).apply {
        text = value; textSize = 14f; setTextColor(fg()); gravity = Gravity.CENTER
        includeFontPadding = false
        minHeight = 48
        setPadding(14, 4, 14, 4); background = glass(); isClickable = true
        setOnClickListener { action() }
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 4, 10, 6)
            setBackgroundColor(bg())
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(5, 0, 2, 5) }
        header.addView(txt("IMBOSS", 22f, true).apply {
            letterSpacing = 0.16f; setTextColor(accent)
        }, LinearLayout.LayoutParams(0, 46, 1f))
        header.addView(btn("⚙") { settingsDialog() })
        header.addView(btn("▢ " + tabs.size) { tabsDialog() })
        root.addView(header)

        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = glass(32f); setPadding(7, 2, 5, 2)
        }
        address = EditText(this).apply {
            hint = "Search or enter website"; textSize = 15f
            setTextColor(fg()); setHintTextColor(muted()); setSingleLine(true)
            setBackgroundColor(Color.TRANSPARENT); imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(10, 9, 5, 9); setSelectAllOnFocus(true)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                    navigate(text.toString()); true
                } else false
            }
        }
        bar.addView(address, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(btn("☆") { bookmark() })
        bar.addView(btn("Go") { navigate(address.text.toString()) })
        root.addView(bar)

        val tabsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setPadding(0, 7, 0, 7) }
        tabStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabsScroll.addView(tabStrip)
        root.addView(tabsScroll, LinearLayout.LayoutParams(-1, 48))

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, 8, 0, 0) }
        listOf(
            "‹" to { back() }, "›" to { forward() }, "⌂" to { home() },
            "＋" to { newTab() }, "⋯" to { menuDialog() }
        ).forEach { pair ->
            bottom.addView(btn(pair.first, pair.second), LinearLayout.LayoutParams(0, 48, 1f).apply {
                setMargins(3, 0, 3, 0)
            })
        }
        root.addView(bottom)
        setContentView(root)
        window.statusBarColor = bg()
        window.navigationBarColor = bg()
        window.decorView.systemUiVisibility = if (dark) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
    }

    private fun homePanel(): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            addJavascriptInterface(HomeBridge(), "Imboss")
            loadUrl("file:///android_asset/home/index.html")
        }
    }

    inner class HomeBridge {
        @JavascriptInterface fun isDark(): Boolean = dark
        @JavascriptInterface fun isPrivate(): Boolean = privateMode
        @JavascriptInterface fun getAccent(): String = String.format("#%06X", 0xFFFFFF and accent)
        @JavascriptInterface fun openUrl(url: String) { runOnUiThread { navigate(url) } }
        @JavascriptInterface fun setDark(value: Boolean) {
            runOnUiThread {
                dark = value
                prefs.edit().putBoolean("dark", dark).apply()
                root.background = pageBackground()
                window.statusBarColor = bg()
                window.navigationBarColor = bg()
                showActive()
            }
        }
        @JavascriptInterface fun manageServices() { runOnUiThread { this@MainActivity.manageServices() } }
        @JavascriptInterface fun openBookmarks() { runOnUiThread { savedList("Bookmarks", "bookmarks") } }
        @JavascriptInterface fun openHistory() { runOnUiThread { savedList("History", "history") } }
        @JavascriptInterface fun openDownloads() { runOnUiThread { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } }
    }

    private fun serviceIcon(name: String) = when (name) {
        "Spotify" -> "♫"; "Apple Music" -> "♪"; "Netflix" -> "N"
        "Prime Video" -> "▶"; "Telegram" -> "➤"; else -> "◉"
    }

    private fun createWebView(): WebView = WebView(this).apply {
        setBackgroundColor(Color.WHITE)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.safeBrowsingEnabled = true
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                tabs.firstOrNull { it.web === view }?.let { it.title = title?.take(24) ?: it.title; refreshTabs() }
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                val tab = tabs.firstOrNull { it.web === view } ?: return
                tab.url = url ?: tab.url
                if (tabs.indexOf(tab) == active) address.setText(tab.url)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                val tab = tabs.firstOrNull { it.web === view } ?: return
                tab.url = url ?: tab.url
                if (!privateMode && tab.url.startsWith("http")) addHistory(tab.url)
                if (tabs.indexOf(tab) == active) address.setText(tab.url)
                refreshTabs()
            }
        }
        setDownloadListener(DownloadListener { url, userAgent, disposition, mime, _ ->
            try {
                val filename = android.webkit.URLUtil.guessFileName(url, disposition, mime)
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mime)
                    .addRequestHeader("User-Agent", userAgent)
                    .setTitle(filename).setDescription("Downloading with IMBOSS")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                toast("Download started")
            } catch (_: Exception) { toast("Download could not start") }
        })
    }

    private fun newTab(url: String? = null) {
        val web = createWebView()
        val tab = Tab(web, url ?: "imboss://home")
        tabs.add(tab); active = tabs.lastIndex
        showActive()
        if (url != null) web.loadUrl(url)
    }

    private fun showActive() {
        content.removeAllViews()
        val tab = tabs.getOrNull(active) ?: return
        if (tab.url == "imboss://home") {
            content.addView(homePanel(), FrameLayout.LayoutParams(-1, -1))
            address.setText("")
        } else {
            content.addView(tab.web, FrameLayout.LayoutParams(-1, -1))
            address.setText(tab.url)
        }
        refreshTabs()
    }

    private fun switchTab(index: Int) {
        if (index !in tabs.indices) return
        active = index
        showActive()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        tabs[index].web.stopLoading()
        tabs[index].web.destroy()
        tabs.removeAt(index)
        if (tabs.isEmpty()) newTab() else { active = index.coerceAtMost(tabs.lastIndex); showActive() }
    }

    private fun refreshTabs() {
        if (!::tabStrip.isInitialized) return
        tabStrip.removeAllViews()
        tabs.forEachIndexed { i, tab ->
            tabStrip.addView(btn((if (i == active) "● " else "") + tab.title.take(15) + "  ×") {
                if (i == active) closeTab(i) else switchTab(i)
            }, LinearLayout.LayoutParams(-2, 40).apply { setMargins(3, 0, 3, 0) })
        }
        if (::root.isInitialized && root.childCount > 0) {
            val header = root.getChildAt(0) as LinearLayout
            (header.getChildAt(2) as TextView).text = "▢ " + tabs.size
        }
    }

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isEmpty() || tabs.isEmpty()) return
        val target = when {
            value.startsWith("https://") || value.startsWith("http://") -> value
            value.contains(".") && !value.contains(" ") -> "https://" + value
            else -> {
                val base = when (engine) {
                    "Bing" -> "https://www.bing.com/search?q="
                    "DuckDuckGo" -> "https://duckduckgo.com/?q="
                    else -> "https://www.google.com/search?q="
                }
                base + Uri.encode(value)
            }
        }
        val tab = tabs[active]
        tab.url = target
        tab.title = Uri.parse(target).host ?: "Loading"
        content.removeAllViews()
        content.addView(tab.web, FrameLayout.LayoutParams(-1, -1))
        address.setText(target)
        tab.web.loadUrl(target)
        refreshTabs()
    }

    private fun home() {
        if (tabs.isEmpty()) newTab() else {
            tabs[active].web.stopLoading()
            tabs[active].url = "imboss://home"
            showActive()
        }
    }

    private fun back() {
        val web = tabs.getOrNull(active)?.web ?: return
        if (web.canGoBack()) web.goBack() else home()
    }

    private fun forward() { tabs.getOrNull(active)?.web?.let { if (it.canGoForward()) it.goForward() } }

    private fun tabsDialog() {
        val names = tabs.mapIndexed { i, tab -> (if (i == active) "● " else "") + tab.title + "  (tap to close)" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Open tabs: " + tabs.size)
            .setItems(names) { _, which -> closeTab(which) }
            .setPositiveButton("New tab") { _, _ -> newTab() }
            .setNegativeButton("Done", null).show()
    }

    private fun menuDialog() {
        val items = arrayOf("Bookmarks", "History", "Downloads", "Manage services",
            "Private browsing: " + if (privateMode) "ON" else "OFF", "Clear browsing data", "Settings")
        AlertDialog.Builder(this).setTitle("IMBOSS").setItems(items) { _, which ->
            when (which) {
                0 -> savedList("Bookmarks", "bookmarks")
                1 -> savedList("History", "history")
                2 -> startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                3 -> manageServices()
                4 -> {
                    privateMode = !privateMode
                    tabs.getOrNull(active)?.web?.let { it.clearHistory(); if (privateMode) it.clearCache(true) }
                    toast(if (privateMode) "Private session on. Websites may still track you." else "Private session off")
                    showActive()
                }
                5 -> clearData()
                6 -> settingsDialog()
            }
        }.show()
    }

    private fun settingsDialog() {
        val items = arrayOf("Search engine: " + engine, "Switch " + if (dark) "to light theme" else "to dark theme",
            "Accent colour", "Service shortcuts", "About IMBOSS")
        AlertDialog.Builder(this).setTitle("Settings").setItems(items) { _, which ->
            when (which) {
                0 -> AlertDialog.Builder(this).setTitle("Search engine")
                    .setSingleChoiceItems(arrayOf("Google", "Bing", "DuckDuckGo"), arrayOf("Google", "Bing", "DuckDuckGo").indexOf(engine)) { dialog, item ->
                        engine = arrayOf("Google", "Bing", "DuckDuckGo")[item]
                        prefs.edit().putString("engine", engine).apply()
                        dialog.dismiss(); settingsDialog()
                    }.setNegativeButton("Cancel", null).show()
                1 -> {
                    dark = !dark
                    prefs.edit().putBoolean("dark", dark).apply()
                    root.setBackgroundColor(bg())
                    showActive()
                }
                2 -> {
                    val colours = intArrayOf(0xFF86A8FF.toInt(), 0xFF78DCC6.toInt(), 0xFFFF9DB5.toInt(), 0xFFC2A4FF.toInt(), 0xFFFFC477.toInt())
                    AlertDialog.Builder(this).setTitle("Accent colour")
                        .setItems(arrayOf("Ice blue", "Mint", "Rose", "Lavender", "Amber")) { _, i ->
                            accent = colours[i]
                            prefs.edit().putInt("accent", accent).apply()
                            rebuildUi()
                        }.show()
                }
                3 -> manageServices()
                4 -> AlertDialog.Builder(this).setTitle("IMBOSS Browser")
                    .setMessage("Version 0.2.0\nUses Android System WebView (Chromium-based), not a separately bundled Chromium engine. Private mode prevents this app from saving its browsing history, but does not guarantee anonymity or block website tracking.")
                    .setPositiveButton("OK", null).show()
            }
        }.show()
    }

    private fun rebuildUi() {
        val oldTabs = tabs.toList()
        buildUi()
        showActive()
    }

    private fun manageServices() {
        val names = services.keys.toTypedArray()
        val checked = names.map { prefs.getBoolean("service_" + it, true) }.toBooleanArray()
        AlertDialog.Builder(this).setTitle("Show on homepage")
            .setMultiChoiceItems(names, checked) { _, i, enabled ->
                prefs.edit().putBoolean("service_" + names[i], enabled).apply()
            }.setPositiveButton("Done") { _, _ -> showActive() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun bookmark() {
        val url = tabs.getOrNull(active)?.url ?: return
        if (!url.startsWith("http")) { toast("Open a website first"); return }
        addList("bookmarks", url); toast("Bookmark saved")
    }

    private fun addHistory(url: String) = addList("history", url)

    private fun addList(key: String, value: String) {
        val old = JSONArray(prefs.getString(key, "[]") ?: "[]")
        val list = (0 until old.length()).map { old.optString(it) }.filter { it != value }.toMutableList()
        list.add(0, value)
        val result = JSONArray()
        list.take(100).forEach { result.put(it) }
        prefs.edit().putString(key, result.toString()).apply()
    }

    private fun savedList(title: String, key: String) {
        val data = JSONArray(prefs.getString(key, "[]") ?: "[]")
        val list = (0 until data.length()).map { data.optString(it) }
        if (list.isEmpty()) { toast("No " + title.lowercase() + " yet"); return }
        AlertDialog.Builder(this).setTitle(title).setItems(list.toTypedArray()) { _, i -> navigate(list[i]) }
            .setNeutralButton("Clear") { _, _ -> prefs.edit().putString(key, "[]").apply(); toast(title + " cleared") }
            .setNegativeButton("Close", null).show()
    }

    private fun clearData() {
        AlertDialog.Builder(this).setTitle("Clear browsing data?")
            .setMessage("Clears saved history, page cache and form data. Bookmarks remain.")
            .setPositiveButton("Clear") { _, _ ->
                prefs.edit().putString("history", "[]").apply()
                tabs.forEach { it.web.clearHistory(); it.web.clearCache(true); it.web.clearFormData() }
                toast("Browsing data cleared")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onBackPressed() {
        if (tabs.getOrNull(active)?.web?.canGoBack() == true) back() else home()
    }

    override fun onDestroy() {
        tabs.forEach { it.web.stopLoading(); it.web.destroy() }
        tabs.clear()
        super.onDestroy()
    }
}
