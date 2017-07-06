package ca.allanwang.kau.swipe

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import ca.allanwang.kau.logging.KL
import ca.allanwang.kau.utils.navigationBarColor
import ca.allanwang.kau.utils.statusBarColor
import ca.allanwang.kau.utils.withAlpha
import java.lang.ref.WeakReference

class SwipeBackLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), SwipeBackContract {

    override val swipeBackLayout: SwipeBackLayout
        get() = this
    /**
     * Threshold of scroll, we will close the activity, when scrollPercent over
     * this value;
     */
    override var scrollThreshold = DEFAULT_SCROLL_THRESHOLD
        set(value) {
            if (value >= 1.0f || value <= 0) throw IllegalArgumentException("Threshold value should be between 0 and 1.0")
            field = value
        }

    var activity: Activity? = null
        set(value) {
            field = value
            if (value != null) {
                statusBarAlpha = Color.alpha(value.statusBarColor)
                navBarAlpha = Color.alpha(value.navigationBarColor)
            }
        }

    override var swipeEnabled = true

    override var disallowIntercept = false

    private var contentView: View? = null

    private val dragHelper: ViewDragHelper

    private var scrollPercent: Float = 0f

    private var contentOffset: Int = 0

    /**
     * The set of listeners to be sent events through.
     */
    private var listeners: MutableList<WeakReference<SwipeListener>> = mutableListOf()

    private var scrimOpacity: Float = 0f

    override var scrimColor = DEFAULT_SCRIM_COLOR
        /**
         * Set a color to use for the scrim that obscures primary content while a
         * drawer is open.

         * @param color Color to use in 0xAARRGGBB format.
         */
        set(value) {
            field = value
            invalidate()
        }

    private var statusBarAlpha: Int = 255
    private var navBarAlpha: Int = 255

    val chromeFadeListener: SwipeListener by lazy {
        object : SwipeListener {
            override fun onScroll(percent: Float, px: Int, edgeFlag: Int) {
                activity?.apply {
                    if (edgeCurrentFlag != SWIPE_EDGE_TOP) statusBarColor = statusBarColor.withAlpha((statusBarAlpha * (1 - percent)).toInt())
                    if (edgeCurrentFlag != SWIPE_EDGE_BOTTOM) navigationBarColor = navigationBarColor.withAlpha((navBarAlpha * (1 - percent)).toInt())
                }
            }

            override fun onEdgeTouch() {}

            override fun onScrollToClose(edgeFlag: Int) {}

        }
    }


    private var inLayout: Boolean = false

    override var edgeSize: Int
        get() = dragHelper.edgeSize
        set(value) {
            dragHelper.edgeSize = value
        }

    override var edgeFlag = SWIPE_EDGE_LEFT
        /**
         * We will verify that only one axis is used at a time
         */
        set(value) {
            field = value
            dragHelper.setEdgeTrackingEnabled(value)
        }

    private val isHorizontal: Boolean
        get() = edgeFlag and SWIPE_EDGE_HORIZONTAL > 0

    private val isVertical: Boolean
        get() = edgeFlag and SWIPE_EDGE_VERTICAL > 0


    /**
     * Specifies the edge flag that should by considered at the current moment
     */
    private var edgeCurrentFlag: Int = 0

    private val isCurrentlyHorizontal: Boolean
        get() = edgeCurrentFlag and SWIPE_EDGE_HORIZONTAL > 0

    private val isCurrentlyVertical: Boolean
        get() = edgeCurrentFlag and SWIPE_EDGE_VERTICAL > 0

