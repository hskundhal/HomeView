package com.harpreet.homeview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var grid: CameraGrid
    private lateinit var overlay: DragFrame
    private lateinit var web: WebView
    private val tiles = ArrayList<CamTile>()
    private val cams = ArrayList<Prefs.Cam>()

    private val main = Handler(Looper.getMainLooper())
    private lateinit var io: ScheduledExecutorService

    private var pageReady = false
    private var weatherJson: String? = null
    private var eventsJson: String = "[]"
    private var corner = 0 // 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        grid = CameraGrid(this)
        for (i in 0 until 4) {
            val t = CamTile(this)
            t.setOnClickListener { onTileTap(i) }
            t.setOnLongClickListener { openSettings(); true }
            tiles.add(t)
            grid.addView(t)
        }
        root.addView(grid, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        web = WebView(this)
        web.setBackgroundColor(Color.TRANSPARENT)
        web.settings.javaScriptEnabled = true
        web.settings.textZoom = 100
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                pushWeather()
                pushEvents()
            }
        }
        web.loadUrl("file:///android_asset/overlay.html")

        overlay = DragFrame(this)
        overlay.addView(web, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlay.onTap = { x, y -> onOverlayTap(x, y) }
        overlay.onHold = { openSettings() }
        overlay.onDrop = { cx, cy -> onOverlayDrop(cx, cy) }
        root.addView(overlay, FrameLayout.LayoutParams(1, 1))

        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or2, ob ->
            if (r - l != or2 - ol || b - t != ob - ot) root.post { sizeOverlay() }
        }

        setContentView(root)
        corner = Prefs.sp(this).getInt("corner", 0)

        io = Executors.newSingleThreadScheduledExecutor()
        io.scheduleWithFixedDelay({ loadWeather() }, 10, 10, TimeUnit.MINUTES)
        io.scheduleWithFixedDelay({ loadCalendar() }, 15, 15, TimeUnit.MINUTES)
        main.postDelayed(watchdog, 10_000)

        if (!Prefs.sp(this).getBoolean("configured", false)) openSettings()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        cams.clear()
        for (i in 0 until 4) {
            val c = Prefs.cam(this, i)
            cams.add(c)
            tiles[i].setName(c.name)
        }
        showGrid()
        io.execute { loadWeather(); loadCalendar() }
    }

    override fun onPause() {
        super.onPause()
        for (t in tiles) t.stop()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        io.shutdownNow()
        for (t in tiles) t.stop()
        web.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back leaves full screen; otherwise stay on the dashboard.
        if (grid.fullIndex >= 0) showGrid()
    }

    // ---------- cameras ----------

    private fun onTileTap(i: Int) {
        if (grid.fullIndex >= 0) showGrid() else showFull(i)
    }

    private fun showGrid() {
        grid.fullIndex = -1
        for (i in 0 until 4) {
            val t = tiles[i]
            t.visibility = View.VISIBLE
            t.setFullScreen(false)
            t.play(cams.getOrNull(i)?.url(main = false))
        }
    }

    private fun showFull(i: Int) {
        for (k in 0 until 4) {
            val t = tiles[k]
            if (k == i) {
                t.visibility = View.VISIBLE
                t.setFullScreen(true)
                t.play(cams.getOrNull(k)?.url(main = true))
            } else {
                t.stop()
                t.visibility = View.GONE
            }
        }
        grid.fullIndex = i
    }

    private val watchdog = object : Runnable {
        override fun run() {
            for (t in tiles) t.watchdog()
            main.postDelayed(this, 10_000)
        }
    }

    // ---------- weather box ----------

    private fun sizeOverlay() {
        val w = root.width
        val h = root.height
        if (w == 0 || h == 0) return
        overlay.layoutParams = FrameLayout.LayoutParams(w / 2, h / 2)
        placeOverlay(false)
    }

    private fun placeOverlay(animate: Boolean) {
        val tx = if (corner and 1 == 1) (root.width - root.width / 2).toFloat() else 0f
        val ty = if (corner and 2 == 2) (root.height - root.height / 2).toFloat() else 0f
        if (animate) {
            overlay.animate().translationX(tx).translationY(ty).setDuration(200).start()
        } else {
            overlay.translationX = tx
            overlay.translationY = ty
        }
    }

    private fun onOverlayDrop(cx: Float, cy: Float) {
        corner = (if (cx > root.width / 2f) 1 else 0) + (if (cy > root.height / 2f) 2 else 0)
        Prefs.sp(this).edit().putInt("corner", corner).apply()
        placeOverlay(true)
    }

    /** A tap on the weather box acts like a tap on the camera underneath it. */
    private fun onOverlayTap(rawX: Float, rawY: Float) {
        if (grid.fullIndex >= 0) {
            showGrid()
            return
        }
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        val x = rawX - loc[0]
        val y = rawY - loc[1]
        val i = (if (x > root.width / 2f) 1 else 0) + (if (y > root.height / 2f) 2 else 0)
        showFull(i)
    }

    // ---------- data ----------

    private fun loadWeather() {
        try {
            val json = Weather.fetch(Prefs.lat(this), Prefs.lon(this))
            main.post {
                weatherJson = json
                pushWeather()
            }
        } catch (e: Throwable) {
            // keep showing the last good weather
        }
    }

    private fun loadCalendar() {
        try {
            val urls = Prefs.calendarUrls(this)
            val arr = if (urls.isEmpty()) null else CalendarFeed.fetch(urls)
            val payload = JSONObject()
                .put("enabled", urls.isNotEmpty())
                .put("events", arr ?: org.json.JSONArray())
                .toString()
            main.post {
                eventsJson = payload
                pushEvents()
            }
        } catch (e: Throwable) {
            // keep the last list
        }
    }

    private fun pushWeather() {
        val w = weatherJson ?: return
        if (pageReady) web.evaluateJavascript("setWeather($w);", null)
    }

    private fun pushEvents() {
        if (pageReady) web.evaluateJavascript("setEvents($eventsJson);", null)
    }

    // ---------- misc ----------

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }
}
