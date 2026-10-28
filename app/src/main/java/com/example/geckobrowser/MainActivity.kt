package com.example.geckobrowser

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

class MainActivity : AppCompatActivity() {

    companion object {
        // El runtime debe crearse una sola vez por proceso
        private var runtime: GeckoRuntime? = null
    }

    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var modeBtn: Button

    private val homeUrl = "https://duckduckgo.com"
    private var desktop = true
    private var canGoBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        geckoView = findViewById(R.id.geckoView)
        urlBar = findViewById(R.id.urlBar)
        progress = findViewById(R.id.progress)
        modeBtn = findViewById(R.id.modeBtn)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        desktop = prefs.getBoolean("desktop", true)

        val rt = runtime ?: GeckoRuntime.create(applicationContext).also { runtime = it }

        session = GeckoSession(
            GeckoSessionSettings.Builder()
                .userAgentMode(modeUa())
                .viewportMode(modeViewport())
                .build()
        )

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (url != null && !urlBar.hasFocus()) urlBar.setText(url)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@MainActivity.canGoBack = canGoBack
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                progress.visibility = View.VISIBLE
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                progress.visibility = View.INVISIBLE
            }
            override fun onProgressChange(session: GeckoSession, p: Int) {
                progress.progress = p
            }
        }

        session.open(rt)
        geckoView.setSession(session)

        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                load(urlBar.text.toString())
                urlBar.clearFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(urlBar.windowToken, 0)
                true
            } else false
        }

        findViewById<Button>(R.id.backBtn).setOnClickListener { session.goBack() }
        findViewById<Button>(R.id.forwardBtn).setOnClickListener { session.goForward() }
        findViewById<Button>(R.id.reloadBtn).setOnClickListener { session.reload() }
        findViewById<Button>(R.id.homeBtn).setOnClickListener { load(homeUrl) }
        modeBtn.setOnClickListener { showModeMenu() }
        updateModeLabel()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack) session.goBack() else finish()
            }
        })

        load(intent?.data?.toString() ?: homeUrl)
    }

    private fun modeUa() =
        if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE

    private fun modeViewport() =
        if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE

    private fun updateModeLabel() {
        modeBtn.text = if (desktop) "Escritorio" else "Móvil"
    }

    private fun showModeMenu() {
        val menu = PopupMenu(this, modeBtn)
        menu.menu.add(0, 0, 0, "Escritorio")
        menu.menu.add(0, 1, 1, "Móvil")
        menu.setOnMenuItemClickListener { item ->
            desktop = item.itemId == 0
            getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("desktop", desktop).apply()
            session.settings.userAgentMode = modeUa()
            session.settings.viewportMode = modeViewport()
            updateModeLabel()
            session.reload()
            true
        }
        menu.show()
    }

    private fun load(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val url = when {
            text.startsWith("http://") || text.startsWith("https://") -> text
            !text.contains(" ") && text.contains(".") -> "https://$text"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(text)
        }
        session.loadUri(url)
    }

    override fun onDestroy() {
        geckoView.releaseSession()
        session.close()
        super.onDestroy()
    }
}
