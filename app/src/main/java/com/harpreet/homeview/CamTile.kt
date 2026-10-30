package com.harpreet.homeview

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** One camera square: live RTSP video + name + LIVE/offline label. */
class CamTile(context: Context) : FrameLayout(context) {

    private val playerView = PlayerView(context)
    private val nameView = TextView(context)
    private val statusView = TextView(context)
    private val handler = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null
    private var url: String? = null
    private var waitingSince = 0L

    init {
        setBackgroundColor(BG)
        playerView.setUseController(false)
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
        playerView.setShutterBackgroundColor(BG)
        playerView.setKeepContentOnPlayerReset(true)
        addView(playerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (tv in listOf(nameView, statusView)) {
            tv.setTextColor(Color.parseColor("#F1EFE8"))
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            tv.setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        val m = dp(12)
        addView(nameView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START).apply { setMargins(m, 0, m, m) })
        addView(statusView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END).apply { setMargins(m, 0, m, m) })
    }

    // The tile handles all taps itself (tap = full screen, long-press = settings).
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    fun setName(name: String) { nameView.text = name }

    fun setFullScreen(full: Boolean) {
        playerView.setResizeMode(
            if (full) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        )
    }

    /** Start (or keep) playing this link. null = camera not set up. */
    fun play(newUrl: String?) {
        if (newUrl == url && player != null) return
        stop()
        url = newUrl
        if (newUrl == null) {
            setStatus("Long-press to set up", "#B4B2A9")
            return
        }
        start()
    }

    fun stop() {
        handler.removeCallbacks(retry)
        releasePlayer()
        url = null
    }

    /** Called every few seconds: restart a stream that is stuck connecting. */
    fun watchdog() {
        if (url != null && waitingSince != 0L && SystemClock.elapsedRealtime() - waitingSince > 20_000) {
            restart()
        }
    }

    private fun start() {
        val u = url ?: return
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 2000, 250, 250)
            .build()
        val p = ExoPlayer.Builder(context).setLoadControl(loadControl).build()
        p.volume = 0f
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        p.addListener(listener)
        val source = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .setTimeoutMs(10_000)
            .createMediaSource(MediaItem.fromUri(u))
        p.setMediaSource(source)
        p.playWhenReady = true
        p.prepare()
        playerView.player = p
        player = p
        waitingSince = SystemClock.elapsedRealtime()
        setStatus("Connecting…", "#D3D1C7")
    }

    private fun restart() {
        handler.removeCallbacks(retry)
        releasePlayer()
        start()
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        waitingSince = 0L
    }

    private val retry = Runnable { if (url != null) restart() }

    private fun retrySoon() {
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, 5_000)
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    waitingSince = 0L
                    setLive()
                }
                Player.STATE_BUFFERING -> {
                    if (waitingSince == 0L) waitingSince = SystemClock.elapsedRealtime()
                    setStatus("Connecting…", "#D3D1C7")
                }
                Player.STATE_ENDED -> {
                    setStatus("Offline · retrying", "#F09595")
                    retrySoon()
                }
                else -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            setStatus("Offline · retrying", "#F09595")
            retrySoon()
        }
    }

    private fun setStatus(text: String, color: String) {
        statusView.text = text
        statusView.setTextColor(Color.parseColor(color))
    }

    private fun setLive() {
        val s = SpannableString("●  LIVE")
        s.setSpan(ForegroundColorSpan(Color.parseColor("#E24B4A")), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        statusView.setTextColor(Color.parseColor("#F1EFE8"))
        statusView.text = s
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.parseColor("#111111")
    }
}