    init {
        dragHelper = ViewDragHelper.create(this, ViewDragCallback())
        val density = resources.displayMetrics.density
        val minVel = MIN_FLING_VELOCITY * density
        //allow touch from anywhere on the screen
        edgeSize = Math.max(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        dragHelper.minVelocity = minVel
        dragHelper.maxVelocity = 2 * minVel
        edgeFlag = edgeFlag
        addListener(chromeFadeListener)
    }


    /**
     * Set up contentView which will be moved by user gesture

     * @param view
     */
    private fun setContentView(view: View) {
        contentView = view
    }

    /**
     * Set the size of an edge. This is the range in pixels along the edges of
     * this view that will actively detect edge touches or drags if edge
     * tracking is enabled.

     * @param swipeEdge The size of an edge in pixels
     */
    //    override fun setEdgeSize(swipeEdge: Int) {
//        trackingEdge = swipeEdge
//    }


    override fun setEdgeSizePercent(swipeEdgePercent: Float) {
        edgeSize = (resources.displayMetrics.widthPixels * swipeEdgePercent).toInt()
    }

    /**
     * Add a callback to be invoked when a swipe event is sent to this view.

     * @param listener the swipe listener to attach to this view
     */
    override fun addListener(listener: SwipeListener) {
        listeners.add(WeakReference(listener))
    }

    /**
     * Removes a listener from the set of listeners

     * @param listener
     */
    override fun removeListener(listener: SwipeListener) {
        val iter = listeners.iterator()
        while (iter.hasNext()) {
            val l = iter.next().get()
            if (l == null || l == listener)
                iter.remove()
        }
    }

    /**
     * Scroll out contentView and finish the activity
     */
    override fun scrollToFinishActivity() {
        val childWidth = contentView!!.width
        val top = 0
        val left = childWidth + OVERSCROLL_DISTANCE
        dragHelper.smoothSlideViewTo(contentView, left, top)
        invalidate()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!swipeEnabled || disallowIntercept) return false
        val s = dragHelper.shouldInterceptTouchEvent(event)
        return s
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!swipeEnabled) return false
        dragHelper.processTouchEvent(event)
        return true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        inLayout = true
        val xOffset: Int
        val yOffset: Int
        if (isCurrentlyHorizontal) {
            xOffset = contentOffset
            yOffset = 0
        } else {
            xOffset = 0
            yOffset = contentOffset
        }
        contentView?.layout(xOffset, yOffset, xOffset + contentView!!.measuredWidth, yOffset + contentView!!.measuredHeight)
        inLayout = false
    }

    override fun requestLayout() {
        if (!inLayout) super.requestLayout()
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val drawContent = child === contentView
        val ret = super.drawChild(canvas, child, drawingTime)
        if (scrimOpacity > 0 && drawContent && dragHelper.viewDragState != ViewDragHelper.STATE_IDLE)
            drawScrim(canvas, child)
        return ret
    }

    private fun drawScrim(canvas: Canvas, child: View) {
        val baseAlpha = (scrimColor and 0xff000000.toInt()).ushr(24)
        val alpha = (baseAlpha * scrimOpacity).toInt()
        val color = alpha shl 24 or (scrimColor and 0xffffff)
        canvas.clipRect(0, 0, width, height)
        canvas.drawColor(color)
    }

    fun attachToActivity(activity: Activity) {
        if (parent != null) return
        this.activity = activity
        val a = activity.theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        val background = a.getResourceId(0, 0)
        a.recycle()

        val decor = activity.window.decorView as ViewGroup
        var decorChild = decor.findViewById<View>(android.R.id.content)
        while (decorChild.parent !== decor)
            decorChild = decorChild.parent as View
        decorChild.setBackgroundResource(background)
        decor.removeView(decorChild)
        addView(decorChild)
        setContentView(decorChild)
        decor.addView(this)
    }

    fun removeFromActivity(activity: Activity) {
        if (parent == null) return
        val decorChild = getChildAt(0) as ViewGroup
        val decor = activity.window.decorView as ViewGroup
        decor.removeView(this)
        removeView(decorChild)
        decor.addView(decorChild)
    }

    override fun computeScroll() {
        scrimOpacity = 1 - scrollPercent
        if (dragHelper.continueSettling(true)) ViewCompat.postInvalidateOnAnimation(this)
    }

    private inner class ViewDragCallback : ViewDragHelper.Callback() {
        private var isScrollOverValid: Boolean = false

        /**
         * Toggles whether we may intercept the touch event
         */
        override fun tryCaptureView(view: View, i: Int): Boolean {
            val ret = dragHelper.isEdgeTouched(edgeFlag, i)
            if (ret) {
                listeners.forEach { it.get()?.onEdgeTouch() }
                isScrollOverValid = true
            }
            return ret
        }

        /**
         * Needs to be bigger than 0 to specify that scrolling is possible horizontally
         */
        override fun getViewHorizontalDragRange(child: View): Int {
            return if (isHorizontal) 1 else 0
        }

        /**
         * Needs to be bigger than 0 to specify that scrolling is possible vertically
         */
        override fun getViewVerticalDragRange(child: View): Int {
            return if (isVertical) 1 else 0
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            //make sure that we are using the proper axis
            val horizontal = (isHorizontal && (!isVertical || dy == 0))
            edgeCurrentFlag =
                    if (horizontal)
                        if (left > 0) SWIPE_EDGE_LEFT else SWIPE_EDGE_RIGHT
                    else
                        if (top > 0) SWIPE_EDGE_BOTTOM else SWIPE_EDGE_TOP

            scrollPercent = Math.abs(
                    if (horizontal) left.toFloat() / contentView!!.width
                    else (top.toFloat() / contentView!!.height))
            contentOffset = if (horizontal) left else top
            KL.d("poz $horizontal $scrollPercent $contentOffset")
            invalidate()
            if (scrollPercent < scrollThreshold && !isScrollOverValid)
                isScrollOverValid = true

            listeners.forEach { it.get()?.onScroll(scrollPercent, contentOffset, edgeCurrentFlag) }

            if (scrollPercent >= 1) {
                if (!(activity?.isFinishing ?: true)) {
                    if (scrollPercent >= scrollThreshold && isScrollOverValid) {
                        isScrollOverValid = false
                        listeners.forEach { it.get()?.onScrollToClose(edgeCurrentFlag) }
                    }
                    activity?.finish()
                    activity?.overridePendingTransition(0, 0)
                }
            }
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            var result = Pair(0, 0)
            KL.d("Release $edgeCurrentFlag $xvel $yvel")
            if (scrollPercent <= scrollThreshold) {
                //threshold not met; check velocities
                if ((edgeCurrentFlag == SWIPE_EDGE_LEFT && xvel > MIN_FLING_VELOCITY)
                        || (edgeCurrentFlag == SWIPE_EDGE_RIGHT && xvel < -MIN_FLING_VELOCITY)
                        || (edgeCurrentFlag == SWIPE_EDGE_TOP && yvel < -MIN_FLING_VELOCITY)
                        || (edgeCurrentFlag == SWIPE_EDGE_BOTTOM && yvel > MIN_FLING_VELOCITY))
                    result = exitCaptureOffsets(edgeCurrentFlag, releasedChild)
            } else {
                //threshold met; fling to designated side
                result = exitCaptureOffsets(edgeCurrentFlag, releasedChild)
            }
            dragHelper.settleCapturedViewAt(result.first, result.second)
            invalidate()
            edgeCurrentFlag = 0
        }

        private fun exitCaptureOffsets(edgeFlag: Int, view: View): Pair<Int, Int> {
            var top: Int = 0
            var left: Int = 0
            when (edgeFlag) {
                SWIPE_EDGE_LEFT -> left = view.width + OVERSCROLL_DISTANCE
                SWIPE_EDGE_RIGHT -> left = -(view.width + OVERSCROLL_DISTANCE)
                SWIPE_EDGE_TOP -> top = -(view.height + OVERSCROLL_DISTANCE)
                SWIPE_EDGE_BOTTOM -> top = view.height + OVERSCROLL_DISTANCE
                else -> KL.e("Unknown edgeCurrentFlag $edgeCurrentFlag")
            }
            return Pair(left, top)
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            return if (!isHorizontal || isCurrentlyVertical) 0
            else if (edgeFlag == SWIPE_EDGE_RIGHT) Math.min(0, Math.max(left, -child.width))
            else if (edgeFlag == SWIPE_EDGE_LEFT) Math.min(child.width, Math.max(left, 0))
            else Math.max(left, -child.width)
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            return if (!isVertical || isCurrentlyHorizontal) 0
            else if (edgeFlag == SWIPE_EDGE_BOTTOM) Math.min(0, Math.max(top, -child.height))
            else if (edgeFlag == SWIPE_EDGE_TOP) Math.min(child.height, Math.max(top, 0))
            else Math.max(top, -child.height)
        }
    }

    companion object {
        /**
         * Minimum velocity that will be detected as a fling
         */
        const val MIN_FLING_VELOCITY = 400 // dips per second

        const val DEFAULT_SCRIM_COLOR = 0x99000000.toInt()

        /**
         * Default threshold of scroll
         */
        const val DEFAULT_SCROLL_THRESHOLD = 0.3f

        const val OVERSCROLL_DISTANCE = 10

    }
}
