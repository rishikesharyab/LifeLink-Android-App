package com.rishikesh.lifelink

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Spotlight tour: dims the screen, cuts a rounded hole around each step's
 * target view, and shows a tooltip card with Next / Skip.
 *
 * Usage:
 *   TourGuide(activity)
 *       .addStep("Title", "Body") { someView }
 *       .start { /* called on finish or skip */ }
 */
class TourGuide(private val activity: Activity) {

    private class Step(
        val title: String,
        val body: String,
        val target: () -> View?,
        val onEnter: (() -> Unit)?,
        val settleMs: Long
    )

    private val steps = mutableListOf<Step>()
    private var index = 0
    private var overlay: Overlay? = null
    private var onDone: (() -> Unit)? = null

    val isShowing: Boolean get() = overlay != null

    /**
     * @param onEnter  runs before the step is measured (expand sheets, scroll, etc.)
     * @param settleMs wait after onEnter so animations finish before measuring the target
     * @param target   view to spotlight; the step is skipped if it's null or not visible
     */
    fun addStep(
        title: String,
        body: String,
        onEnter: (() -> Unit)? = null,
        settleMs: Long = 150L,
        target: () -> View?
    ): TourGuide {
        steps.add(Step(title, body, target, onEnter, settleMs))
        return this
    }

    fun start(onDone: () -> Unit) {
        if (steps.isEmpty() || overlay != null) return
        this.onDone = onDone
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val o = Overlay(activity)
        overlay = o
        root.addView(
            o,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        goTo(0)
    }

    /** Ends the tour and fires the onDone callback (finish, skip, or back press). */
    fun dismiss() {
        val o = overlay ?: return
        overlay = null
        (o.parent as? ViewGroup)?.removeView(o)
        onDone?.invoke()
        onDone = null
    }

    /** Removes the overlay silently (no callback) — call from onDestroy. */
    fun release() {
        val o = overlay ?: return
        overlay = null
        (o.parent as? ViewGroup)?.removeView(o)
        onDone = null
    }

    private fun goTo(i: Int) {
        val o = overlay ?: return
        if (i >= steps.size) {
            dismiss()
            return
        }
        index = i
        val s = steps[i]
        o.hideTip()
        s.onEnter?.invoke()
        o.postDelayed({
            if (overlay !== o) return@postDelayed
            val t = s.target()
            if (t == null || !t.isShown || t.width == 0 || t.height == 0) {
                goTo(i + 1)
            } else {
                o.showStep(i, steps.size, s.title, s.body, t)
            }
        }, s.settleMs)
    }

    // ── Overlay view ──────────────────────────────────────────────────────────

    private inner class Overlay(ctx: Context) : FrameLayout(ctx) {

        private val d = ctx.resources.displayMetrics.density
        private val dimColor = Color.argb(160, 20, 12, 8)
        private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        private val hole = RectF()
        private var hasHole = false
        private var anim: ValueAnimator? = null

        private val tip = LinearLayout(ctx)
        private val tvTitle = TextView(ctx)
        private val tvBody = TextView(ctx)
        private val dots = LinearLayout(ctx)
        private val btnSkip = TextView(ctx)
        private val btnNext = TextView(ctx)

        init {
            setWillNotDraw(false)
            setLayerType(LAYER_TYPE_HARDWARE, null)
            isClickable = true // swallow taps outside the tooltip
            elevation = 100f * d

            tip.orientation = LinearLayout.VERTICAL
            tip.alpha = 0f
            tip.setPadding(px(16), px(14), px(16), px(12))
            tip.background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 14 * d
            }

            tvTitle.apply {
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFF2C2C2A.toInt())
            }
            tvBody.apply {
                textSize = 13f
                setLineSpacing(3 * d, 1f)
                setTextColor(0xFF5F5E5A.toInt())
                setPadding(0, px(4), 0, px(12))
            }

            dots.orientation = LinearLayout.HORIZONTAL
            dots.gravity = Gravity.CENTER_VERTICAL

            btnSkip.apply {
                text = "Skip"
                textSize = 13f
                setTextColor(0xFF5F5E5A.toInt())
                setPadding(px(10), px(8), px(10), px(8))
                setOnClickListener { dismiss() }
            }
            btnNext.apply {
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_pill_filled)
                setPadding(px(18), px(8), px(18), px(8))
                setOnClickListener { goTo(index + 1) }
            }

            val footer = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(dots, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(btnSkip)
                addView(btnNext)
            }

            tip.addView(tvTitle)
            tip.addView(tvBody)
            tip.addView(footer)
            addView(tip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        private fun px(v: Int) = (v * d).toInt()

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(dimColor)
            if (hasHole) canvas.drawRoundRect(hole, 14 * d, 14 * d, clearPaint)
        }

        fun hideTip() {
            tip.animate().cancel()
            tip.animate().alpha(0f).setDuration(100).start()
        }

        fun showStep(i: Int, total: Int, title: String, body: String, target: View) {
            val t = IntArray(2)
            val me = IntArray(2)
            target.getLocationOnScreen(t)
            getLocationOnScreen(me)

            val pad = 6 * d
            val r = RectF(
                t[0] - me[0] - pad,
                t[1] - me[1] - pad,
                t[0] - me[0] + target.width + pad,
                t[1] - me[1] + target.height + pad
            )
            moveHole(r)

            tvTitle.text = title
            tvBody.text = body
            btnNext.text = if (i == total - 1) "Got it" else "Next"
            btnSkip.visibility = if (i == total - 1) View.GONE else View.VISIBLE

            dots.removeAllViews()
            for (k in 0 until total) {
                val active = k == i
                val dot = View(context).apply {
                    background = GradientDrawable().apply {
                        setColor(if (active) 0xFF8B3A1F.toInt() else 0xFFE4D8C8.toInt())
                        cornerRadius = 3 * d
                    }
                }
                dots.addView(
                    dot,
                    LinearLayout.LayoutParams(px(if (active) 16 else 6), px(6)).apply {
                        marginEnd = px(4)
                    }
                )
            }

            val w = minOf(width - px(32), px(300))
            tip.layoutParams = LayoutParams(w, LayoutParams.WRAP_CONTENT)
            tip.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            val th = tip.measuredHeight

            val gap = px(12)
            val below = height - r.bottom
            val above = r.top
            var y = when {
                below >= th + gap + px(8) -> r.bottom + gap
                above >= th + gap + px(8) -> r.top - th - gap
                else -> r.bottom + gap
            }
            y = y.coerceIn(px(8).toFloat(), (height - th - px(8)).toFloat())

            tip.translationX = ((width - w) / 2).toFloat()
            tip.translationY = y
            tip.animate().cancel()
            tip.animate().alpha(1f).setDuration(160).start()
        }

        private fun moveHole(to: RectF) {
            anim?.cancel()
            if (!hasHole) {
                hole.set(to)
                hasHole = true
                invalidate()
                return
            }
            val from = RectF(hole)
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 240
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val f = it.animatedFraction
                    hole.set(
                        from.left + (to.left - from.left) * f,
                        from.top + (to.top - from.top) * f,
                        from.right + (to.right - from.right) * f,
                        from.bottom + (to.bottom - from.bottom) * f
                    )
                    invalidate()
                }
                start()
            }
        }
    }
}