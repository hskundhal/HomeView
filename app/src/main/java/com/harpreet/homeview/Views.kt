package com.harpreet.homeview

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.hypot

/** Lays out 4 camera tiles in a 2x2 grid, or one tile full screen. */
class CameraGrid(context: Context) : ViewGroup(context) {

    private val gap = (2 * resources.displayMetrics.density).toInt()

    var fullIndex = -1
        set(value) {
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        val h = View.MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = (w - gap) / 2
        val ch = (h - gap) / 2
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            if (fullIndex >= 0) {
                c.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            } else {
                c.measure(View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(ch, View.MeasureSpec.EXACTLY))
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        val cw = (w - gap) / 2
        val ch = (h - gap) / 2
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            if (fullIndex >= 0) {
                c.layout(0, 0, w, h)
            } else {
                val x = if (i % 2 == 0) 0 else cw + gap
                val y = if (i < 2) 0 else ch + gap
                c.layout(x, y, x + cw, y + ch)
            }
        }
    }
}

/** Holds the weather WebView. Drag to move (snaps to a corner), tap passes through, long-press = settings. */
class DragFrame(context: Context) : FrameLayout(context) {

    var onTap: ((Float, Float) -> Unit)? = null
    var onHold: (() -> Unit)? = null
    var onDrop: ((Float, Float) -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startTx = 0f
    private var startTy = 0f
    private var dragging = false

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (!dragging) onTap?.invoke(e.rawX, e.rawY)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (!dragging) onHold?.invoke()
        }
    })

    // Never let the WebView itself receive touches.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                downX = ev.rawX
                downY = ev.rawY
                startTx = translationX
                startTy = translationY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (!dragging && hypot(dx, dy) > slop) dragging = true
                if (dragging) {
                    translationX = startTx + dx
                    translationY = startTy + dy
                }
            }
        }
        detector.onTouchEvent(ev)
        if ((ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) && dragging) {
            onDrop?.invoke(translationX + width / 2f, translationY + height / 2f)
            dragging = false
        }
        return true
    }
}
