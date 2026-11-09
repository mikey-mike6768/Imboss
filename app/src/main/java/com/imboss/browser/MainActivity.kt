package com.imboss.browser

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var shell: LinearLayout
    private lateinit var address: EditText
    private lateinit var web: WebView
    private var dark = true
    private var privateMode = false
    private val shortcuts = linkedMapOf(
        "Spotify" to "https://open.spotify.com",
        "Apple Music" to "https://music.apple.com",
        "Netflix" to "https://www.netflix.com",
        "Prime Video" to "https://www.primevideo.com",
        "Telegram" to "https://web.telegram.org",
        "WhatsApp" to "https://web.whatsapp.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildBrowser()
        open("https://www.google.com")
    }

    private fun colorBackground() = if (dark) Color.rgb(12, 17, 31) else Color.rgb(232, 239, 249)
    private fun colorText() = if (dark) Color.WHITE else Color.rgb(20, 29, 45)
    private fun glass(): GradientDrawable = GradientDrawable().apply {
        setColor(if (dark) 0xCC27344B.toInt() else 0xDDFEFFFF.toInt())
        cornerRadius = 34f
        setStroke(1, if (dark) 0x66FFFFFF else 0x668394AA)
    }

    private fun textButton(value: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(colorText())
            gravity = Gravity.CENTER
            setPadding(15, 12, 15, 12)
            background = glass()
            isClickable = true
            setOnClickListener { action() }
        }

    private fun buildBrowser() {
        shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 18, 12, 10)
            setBackgroundColor(colorBackground())
        }
        val brand = TextView(this).apply {
            text = "IMBOSS"
            textSize = 23f
            letterSpacing = 0.16f
            setTextColor(colorText())
            setPadding(8, 8, 8, 12)
        }
        shell.addView(brand)
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = glass()
            setPadding(8, 4, 8, 4)
        }
        address = EditText(this).apply {
            hint = "Search or enter website"
            setSingleLine(true)
            textSize = 15f
            setTextColor(colorText())
            setHintTextColor(0xFF9AA9BE.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(12, 8, 8, 8)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO) { open(address.text.toString()); true } else false
            }
        }
        bar.addView(address, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(textButton("Go") { open(address.text.toString()) })
        shell.addView(bar)
        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }
        listOf(
            "‹" to { if (web.canGoBack()) web.goBack() },
            "›" to { if (web.canGoForward()) web.goForward() },
            "⌂" to { open("https://www.google.com") },
            "＋" to { open("about:blank") },
            "⋯" to { showMenu() }
        ).forEach { (label, click) ->
            actions.addView(textButton(label, click), LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3, 0, 3, 0) })
        }
        shell.addView(actions)
        web = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        shell.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(shell)
    }

    private fun open(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val url = when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.contains(".") && !value.contains(" ") -> "https://$value"
            else -> "https://www.google.com/search?q=" + android.net.Uri.encode(value)
        }
        address.setText(url)
        web.loadUrl(url)
    }

    private fun showMenu() {
        val options = arrayOf("Home", "Bookmarks", "Service shortcuts", "Toggle light/dark theme", "Toggle private mode", "Reload")
        AlertDialog.Builder(this)
            .setTitle("IMBOSS")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> open("https://www.google.com")
                    1 -> AlertDialog.Builder(this).setTitle("Bookmarks")
                        .setItems(arrayOf("Google", "Wikipedia", "YouTube")) { _, item ->
                            open(arrayOf("https://google.com", "https://wikipedia.org", "https://youtube.com")[item])
                        }.show()
                    2 -> AlertDialog.Builder(this).setTitle("Your services")
                        .setItems(shortcuts.keys.toTypedArray()) { _, item -> open(shortcuts.values.elementAt(item)) }.show()
                    3 -> { dark = !dark; shell.setBackgroundColor(colorBackground()) }
                    4 -> {
                        privateMode = !privateMode
                        if (privateMode) {
                            web.clearHistory()
                            web.clearCache(true)
                            web.clearFormData()
                        }
                        AlertDialog.Builder(this).setMessage(if (privateMode) "Basic private mode enabled. This is not a hardened anonymity mode." else "Private mode disabled.").setPositiveButton("OK", null).show()
                    }
                    5 -> web.reload()
                }
            }.show()
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
