package com.darsh7893.eyebrowser

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

/**
 * Minimal full-screen browser for Fire TV / Android TV.
 *
 * Remote control:
 * - MENU key, or long-press SELECT (DPAD_CENTER) → options menu
 * - BACK → WebView history, then exit
 */
class MainActivity : AppCompatActivity(), RemoteKeyboardServer.Listener {

    companion object {
        private const val PREFS_NAME = "eyebrowser"
        private const val KEY_START_URL = "start_url"
        private const val KEY_HINT_SHOWN = "hint_shown"
        private const val DEFAULT_START_URL = "https://www.google.com"
        private const val QR_SIZE_PX = 512
        private const val HINT_FADE_DELAY_MS = 6000L
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var hintView: TextView
    private lateinit var errorOverlay: FrameLayout
    private lateinit var errorMessage: TextView
    private lateinit var errorRetry: Button

    private var keyboardServer: RemoteKeyboardServer? = null
    private var keyboardPort = -1

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------ lifecycle

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.eye_navy))
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            isFocusable = true
            isFocusableInTouchMode = true
            webViewClient = eyeWebViewClient
            webChromeClient = eyeWebChromeClient
        }
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.eye_accent)
            )
            progressBackgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.eye_outline)
            )
            visibility = View.GONE
            isFocusable = false
        }
        root.addView(
            progressBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 6.dp
            ).apply { gravity = Gravity.TOP }
        )

        hintView = TextView(this).apply {
            text = "Long-press SELECT or press MENU for options"
            setTextColor(ContextCompat.getColor(context, R.color.eye_text))
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.hint_bg)
            setPadding(24.dp, 12.dp, 24.dp, 12.dp)
            visibility = View.GONE
            isFocusable = false
        }
        root.addView(
            hintView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 56.dp // clear of TV overscan
            }
        )

        errorOverlay = buildErrorOverlay()
        root.addView(
            errorOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    errorOverlay.visibility == View.VISIBLE -> hideError()
                    webView.canGoBack() -> webView.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        webView.requestFocus()
        webView.loadUrl(prefs.getString(KEY_START_URL, DEFAULT_START_URL) ?: DEFAULT_START_URL)
        maybeShowFirstRunHint()
    }

    override fun onDestroy() {
        try {
            keyboardServer?.stop()
        } catch (e: Exception) {
            // Best effort — nothing to do on shutdown.
        }
        keyboardServer = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ chrome

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }

    private val eyeWebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            view.loadUrl(request.url.toString())
            return true
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            view.loadUrl(url)
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            hideError()
            progressBar.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String) {
            progressBar.visibility = View.GONE
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                showError(error.description?.toString() ?: "Unknown error")
            }
        }

        @Suppress("DEPRECATION")
        override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String,
            failingUrl: String
        ) {
            showError(description)
        }
    }

    private val eyeWebChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
            progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
        }
    }

    // ------------------------------------------------------------------ keys

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                dismissHint()
                showMenu()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                // Track so a long-press reaches onKeyLongPress, but let the
                // short press fall through so links stay clickable.
                if (event.repeatCount == 0) event.startTracking()
                super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            dismissHint()
            showMenu()
            true
        } else {
            super.onKeyLongPress(keyCode, event)
        }
    }

    // ------------------------------------------------------------------ menu

    private fun showMenu() {
        val items = listOf(
            "Go to address…" to { showAddressDialog() },
            "Set start page…" to { showStartPageDialog() },
            "Phone keyboard (QR)…" to { showKeyboardDialog() },
            "Reload" to { webView.reload() },
            "Go forward" to {
                if (webView.canGoForward()) webView.goForward()
                else toast("Nothing to go forward to")
            }
        )

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
        }
        var dialog: AlertDialog? = null
        items.forEach { (label, action) ->
            val row = TextView(this).apply {
                text = label
                textSize = 20f
                setPadding(24.dp, 16.dp, 24.dp, 16.dp)
                setBackgroundResource(R.drawable.menu_item_bg)
                setTextColor(ContextCompat.getColorStateList(context, R.color.menu_item_text))
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    dialog?.dismiss()
                    action()
                }
            }
            list.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        dialog = AlertDialog.Builder(this, R.style.EyeDialog)
            .setTitle("Options")
            .setView(list)
            .create()
        dialog.show()
        (list.getChildAt(0) as TextView).requestFocus()
    }

    private fun showAddressDialog() {
        showUrlDialog(
            title = "Go to address",
            label = "Web address",
            initial = webView.url ?: "",
            confirmText = "Go"
        ) { input ->
            webView.loadUrl(normalizeUrl(input) ?: return@showUrlDialog)
        }
    }

    private fun showStartPageDialog() {
        showUrlDialog(
            title = "Start page",
            label = "Page to load when the app starts",
            initial = prefs.getString(KEY_START_URL, DEFAULT_START_URL) ?: DEFAULT_START_URL,
            confirmText = "Save"
        ) { input ->
            val url = normalizeUrl(input) ?: return@showUrlDialog
            prefs.edit().putString(KEY_START_URL, url).apply()
            webView.loadUrl(url)
            toast("Start page saved")
        }
    }

    /** Shared address/start-page dialog with styled input + buttons. */
    private fun showUrlDialog(
        title: String,
        label: String,
        initial: String,
        confirmText: String,
        onConfirm: (String) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 16.dp, 24.dp, 8.dp)
        }

        val labelView = TextView(this).apply {
            text = label
            setTextColor(ContextCompat.getColor(context, R.color.eye_text_dim))
            textSize = 14f
            setPadding(0, 0, 0, 8.dp)
        }
        val input = EditText(this).apply {
            setText(initial)
            setSelection(initial.length)
            setTextColor(ContextCompat.getColor(context, R.color.eye_text))
            setHintTextColor(ContextCompat.getColor(context, R.color.eye_text_dim))
            hint = "https://…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            textSize = 18f
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
            setBackgroundResource(R.drawable.edit_text_bg)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 20.dp, 0, 0)
        }
        var dialog: AlertDialog? = null
        val cancel = Button(this).apply {
            text = "Cancel"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.btn_bg)
            setPadding(28.dp, 10.dp, 28.dp, 10.dp)
            setOnClickListener { dialog?.dismiss() }
        }
        val ok = Button(this).apply {
            text = confirmText
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.btn_bg)
            setPadding(28.dp, 10.dp, 28.dp, 10.dp)
            setOnClickListener {
                val value = input.text.toString()
                if (value.isBlank()) {
                    toast("Enter an address first")
                    return@setOnClickListener
                }
                dialog?.dismiss()
                onConfirm(value)
            }
        }
        buttons.addView(cancel)
        buttons.addView(
            ok,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 12.dp }
        )

        container.addView(labelView)
        container.addView(input)
        container.addView(buttons)

        dialog = AlertDialog.Builder(this, R.style.EyeDialog)
            .setTitle(title)
            .setView(container)
            .create()
        dialog.show()
        input.requestFocus()
    }

    /** Adds https:// when the user omits the scheme; null when blank. */
    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.matches(Regex("^[a-zA-Z][a-zA-Z0-9+\\-.]*://.*"))) trimmed
        else "https://$trimmed"
    }

    // ------------------------------------------------------------------ phone keyboard

    private fun showKeyboardDialog() {
        if (!ensureKeyboardServer()) {
            toast("Couldn't start the keyboard server")
            return
        }
        val ip = wifiIpAddress()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28.dp, 20.dp, 28.dp, 20.dp)
        }

        if (ip == null) {
            container.addView(TextView(this).apply {
                text = "Connect this TV to Wi-Fi first, then try again."
                setTextColor(ContextCompat.getColor(context, R.color.eye_text))
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(0, 16.dp, 0, 16.dp)
            })
        } else {
            val url = "http://$ip:$keyboardPort"
            try {
                val qrCard = ImageView(this).apply {
                    setBackgroundResource(R.drawable.qr_card_bg)
                    setPadding(20.dp, 20.dp, 20.dp, 20.dp)
                    setImageBitmap(makeQrBitmap(url))
                    contentDescription = "QR code for $url"
                }
                val size = 280.dp
                container.addView(
                    qrCard,
                    LinearLayout.LayoutParams(size, size)
                )
            } catch (e: Exception) {
                // QR failed — the selectable URL below is still usable.
            }
            container.addView(TextView(this).apply {
                text = url
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                setTextColor(ContextCompat.getColor(context, R.color.eye_text))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 20.dp, 0, 8.dp)
            })
            container.addView(TextView(this).apply {
                text = "Scan with your phone — it must be on the same Wi-Fi."
                setTextColor(ContextCompat.getColor(context, R.color.eye_text_dim))
                textSize = 14f
                gravity = Gravity.CENTER
            })
        }

        AlertDialog.Builder(this, R.style.EyeDialog)
            .setTitle("Phone keyboard")
            .setView(container)
            .setPositiveButton("Close", null)
            .show()
    }

    /** Starts the local server, trying 8080..8085 in case a port is busy. */
    private fun ensureKeyboardServer(): Boolean {
        if (keyboardServer != null) return true
        for (port in 8080..8085) {
            try {
                val server = RemoteKeyboardServer(port, this)
                server.start()
                keyboardServer = server
                keyboardPort = port
                return true
            } catch (e: Exception) {
                // Port busy — try the next one.
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun wifiIpAddress(): String? {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        val raw = wifi.connectionInfo?.ipAddress ?: 0
        if (raw == 0) return null // not on Wi-Fi
        return "${raw and 0xff}.${raw shr 8 and 0xff}.${raw shr 16 and 0xff}.${raw shr 24 and 0xff}"
    }

    private fun makeQrBitmap(content: String): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX)
        val bmp = Bitmap.createBitmap(QR_SIZE_PX, QR_SIZE_PX, Bitmap.Config.RGB_565)
        for (x in 0 until QR_SIZE_PX) {
            for (y in 0 until QR_SIZE_PX) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    // Server callbacks arrive on NanoHTTPD worker threads — hop to the UI thread.
    override fun onRemoteText(text: String) {
        runOnUiThread { injectRemoteText(text) }
    }

    override fun onRemoteEnter() {
        runOnUiThread {
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    /**
     * Mirrors the phone's text into the page's focused field. JSONObject.quote
     * makes the string safe to embed in the JS snippet.
     */
    private fun injectRemoteText(text: String) {
        val js = "(function(t){" +
            "var el=document.activeElement;" +
            "if(!el)return;" +
            "var tag=(el.tagName||'').toUpperCase();" +
            "if(tag==='INPUT'||tag==='TEXTAREA'){" +
            "el.value=t;" +
            "el.dispatchEvent(new Event('input',{bubbles:true}));" +
            "el.dispatchEvent(new Event('change',{bubbles:true}));" +
            "}else if(el.isContentEditable){" +
            "el.textContent=t;" +
            "el.dispatchEvent(new Event('input',{bubbles:true}));" +
            "}})(" + JSONObject.quote(text) + ");"
        webView.evaluateJavascript(js, null)
    }

    // ------------------------------------------------------------------ error + hint

    private fun buildErrorOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E60D1B2A"))
            visibility = View.GONE
            isFocusable = false
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.dialog_bg)
            setPadding(36.dp, 32.dp, 36.dp, 32.dp)
        }
        val title = TextView(this).apply {
            text = "Couldn't load this page"
            setTextColor(ContextCompat.getColor(context, R.color.eye_text))
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        errorMessage = TextView(this).apply {
            setTextColor(ContextCompat.getColor(context, R.color.eye_text_dim))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 12.dp, 0, 24.dp)
        }
        errorRetry = Button(this).apply {
            text = "Retry"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.btn_bg)
            setPadding(32.dp, 12.dp, 32.dp, 12.dp)
            isFocusable = true
            setOnClickListener {
                hideError()
                webView.reload()
            }
        }
        card.addView(title)
        card.addView(errorMessage)
        card.addView(errorRetry)
        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                leftMargin = 48.dp
                rightMargin = 48.dp
            }
        )
        return overlay
    }

    private fun showError(description: String) {
        progressBar.visibility = View.GONE
        errorMessage.text = description
        errorOverlay.visibility = View.VISIBLE
        errorRetry.requestFocus()
    }

    private fun hideError() {
        errorOverlay.visibility = View.GONE
    }

    private fun maybeShowFirstRunHint() {
        if (prefs.getBoolean(KEY_HINT_SHOWN, false)) return
        prefs.edit().putBoolean(KEY_HINT_SHOWN, true).apply()
        hintView.visibility = View.VISIBLE
        hintView.alpha = 1f
        hintView.postDelayed({
            hintView.animate().alpha(0f).setDuration(800).withEndAction {
                hintView.visibility = View.GONE
            }.start()
        }, HINT_FADE_DELAY_MS)
    }

    private fun dismissHint() {
        hintView.animate().cancel()
        hintView.visibility = View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
