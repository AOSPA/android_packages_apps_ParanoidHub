package co.aospa.hub.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.graphics.withRotation

import co.aospa.hub.R


class UpdateProgressView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val Float.dp: Float
        get() = (this * Resources.getSystem().displayMetrics.density)

    private var progress = 0F
    private var remainderRotation = 0F

    private val animator = ValueAnimator.ofFloat(0F, 0F).apply {
        interpolator = DecelerateInterpolator()
        duration = 192
        addUpdateListener { animation ->
            this@UpdateProgressView.progress = animation.animatedValue as Float
            invalidate()
        }
    }

    private val paint = Paint().apply {
        color = resources.getColor(R.color.theme_accent)
        style = Paint.Style.STROKE
        strokeWidth = 4F.dp
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val underPaint = Paint().apply {
        color = resources.getColor(R.color.theme_accent)
        alpha = (255 * 0.54F).toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4F.dp
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(
                0.001F,
                12F.dp
//            ((2F * PI * width / 2F) / 30).toFloat()
        ), 0F)
    }


    init {
        val animator = ValueAnimator.ofFloat(0F, 360F)
        animator.interpolator = LinearInterpolator()
        animator.duration = 50000
        animator.addUpdateListener { animation ->
            remainderRotation = animation.animatedValue as Float
            invalidate()
        }
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.RESTART
        animator.start()
    }

    fun setProgress(progress: Float) {
        animator.apply {
            cancel()
            setFloatValues(this@UpdateProgressView.progress, progress)
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val rectF = RectF(0 + 4F.dp / 2, 0 + 4F.dp / 2, width - 4F.dp / 2, height - 4F.dp / 2)

        val angle = 360 * progress
        canvas.withRotation(-90F + remainderRotation, width / 2F, height / 2F) {
            drawOval(rectF, underPaint)
        }
        canvas.drawArc(rectF, -90F, angle, false, paint)
    }
}