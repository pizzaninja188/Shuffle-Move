package shuffle.android.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shuffle.android.Capture
import shuffle.android.R
import shuffle.android.ShuffleRepository
import shuffle.android.repo
import shuffle.android.ui.MainActivity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Foreground service that owns the screen capture and the two overlay windows:
 * - a draggable bubble: tap = capture, recognize, solve and show the move; long-press = open the app
 *   on the last capture (to fix cells, teach icons or calibrate the board position);
 * - a full-screen, touch-through arrow layer that shows the suggested move.
 */
class OverlayService : LifecycleService() {

    private lateinit var wm: WindowManager
    private var capturer: ScreenCapturer? = null
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var arrow: MoveArrowView? = null
    private var work: Job? = null
    private var hideJob: Job? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (capturer != null) return START_NOT_STICKY
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Android 14+: must be in the foreground with the mediaProjection type before using the token.
        startInForeground()
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(resultCode, data)
        if (projection == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val (w, h) = screenSize()
        capturer = ScreenCapturer(projection, w, h, resources.displayMetrics.densityDpi)
        addOverlays()
        _running.value = true
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.overlay_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_running))
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    /** Full physical screen size, which is what MediaProjection captures. */
    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            Pair(b.width(), b.height())
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            Pair(m.widthPixels, m.heightPixels)
        }
    }

    // ---- overlay windows ------------------------------------------------------------------------

    private fun overlayParams(w: Int, h: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun addOverlays() {
        val arrowView = MoveArrowView(this).apply { visibility = View.GONE }
        val arrowParams = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, touchable = false,
        ).apply {
            // Android 12+ only lets touches pass through other apps' overlays at <= 0.8 opacity.
            alpha = 0.8f
        }
        wm.addView(arrowView, arrowParams)
        arrow = arrowView

        val d = resources.displayMetrics.density
        val size = (56 * d).roundToInt()
        val b = TextView(this).apply {
            text = "▶"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(59, 91, 169))
                setStroke((2 * d).roundToInt(), Color.WHITE)
            }
            elevation = 6 * d
        }
        val params = overlayParams(size, size, touchable = true).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (8 * d).roundToInt()
            y = resources.displayMetrics.heightPixels / 3
        }
        b.setOnTouchListener(BubbleTouch(params))
        wm.addView(b, params)
        bubble = b
        bubbleParams = params
    }

    /** Drag to move; tap to solve; long-press to review in the app. */
    private inner class BubbleTouch(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@OverlayService).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private val longPress = Runnable {
            longPressed = true
            onBubbleLongPress()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false; longPressed = false
                    main.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        main.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        wm.updateViewLayout(v, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(longPress)
                    if (!dragging && !longPressed) onBubbleTap()
                }
                MotionEvent.ACTION_CANCEL -> main.removeCallbacks(longPress)
            }
            return true
        }
    }

    private fun setBusy(busy: Boolean) {
        bubble?.text = if (busy) "…" else "▶"
    }

    private fun onBubbleTap() {
        if (work?.isActive == true) return
        // A second tap while a hint is showing just dismisses it.
        if (arrow?.visibility == View.VISIBLE) {
            hideHint()
            return
        }
        work = lifecycleScope.launch {
            setBusy(true)
            try {
                val capture = captureAndRead(solve = true) ?: return@launch
                showHint(capture)
            } catch (e: Exception) {
                Log.e(ShuffleRepository.TAG, "Solve failed", e)
                bubble?.text = "!"
                delay(1500)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun onBubbleLongPress() {
        if (work?.isActive == true) return
        work = lifecycleScope.launch {
            setBusy(true)
            try {
                captureAndRead(solve = false) ?: return@launch
                startActivity(
                    Intent(this@OverlayService, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_SCREEN, MainActivity.SCREEN_REVIEW)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            } catch (e: Exception) {
                Log.e(ShuffleRepository.TAG, "Capture failed", e)
            } finally {
                setBusy(false)
            }
        }
    }

    /** Hides the overlays, grabs a clean frame, then recognizes (and optionally solves) the board. */
    private suspend fun captureAndRead(solve: Boolean): Capture? {
        val cap = capturer ?: return null
        hideHint()
        bubble?.visibility = View.INVISIBLE
        val frame = try {
            delay(32)
            cap.captureFresh()
        } finally {
            bubble?.visibility = View.VISIBLE
        }
        if (frame == null) {
            Log.w(ShuffleRepository.TAG, "No frame captured")
            return null
        }
        val r = repo
        val capture = withContext(r.engineDispatcher) {
            val rect = r.boardRect(cap.width, cap.height)
            val reading = r.recognizer().read(frame, rect)
            val outcome = if (solve) r.solve(r.boardFrom(reading)) else null
            Capture(frame, rect, reading, outcome)
        }
        r.publishCapture(capture)
        return capture
    }

    private fun showHint(capture: Capture) {
        val outcome = capture.outcome
        val best = outcome?.best
        val unsure = capture.reading.cells.filter { it.label == null }.map { it.row to it.col }
        val text = buildString {
            when {
                best == null -> append("No move found")
                best.move.size < 4 -> append("Board still moving – tap again")
                else -> append("≈${best.netScore.average.roundToInt()} pts")
            }
            if (unsure.isNotEmpty()) append(" · ${unsure.size} unsure")
            if (outcome?.timedOut == true) append(" · quick")
        }
        arrow?.hint = MoveArrowView.Hint(capture.rect, best?.move ?: emptyList(), text, unsure)
        arrow?.visibility = View.VISIBLE
        hideJob?.cancel()
        hideJob = lifecycleScope.launch {
            delay(HINT_MILLIS)
            hideHint()
        }
    }

    private fun hideHint() {
        hideJob?.cancel()
        arrow?.visibility = View.GONE
    }

    override fun onDestroy() {
        _running.value = false
        main.removeCallbacksAndMessages(null)
        bubble?.let { runCatching { wm.removeView(it) } }
        arrow?.let { runCatching { wm.removeView(it) } }
        bubble = null
        arrow = null
        capturer?.release()
        capturer = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "shuffle.android.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val HINT_MILLIS = 8000L

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, OverlayService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
        }
    }
}
