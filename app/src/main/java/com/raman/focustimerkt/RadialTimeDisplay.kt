package com.raman.focustimerkt

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator

class RadialTimeDisplay : View, ValueAnimator.AnimatorUpdateListener{
    //this delay needs to last some time
    private val defaultPathPaint: Paint = Paint()
    private val defaultPath: Path = Path()

    private val fillPath: Path = Path()
    private val fillPathPaint: Paint = Paint()
    private val fillPathGradient: LinearGradient = LinearGradient(
        768f, 768f, 0f, 0f, intArrayOf(
            Color.parseColor("#ee7752"),
            Color.parseColor("#e73c7e"),
            Color.parseColor("#23a6d5"),
            Color.parseColor("#23d5ab")
        ),
        null, Shader.TileMode.MIRROR
    )

    private val valueAnimator: ValueAnimator = ValueAnimator.ofFloat(0f,360f)

    private var strokeWidth = 0f
    private var arcWidth = 0f
    private var arcHeight = 0f
    private var leftX = 0f
    private var topX = 0f

    private var deltaAngleSweep = 360f
    private var startAngle = 0f


    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        //can use 2 arcs
        //1 is the full arc is a filled arc
        //1 is the unfilled arc
        //radius needs to change if width of arc exceeds bounding box
        //so change bounding box of arc sweep
        arcWidth = w - strokeWidth
        arcHeight = h - strokeWidth

        leftX = 0f + strokeWidth
        topX = 0f + strokeWidth

        defaultPath.addArc(leftX, topX, arcWidth, arcHeight, 0f, 360f)
        fillPath.addArc(leftX,topX,arcWidth,arcHeight,0f,360f)
        //angle and the rectangles along the radius
    }

    private fun init(context: Context) {
        setLayerType(LAYER_TYPE_HARDWARE, null)

        strokeWidth = resources.getInteger(R.integer.radialArcStrokeWidth).toFloat()
        fillPathPaint.style = Paint.Style.STROKE
        fillPathPaint.strokeWidth = strokeWidth
        fillPathPaint.strokeCap = Paint.Cap.ROUND
        fillPathPaint.isAntiAlias = true
        //fillPathPaint.color = resources.getColor(R.color.pastel_light_aqua,context.theme)
        //fillPathPaint.shader = fillPathGradient

        defaultPathPaint.style = Paint.Style.STROKE
        defaultPathPaint.strokeWidth = strokeWidth
        defaultPathPaint.isAntiAlias = true
        //defaultPathPaint.color = resources.getColor(R.color.slate_black,context.theme)

        val fillTypedValue: TypedValue= TypedValue()
        val unFillTypedValue: TypedValue = TypedValue()

        context.theme.resolveAttribute(R.attr.timerRadialFillColor,fillTypedValue,true)
        fillPathPaint.color  = fillTypedValue.data

        context.theme.resolveAttribute(R.attr.timerRadialUnFillColor,unFillTypedValue,true)
        defaultPathPaint.color =  unFillTypedValue.data

        valueAnimator.interpolator = LinearInterpolator()
        valueAnimator.duration = 3500
        valueAnimator.addUpdateListener(this)
        if(!isInEditMode) {
            valueAnimator.addListener((context as MainActivity).resetListener)
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawPaths(canvas)
    }

    private fun drawPaths(canvas: Canvas) {
//        draw small arcs
//        then we have to change the number of arcs
//        or change the nth paint
        //drawAxis(canvas);
        canvas.drawPath(defaultPath, defaultPathPaint)
        fillPath.reset()
        fillPath.addArc(leftX, topX, arcWidth, arcHeight, startAngle, deltaAngleSweep)
        canvas.drawPath(fillPath, fillPathPaint)
        //need to grab a point along this line
    }

    //need to animate forward and backward
    //forward needs to be able to resume from any point from 0f to 360f
    //backward needs to be able to resume from any point 360f to 0f
    //need to be able to pause or play animation

    fun startTickDownAnimation(timeRemaining: Long, percentageComplete: Float){
        if(valueAnimator.isRunning) return
        //ticking down
        //arc is drawn from 0f to some angle positive or negative
        //so to tick down we need the arc to draw from 0f to 360f as 360f approaches 0f
        //also animate the sweep delta needs to get smaller
        valueAnimator.setFloatValues(percentageComplete*360f,360f)
        //valueAnimator.setFloatValues(-(fullRadialSweepAngle - percentageComplete*fullRadialSweepAngle),startAngle)
        valueAnimator.duration = timeRemaining
        valueAnimator.start()
    }

    fun startTickUpAnimation(timeRemaining: Long, percentageComplete: Float){
        valueAnimator.setFloatValues(360f*(1 - percentageComplete),0f)
        //valueAnimator.setFloatValues(-percentageComplete*fullRadialSweepAngle,-fullRadialSweepAngle)
        valueAnimator.duration = timeRemaining
        valueAnimator.start()
    }

    fun pauseAnimation(){
        if(valueAnimator.isStarted && valueAnimator.isRunning) {
            valueAnimator.pause()
            Log.i("RadialTimeDisplay","startAngle => $startAngle")
            Log.i("RadialTimeDisplay","deltaAngleSweep => $deltaAngleSweep")
        }
    }

    fun resumeAnimation(timeRemaining: Long, percentageComplete: Float, tickUpFlag: Boolean){
        if(valueAnimator.isPaused) {
            valueAnimator.resume()
        }else{
            //start the animation
            if(tickUpFlag){
                startTickUpAnimation(timeRemaining,percentageComplete)
            }else{
                startTickDownAnimation(timeRemaining,percentageComplete)
            }
        }
    }

    override fun onAnimationUpdate(animation: ValueAnimator?) {
        startAngle = animation!!.animatedValue as Float
        deltaAngleSweep = 360f - startAngle//animation!!.animatedValue as Float
        invalidate()
    }

    fun setArcTickUpCompletion(percentageComplete: Float) {
        //tick up angle
        //Log.i("RadialTimeDisplay","startAngle => $startAngle")
        if(!valueAnimator.isStarted){
            startAngle = 360f*(1f - percentageComplete)
            deltaAngleSweep = 360f - startAngle
            invalidate()        }

    }

    fun setArcTickDownCompletion(percentageComplete: Float) {
        //Log.i("RadialTimeDisplay","startAngle => $startAngle")
        //animation animates the start angle
        //and the deltaAngleSweep is always the difference between 360f - and start angle
        //so on a tick down
        //pause and play should set these
        if(!valueAnimator.isStarted){
            startAngle = percentageComplete * 360f
            deltaAngleSweep = 360f - startAngle
            invalidate()
        }
    }
}