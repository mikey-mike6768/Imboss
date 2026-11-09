package com.imboss.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Rational
import android.app.PictureInPictureParams
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
    private data class Tab(val web: WebView, var url: String = "imboss://home", var title: String = "New tab", var playing: Boolean = false, var muted: Boolean = false)
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
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var mediaTabIndex = -1
    companion object {
        @Volatile var activeInstance: MainActivity? = null
    }
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
        activeInstance = this
        dark = prefs.getBoolean("dark", true)
        accent = prefs.getInt("accent", accent)
        engine = prefs.getString("engine", "Google") ?: "Google"
        buildUi()
        newTab()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

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
        minHeight = dp(44)
        setPadding(dp(12), dp(3), dp(12), dp(3))
        background = GradientDrawable().apply {
            setColor(if (dark) 0xFF1A2638.toInt() else 0xFFFFFFFF.toInt())
            cornerRadius = 14f
            setStroke(1, if (dark) 0xFF2B3A50.toInt() else 0xFFD9E1EC.toInt())
        }
        isClickable = true
        setOnClickListener { action() }
    }

    private fun navBtn(value: String, action: () -> Unit) = TextView(this).apply {
        text = value; textSize = 22f; setTextColor(if (dark) 0xFFE8EDF7.toInt() else 0xFF26364D.toInt())
        gravity = Gravity.CENTER; includeFontPadding = false
        minHeight = dp(48)
        background = GradientDrawable().apply {
            setColor(if (dark) 0xFF182335.toInt() else 0xFFFFFFFF.toInt())
            cornerRadius = 13f
            setStroke(1, if (dark) 0xFF2B3A50.toInt() else 0xFFD9E1EC.toInt())
        }
        isClickable = true
        setOnClickListener { action() }
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(bg())
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(5), 0, dp(2), dp(5)) }
        header.addView(txt("IMBOSS", 22f, true).apply {
            letterSpacing = 0.16f; setTextColor(accent)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(btn("⚙") { settingsDialog() }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(4), 0, dp(4), 0) })
        header.addView(btn("▢ " + tabs.size) { tabsDialog() }, LinearLayout.LayoutParams(-2, dp(48)))
        root.addView(header)

        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = glass(32f); setPadding(dp(7), dp(2), dp(5), dp(2))
        }
        address = EditText(this).apply {
            hint = "Search or enter website"; textSize = 15f
            setTextColor(fg()); setHintTextColor(muted()); setSingleLine(true)
            setBackgroundColor(Color.TRANSPARENT); imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(10), dp(9), dp(5), dp(9)); setSelectAllOnFocus(true)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                    navigate(text.toString()); true
                } else false
            }
        }
        bar.addView(navBtn("‹") { back() }, LinearLayout.LayoutParams(dp(38), dp(44)).apply { setMargins(dp(1), 0, dp(1), 0) })
        bar.addView(navBtn("›") { forward() }, LinearLayout.LayoutParams(dp(38), dp(44)).apply { setMargins(dp(1), 0, dp(1), 0) })
        bar.addView(navBtn("⌂") { home() }, LinearLayout.LayoutParams(dp(38), dp(44)).apply { setMargins(dp(1), 0, dp(3), 0) })
        bar.addView(address, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(btn("☆") { bookmark() }, LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(dp(2), 0, dp(2), 0) })
        bar.addView(btn("Go") { navigate(address.text.toString()) }, LinearLayout.LayoutParams(dp(46), dp(44)))
        bar.addView(btn("▢") { tabsDialog() }, LinearLayout.LayoutParams(dp(42), dp(44)).apply { setMargins(dp(2), 0, dp(2), 0) })
        bar.addView(btn("⋯") { menuDialog() }, LinearLayout.LayoutParams(dp(42), dp(44)))
        root.addView(bar)

        val tabsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setPadding(0, dp(6), 0, dp(6)) }
        tabStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabsScroll.addView(tabStrip)
        root.addView(tabsScroll, LinearLayout.LayoutParams(-1, dp(48)))

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }
        listOf(
            "‹" to { back() }, "›" to { forward() }, "⌂" to { home() },
            "＋" to { newTab() }, "⋯" to { menuDialog() }
        ).forEach { pair ->
            bottom.addView(navBtn(pair.first, pair.second), LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(64)))
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
                root.setBackgroundColor(bg())
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
        // Remove Android WebView's explicit embedded-browser markers where possible.
        settings.userAgentString = settings.userAgentString
            .replace("; wv", "")
            .replace("Version/4.0 ", "")
        settings.mediaPlaybackRequiresUserGesture = false
        addJavascriptInterface(MediaBridge(), "ImbossMedia")
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.safeBrowsingEnabled = true
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                tabs.firstOrNull { it.web === view }?.let { it.title = title?.take(24) ?: it.title; refreshTabs() }
            }

            // HTML5 video players (YouTube, Netflix, etc.) request a custom fullscreen surface.
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (fullscreenView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                fullscreenCallback = callback
                previousOrientation = requestedOrientation
                if (prefs.getBoolean("media_auto_landscape", true)) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                root.getChildAt(0).visibility = View.GONE
                root.getChildAt(1).visibility = View.GONE
                root.getChildAt(2).visibility = View.GONE
                root.getChildAt(4).visibility = View.GONE
                content.removeAllViews()
                content.addView(view, FrameLayout.LayoutParams(-1, -1))
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }

            override fun onHideCustomView() {
                exitFullscreen()
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
                installMediaHooks(view)
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

    inner class MediaBridge {
        @JavascriptInterface fun onPlaybackChanged(playing: Boolean, title: String?, url: String?) {
            runOnUiThread {
                val mediaUrl = url ?: ""
                val found = tabs.indexOfFirst { it.url == mediaUrl }
                if (found >= 0) {
                    mediaTabIndex = found
                    tabs[found].playing = playing
                    refreshTabs()
                }
                val intent = Intent(this@MainActivity, MediaPlaybackService::class.java).apply {
                    action = if (playing) MediaPlaybackService.ACTION_PLAYING else MediaPlaybackService.ACTION_PAUSED
                    putExtra(MediaPlaybackService.EXTRA_TITLE, title?.take(120) ?: "IMBOSS media")
                    putExtra(MediaPlaybackService.EXTRA_URL, mediaUrl)
                    putExtra(MediaPlaybackService.EXTRA_TAB, mediaTabIndex.coerceAtLeast(0))
                }
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            }
        }
    }

    private fun installMediaHooks(web: WebView?) {
        val script = """
            (function() {
              if (window.__imbossMediaHooked) return;
              window.__imbossMediaHooked = true;
              function report(el, playing) {
                var t = (document.title || el.getAttribute('aria-label') || 'Media').slice(0,120);
                try { window.ImbossMedia.onPlaybackChanged(playing, t, location.href); } catch(e) {}
              }
              document.addEventListener('play', function(e) {
                if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) report(e.target, true);
              }, true);
              document.addEventListener('pause', function(e) {
                if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) report(e.target, false);
              }, true);
              document.addEventListener('ended', function(e) {
                if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) report(e.target, false);
              }, true);
            })();
        """.trimIndent()
        web?.evaluateJavascript(script, null)
    }

    internal fun controlWebPlayback(play: Boolean, index: Int = mediaTabIndex) {
        runOnUiThread {
            val target = tabs.getOrNull(index) ?: return@runOnUiThread
            val js = if (play)
                "document.querySelectorAll('video,audio').forEach(function(m){if(m.paused)m.play().catch(function(){});})"
            else
                "document.querySelectorAll('video,audio').forEach(function(m){if(!m.paused)m.pause();})"
            target.web.evaluateJavascript(js, null)
            target.playing = play
            refreshTabs()
        }
    }

    private fun stopAllMedia() {
        tabs.forEach { tab ->
            tab.web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();})", null)
            tab.playing = false
        }
        mediaTabIndex = -1
        stopService(Intent(this, MediaPlaybackService::class.java).setAction(MediaPlaybackService.ACTION_STOP))
        refreshTabs()
    }

    private fun mediaCenterDialog() {
        val labels = arrayOf("Background audio (where site permits)", "Automatic Picture-in-Picture",
            "Media notifications", "Lock-screen controls", "Bluetooth headset controls",
            "Immersive fullscreen", "Automatic landscape orientation",
            "Restore after temporary audio interruption")
        val keys = arrayOf("media_background", "media_auto_pip", "media_notifications", "media_lock_controls",
            "media_bluetooth", "media_immersive", "media_auto_landscape", "media_resume_focus")
        val checked = keys.mapIndexed { i, key -> prefs.getBoolean(key, i != 1 && i != 7) }.toBooleanArray()
        val dialog = AlertDialog.Builder(this).setTitle("IMBOSS Media Center")
            .setMultiChoiceItems(labels, checked) { _, which, enabled -> prefs.edit().putBoolean(keys[which], enabled).apply() }
            .setPositiveButton("Done", null)
            .setNeutralButton("Active media tabs") { _, _ -> activeMediaDialog() }
            .setNegativeButton("Stop all media", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { stopAllMedia(); dialog.dismiss() }
        }
        dialog.show()
    }

    private fun activeMediaDialog() {
        val activeTabs = tabs.mapIndexedNotNull { i, tab -> if (tab.playing) i to tab else null }
        if (activeTabs.isEmpty()) { toast("No active media tabs detected"); return }
        val labels = activeTabs.map { (_, tab) -> (if (tab.muted) "Muted • " else "Playing • ") + tab.title }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Active media")
            .setItems(labels) { _, which ->
                val (index, tab) = activeTabs[which]
                val actions = arrayOf(if (tab.muted) "Unmute tab" else "Mute tab", "Stop this tab", "Switch to tab")
                AlertDialog.Builder(this).setTitle(tab.title).setItems(actions) { _, action ->
                    when (action) {
                        0 -> {
                            tab.muted = !tab.muted
                            tab.web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(m=>m.muted=\u0024{tab.muted})", null)
                            activeMediaDialog()
                        }
                        1 -> { tab.web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(m=>m.pause())", null); tab.playing = false; refreshTabs() }
                        2 -> switchTab(index)
                    }
                }.show()
            }.setPositiveButton("Stop all") { _, _ -> stopAllMedia() }
            .setNegativeButton("Close", null).show()
    }

    private fun newTab(url: String? = null) {
        val web = createWebView()
        val tab = Tab(web, url ?: "imboss://home")
        tabs.add(tab); active = tabs.lastIndex
        showActive()
        if (url != null) web.loadUrl(url)
    }

    private fun exitFullscreen() {
        val view = fullscreenView ?: return
        fullscreenView = null
        content.removeView(view)
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        requestedOrientation = previousOrientation
        window.decorView.systemUiVisibility = if (dark) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        showActive()
    }

    private fun showActive() {
        content.removeAllViews()
        val tab = tabs.getOrNull(active) ?: return
        val isHome = tab.url == "imboss://home"
        // Home keeps the full dashboard controls; webpages use a compact Chrome-like toolbar.
        root.getChildAt(0).visibility = if (isHome) View.VISIBLE else View.GONE
        root.getChildAt(2).visibility = if (isHome) View.VISIBLE else View.GONE
        root.getChildAt(4).visibility = if (isHome) View.VISIBLE else View.GONE
        val bar = root.getChildAt(1) as LinearLayout
        for (i in 0..2) bar.getChildAt(i).visibility = if (isHome) View.GONE else View.VISIBLE
        if (isHome) {
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
            tabStrip.addView(btn((if (tab.playing) "♫ " else if (i == active) "● " else "") + tab.title.take(15) + "  ×") {
                if (i == active) closeTab(i) else switchTab(i)
            }, LinearLayout.LayoutParams(-2, dp(40)).apply { setMargins(dp(3), 0, dp(3), 0) })
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
        showActive()
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
            "Private browsing: " + if (privateMode) "ON" else "OFF", "Clear browsing data", "Media Center", "Settings")
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
                6 -> mediaCenterDialog()
                7 -> settingsDialog()
            }
        }.show()
    }

    private fun settingsDialog() {
        val items = arrayOf("Search engine: " + engine, "Switch " + if (dark) "to light theme" else "to dark theme",
            "Accent colour", "Service shortcuts", "Media Center", "About IMBOSS")
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
                4 -> mediaCenterDialog()
                5 -> AlertDialog.Builder(this).setTitle("IMBOSS Browser")
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (fullscreenView != null && prefs.getBoolean("media_auto_pip", false) &&
            android.os.Build.VERSION.SDK_INT >= 26 && !isInPictureInPictureMode) {
            try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
            catch (_: Exception) { }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            for (i in 0 until root.childCount) root.getChildAt(i).visibility = View.GONE
        } else if (fullscreenView == null) showActive()
    }

    override fun onBackPressed() {
        if (fullscreenView != null) {
            exitFullscreen()
        } else if (tabs.getOrNull(active)?.web?.canGoBack() == true) {
            back()
        } else {
            home()
        }
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        tabs.forEach { it.web.stopLoading(); it.web.destroy() }
        tabs.clear()
        super.onDestroy()
    }
}
