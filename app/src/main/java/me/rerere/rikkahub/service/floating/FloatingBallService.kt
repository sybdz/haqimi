package me.rerere.rikkahub.service.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.activity.FloatingChatActivity
import java.io.File
import java.io.FileOutputStream

class FloatingBallService : Service() {
    companion object {
        private const val ACTION_START = "me.rerere.rikkahub.action.FLOATING_BALL_START"
        private const val ACTION_STOP = "me.rerere.rikkahub.action.FLOATING_BALL_STOP"
        private const val ACTION_SHOW_BALL = "me.rerere.rikkahub.action.FLOATING_BALL_SHOW"

        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        private const val NOTIFICATION_CHANNEL_ID = "floating_ball"
        private const val NOTIFICATION_ID = 10010

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_STOP
            })
        }

        fun showBall(context: Context) {
            context.startService(Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_SHOW_BALL
            })
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var ballView: View? = null
    private var ballLayoutParams: WindowManager.LayoutParams? = null

    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showBallInternal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, ActivityResultCode.INVALID)
                val data = intent.getParcelableExtraCompat<Intent>(EXTRA_RESULT_DATA)
                if (resultCode != ActivityResultCode.INVALID && data != null) {
                    startMediaProjection(resultCode, data)
                }
            }

            ACTION_SHOW_BALL -> {
                showBallInternal()
            }

            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            runCatching { hideBallInternal() }
            runCatching { stopMediaProjection() }
        }
        serviceScope.coroutineContext.cancel()
    }

    override fun onBind(intent: Intent?) = null

    private fun startMediaProjection(resultCode: Int, data: Intent) {
        stopMediaProjection()
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = manager.getMediaProjection(resultCode, data)
    }

    private fun stopMediaProjection() {
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }

    private fun showBallInternal() {
        if (ballView != null) {
            ballView?.visibility = View.VISIBLE
            return
        }

        val sizePx = dpToPx(48)
        val view = createBallView(sizePx)
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dpToPx(160)
        }

        runCatching {
            windowManager.addView(view, params)
            ballView = view
            ballLayoutParams = params
        }.onFailure {
            it.printStackTrace()
            Toast.makeText(this, "无法显示悬浮窗，请检查权限", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun hideBallInternal() {
        val view = ballView ?: return
        runCatching { windowManager.removeView(view) }
        ballView = null
        ballLayoutParams = null
    }

    private fun createBallView(sizePx: Int): View {
        val container = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.floating_ball_bg)
            elevation = dpToPx(8).toFloat()
        }
        val label = TextView(this).apply {
            text = "AI"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
        }
        container.addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    onBallClick()
                    return true
                }
            }
        )

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        container.setOnTouchListener { _, event ->
            val params = ballLayoutParams ?: return@setOnTouchListener false
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { windowManager.updateViewLayout(container, params) }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> true

                else -> false
            }
        }

        return container
    }

    private fun onBallClick() {
        val projection = mediaProjection
        if (projection == null) {
            Toast.makeText(this, "未授权屏幕录制，请在设置里重新开启悬浮小球", Toast.LENGTH_SHORT).show()
            return
        }

        serviceScope.launch {
            ballView?.visibility = View.INVISIBLE
            delay(80)
            val screenshot = runCatching { captureScreenshotToFile(projection) }.getOrNull()
            if (screenshot == null) {
                ballView?.visibility = View.VISIBLE
                Toast.makeText(this@FloatingBallService, "截图失败", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val intent = Intent(this@FloatingBallService, FloatingChatActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(FloatingChatActivity.EXTRA_SCREENSHOT_URI, screenshot.toUri().toString())
            }
            startActivity(intent)
        }
    }

    private suspend fun captureScreenshotToFile(projection: MediaProjection): File = withContext(Dispatchers.IO) {
        val (width, height, densityDpi) = getDisplaySize()
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = projection.createVirtualDisplay(
            "rikkahub_floating_ball",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )

        try {
            val image = awaitImage(imageReader)
            val bitmap = try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val raw = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                raw.copyPixelsFromBuffer(buffer)
                Bitmap.createBitmap(raw, 0, 0, width, height).also { raw.recycle() }
            } finally {
                runCatching { image.close() }
            }

            val dir = File(cacheDir, "floating_ball")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            file
        } finally {
            runCatching { virtualDisplay.release() }
            runCatching { imageReader.close() }
        }
    }

    private suspend fun awaitImage(imageReader: ImageReader): android.media.Image =
        kotlinx.coroutines.withTimeout(1500) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                val listener = ImageReader.OnImageAvailableListener { reader ->
                    val image = runCatching { reader.acquireLatestImage() }.getOrNull()
                    if (image == null) return@OnImageAvailableListener
                    imageReader.setOnImageAvailableListener(null, null)
                    if (cont.isActive) cont.resume(image) else image.close()
                }
                imageReader.setOnImageAvailableListener(listener, handler)
                cont.invokeOnCancellation {
                    runCatching { imageReader.setOnImageAvailableListener(null, null) }
                }
            }
        }

    private fun getDisplaySize(): Triple<Int, Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = resources.displayMetrics
            val bounds = windowManager.currentWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), metrics.densityDpi)
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "悬浮小球",
            NotificationManager.IMPORTANCE_MIN
        )
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("悬浮小球运行中")
            .setContentText("点击小球可截图并提问")
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

private object ActivityResultCode {
    const val INVALID = Int.MIN_VALUE
}

private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }
}
