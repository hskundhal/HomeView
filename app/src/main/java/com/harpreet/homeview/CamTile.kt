package com.harpreet.homeview

import android.content.Context
import android.graphics.Color
import android.net.Uri
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
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** One camera square: live RTSP video (H.265/H.264 supported via LibVLC) + name + LIVE/offline label. */
class CamTile(context: Context) : FrameLayout(context) {

    private val videoLayout = VLCVideoLayout(context)
    private val nameView = TextView(context)
    private val statusView = TextView(context)
    private val handler = Handler(Looper.getMainLooper())

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var candidateUrls = emptyList<String>()
    private var currentUrlIndex = 0
    private var waitingSince = 0L
    private var isPlayingReady = false

    init {
        setBackgroundColor(BG)
        addView(videoLayout, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

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

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    fun setName(name: String) { nameView.text = name }

    fun setFullScreen(full: Boolean) {}

    fun play(urls: List<String>) {
        android.util.Log.d("CamTile", "play() urls=$urls")
        if (urls == candidateUrls && mediaPlayer?.isPlaying == true) return
        stop()
        candidateUrls = urls
        currentUrlIndex = 0
        if (urls.isEmpty()) {
            setStatus("Long-press to set up", "#B4B2A9")
            return
        }
        startCurrent()
    }

    fun stop() {
        handler.removeCallbacks(retry)
        releasePlayer()
        candidateUrls = emptyList()
        currentUrlIndex = 0
        isPlayingReady = false
    }

    fun watchdog() {
        if (candidateUrls.isNotEmpty() && !isPlayingReady && waitingSince != 0L && SystemClock.elapsedRealtime() - waitingSince > 15_000) {
            nextUrlOrRetry()
        }
    }

    private fun startCurrent() {
        if (candidateUrls.isEmpty()) return
        val u = candidateUrls[currentUrlIndex]
        android.util.Log.d("CamTile", "startCurrent() trying url[$currentUrlIndex/${candidateUrls.size}]: $u")

        val options = ArrayList<String>().apply {
            add("--rtsp-tcp")
            add("--avcodec-hw=any")
            add("--no-audio")
            add("--network-caching=500")
            add("--rtsp-caching=500")
        }

        try {
            val vlc = LibVLC(context, options)
            libVlc = vlc
            val mp = MediaPlayer(vlc)
            mediaPlayer = mp

            mp.setEventListener(object : MediaPlayer.EventListener {
                override fun onEvent(event: MediaPlayer.Event) {
                    when (event.type) {
                        MediaPlayer.Event.Playing -> {
                            handler.post {
                                isPlayingReady = true
                                waitingSince = 0L
                                setLive()
                            }
                        }
                        MediaPlayer.Event.EncounteredError, MediaPlayer.Event.EndReached -> {
                            handler.post {
                                android.util.Log.e("CamTile", "LibVLC error/end event: ${event.type}")
                                setStatus("Offline · retrying", "#F09595")
                                nextUrlOrRetry()
                            }
                        }
                        else -> {}
                    }
                }
            })

            mp.attachViews(videoLayout, null, false, false)
            val media = Media(vlc, Uri.parse(u))
            media.setHWDecoderEnabled(true, false)
            mp.media = media
            media.release()
            mp.play()

            waitingSince = SystemClock.elapsedRealtime()
            isPlayingReady = false
            setStatus("Connecting…", "#D3D1C7")
        } catch (e: Exception) {
            android.util.Log.e("CamTile", "Exception starting LibVLC", e)
            nextUrlOrRetry()
        }
    }

    private fun nextUrlOrRetry() {
        if (candidateUrls.isEmpty()) return
        currentUrlIndex = (currentUrlIndex + 1) % candidateUrls.size
        android.util.Log.d("CamTile", "Switching to next candidate URL index $currentUrlIndex: ${candidateUrls[currentUrlIndex]}")
        handler.removeCallbacks(retry)
        releasePlayer()
        startCurrent()
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.detachViews()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null

        try {
            libVlc?.release()
        } catch (e: Exception) {}
        libVlc = null

        waitingSince = 0L
    }

    private val retry = Runnable { if (candidateUrls.isNotEmpty()) nextUrlOrRetry() }

    private fun retrySoon() {
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, 5_000)
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
