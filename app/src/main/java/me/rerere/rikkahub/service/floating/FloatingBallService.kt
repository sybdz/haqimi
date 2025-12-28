package me.rerere.rikkahub.service.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.FloatingChatPanel
import me.rerere.rikkahub.ui.theme.RikkahubTheme

class FloatingBallService : Service() {
    companion object {
        private const val TAG = "FloatingBallService"
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
    private val displayManager by lazy { getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    private var ballView: View? = null
    private var ballLayoutParams: WindowManager.LayoutParams? = null
    private var dialogView: View? = null
    private var dialogLayoutParams: WindowManager.LayoutParams? = null
    private var dialogVisible = false

    private var screenWidth = 0
    private var screenHeight = 0
    private var ballSizePx = 0
    private var dialogGapPx = 0

    private val screenshotFiles = mutableSetOf<File>()

    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var mediaProjectionActive = false
    private val overlayViewTreeOwner = FloatingOverlayViewTreeOwner()
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId != android.view.Display.DEFAULT_DISPLAY) return
            updateDisplayBoundsAndClamp()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        initLayoutDefaults()
        showBallInternal()
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
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
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
        serviceScope.launch {
            runCatching { hideBallInternal() }
            runCatching { stopMediaProjection() }
            screenshotFiles.forEach { file -> runCatching { if (file.exists()) file.delete() } }
            screenshotFiles.clear()
        }
        overlayViewTreeOwner.onDestroy()
        serviceScope.coroutineContext.cancel()
    }

    override fun onBind(intent: Intent?) = null

    private fun startMediaProjection(resultCode: Int, data: Intent) {
        stopMediaProjection()
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, data)
        if (projection == null) {
            mediaProjectionActive = false
            Log.w(TAG, "media projection is null")
            return
        }
        mediaProjection = projection
        mediaProjectionActive = true
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "media projection stopped")
                mediaProjectionActive = false
                val current = mediaProjection
                val currentCallback = mediaProjectionCallback
                mediaProjection = null
                mediaProjectionCallback = null
                if (current != null && currentCallback != null) {
                    runCatching { current.unregisterCallback(currentCallback) }
                }
            }
        }
        mediaProjectionCallback = callback
        projection.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun stopMediaProjection() {
        mediaProjectionActive = false
        val projection = mediaProjection ?: return
        val callback = mediaProjectionCallback
        mediaProjection = null
        mediaProjectionCallback = null
        if (callback != null) {
            runCatching { projection.unregisterCallback(callback) }
        }
        runCatching { projection.stop() }
    }

    private fun initLayoutDefaults() {
        val (width, height, _) = getDisplaySize()
        screenWidth = width
        screenHeight = height
        ballSizePx = dpToPx(48)
        dialogGapPx = dpToPx(4)

        val minWidth = dpToPx(200)
        val minHeight = dpToPx(200)
        val maxWidth = (screenWidth - dpToPx(16)).coerceAtLeast(minWidth)
        val maxHeight = (screenHeight - ballSizePx - dialogGapPx - dpToPx(56)).coerceAtLeast(minHeight)

        val dialogWidth = dpToPx(280).coerceIn(minWidth, maxWidth)
        val dialogHeight = dpToPx(320).coerceIn(minHeight, maxHeight)
        val defaultX = ((screenWidth - dialogWidth) / 2).coerceAtLeast(0)
        val defaultY = (screenHeight - dialogHeight - ballSizePx - dialogGapPx - dpToPx(40)).coerceAtLeast(0)

        dialogLayoutParams = WindowManager.LayoutParams(
            dialogWidth,
            dialogHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = defaultX
            y = defaultY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        ballLayoutParams = WindowManager.LayoutParams(
            ballSizePx,
            ballSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = defaultX
            y = defaultY + dialogHeight + dialogGapPx
        }
    }

    private fun showBallInternal() {
        if (ballView != null) {
            ballView?.visibility = View.VISIBLE
            return
        }
        if (ballLayoutParams == null || dialogLayoutParams == null) {
            initLayoutDefaults()
        }

        val view = createBallView()
        val params = ballLayoutParams ?: return

        runCatching {
            windowManager.addView(view, params)
            ballView = view
        }.onFailure {
            it.printStackTrace()
            Toast.makeText(this, "无法显示悬浮窗，请检查权限", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun hideBallInternal() {
        dialogView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        dialogView = null
        dialogVisible = false

        ballView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        ballView = null
        ballLayoutParams = null
        dialogLayoutParams = null
    }

    private fun createBallView(): View {
        val container = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.floating_ball_bg)
            elevation = dpToPx(6).toFloat()
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var isDragging = false
        var hasDragged = false
        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (hasDragged) return true
                    toggleDialog()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    isDragging = true
                    hasDragged = true
                }
            }
        )

        var touchX = 0f
        var touchY = 0f
        var startBallX = 0
        var startBallY = 0

        container.setOnTouchListener { _, event ->
            val ballParams = ballLayoutParams ?: return@setOnTouchListener false
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchX = event.rawX
                    touchY = event.rawY
                    startBallX = ballParams.x
                    startBallY = ballParams.y
                    isDragging = false
                    hasDragged = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (!isDragging) {
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            isDragging = true
                        } else {
                            return@setOnTouchListener true
                        }
                    }
                    hasDragged = true
                    moveBallTo(startBallX + dx, startBallY + dy)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    hasDragged = false
                    true
                }

                else -> false
            }
        }

        return container
    }

    private fun toggleDialog() {
        if (dialogVisible) {
            hideDialog()
        } else {
            showDialog()
        }
    }

    private fun ensureDialogView() {
        if (dialogView != null) return
        val params = dialogLayoutParams ?: return
        val view = ComposeView(this).apply {
            visibility = View.GONE
            setViewTreeOwners(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides overlayViewTreeOwner) {
                    RikkahubTheme {
                        FloatingChatPanel(
                            modifier = Modifier.fillMaxSize(),
                            onClose = { hideDialog() },
                            onRequestScreenshot = { captureScreenshotForChat() },
                            onResize = { fromLeft, dx, dy -> resizeDialog(fromLeft, dx, dy) },
                            onMove = { dx, dy -> moveDialogBy(dx, dy) },
                        )
                    }
                }
            }
        }
        runCatching {
            windowManager.addView(view, params)
            dialogView = view
            overlayViewTreeOwner.onStart()
        }.onFailure {
            it.printStackTrace()
            Toast.makeText(this, "无法显示悬浮窗，请检查权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setViewTreeOwners(view: View) {
        view.setTag(androidx.lifecycle.runtime.R.id.view_tree_lifecycle_owner, overlayViewTreeOwner)
        view.setTag(androidx.savedstate.R.id.view_tree_saved_state_registry_owner, overlayViewTreeOwner)
        view.setTag(androidx.lifecycle.viewmodel.R.id.view_tree_view_model_store_owner, overlayViewTreeOwner)
    }

    private fun showDialog() {
        ensureDialogView()
        val view = dialogView ?: return
        val params = dialogLayoutParams ?: return
        dialogVisible = true
        refreshDisplaySize()
        ensureDialogInBounds()
        runCatching { windowManager.updateViewLayout(view, params) }
        view.animate().cancel()
        view.translationY = params.height.toFloat()
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(220)
            .start()
    }

    private fun hideDialog() {
        val view = dialogView ?: return
        val params = dialogLayoutParams
        dialogVisible = false
        view.animate().cancel()
        val offset = params?.height ?: view.height
        view.animate()
            .translationY(offset.toFloat())
            .alpha(0f)
            .setDuration(180)
            .withEndAction { view.visibility = View.GONE }
            .start()
    }

    private fun moveBallTo(x: Int, y: Int) {
        val ballParams = ballLayoutParams ?: return

        refreshDisplaySize()
        val maxX = (screenWidth - ballSizePx).coerceAtLeast(0)
        val maxY = (screenHeight - ballSizePx).coerceAtLeast(0)
        val newX = x.coerceIn(0, maxX)
        val newY = y.coerceIn(0, maxY)

        if (ballParams.x == newX && ballParams.y == newY) return

        ballParams.x = newX
        ballParams.y = newY
        updateBallLayout()
    }

    private fun resizeDialog(fromLeft: Boolean, dx: Float, dy: Float) {
        val dialogParams = dialogLayoutParams ?: return

        refreshDisplaySize()
        val minWidth = dpToPx(200)
        val minHeight = dpToPx(200)
        val maxWidth = (screenWidth - dpToPx(16)).coerceAtLeast(minWidth)
        val maxHeight = (screenHeight - dialogParams.y - dpToPx(16)).coerceAtLeast(minHeight)

        val deltaX = dx.toInt()
        val deltaY = dy.toInt()

        val targetWidth = if (fromLeft) dialogParams.width - deltaX else dialogParams.width + deltaX
        val targetHeight = dialogParams.height + deltaY
        val newWidth = targetWidth.coerceIn(minWidth, maxWidth)
        val newHeight = targetHeight.coerceIn(minHeight, maxHeight)

        val rightEdge = dialogParams.x + dialogParams.width
        val newX = if (fromLeft) {
            (rightEdge - newWidth).coerceIn(0, screenWidth - newWidth)
        } else {
            dialogParams.x.coerceIn(0, screenWidth - newWidth)
        }

        dialogParams.width = newWidth
        dialogParams.height = newHeight
        dialogParams.x = newX
        updateDialogLayout()
    }

    private fun moveDialogBy(dx: Float, dy: Float) {
        val dialogParams = dialogLayoutParams ?: return

        refreshDisplaySize()
        val maxX = (screenWidth - dialogParams.width).coerceAtLeast(0)
        val maxY = (screenHeight - dialogParams.height).coerceAtLeast(0)
        val newX = (dialogParams.x + dx.toInt()).coerceIn(0, maxX)
        val newY = (dialogParams.y + dy.toInt()).coerceIn(0, maxY)
        if (newX == dialogParams.x && newY == dialogParams.y) return
        dialogParams.x = newX
        dialogParams.y = newY
        updateDialogLayout()
    }

    private fun ensureDialogInBounds() {
        val dialogParams = dialogLayoutParams ?: return
        val maxX = (screenWidth - dialogParams.width).coerceAtLeast(0)
        val maxY = (screenHeight - dialogParams.height).coerceAtLeast(0)
        val newX = dialogParams.x.coerceIn(0, maxX)
        val newY = dialogParams.y.coerceIn(0, maxY)
        if (newX == dialogParams.x && newY == dialogParams.y) return
        dialogParams.x = newX
        dialogParams.y = newY
        updateDialogLayout()
    }

    private fun clampBallInBounds() {
        val ballParams = ballLayoutParams ?: return
        val maxX = (screenWidth - ballSizePx).coerceAtLeast(0)
        val maxY = (screenHeight - ballSizePx).coerceAtLeast(0)
        val newX = ballParams.x.coerceIn(0, maxX)
        val newY = ballParams.y.coerceIn(0, maxY)
        if (newX == ballParams.x && newY == ballParams.y) return
        ballParams.x = newX
        ballParams.y = newY
        updateBallLayout()
    }

    private fun adjustDialogSizeForScreen() {
        val dialogParams = dialogLayoutParams ?: return
        val minWidth = dpToPx(200)
        val minHeight = dpToPx(200)
        val maxWidth = (screenWidth - dpToPx(16)).coerceAtLeast(minWidth)
        val maxHeight = (screenHeight - dpToPx(16)).coerceAtLeast(minHeight)
        val newWidth = dialogParams.width.coerceIn(minWidth, maxWidth)
        val newHeight = dialogParams.height.coerceIn(minHeight, maxHeight)
        if (newWidth == dialogParams.width && newHeight == dialogParams.height) return
        dialogParams.width = newWidth
        dialogParams.height = newHeight
        updateDialogLayout()
    }

    private fun updateDialogLayout() {
        val view = dialogView ?: return
        val params = dialogLayoutParams ?: return
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun updateBallLayout() {
        val view = ballView ?: return
        val params = ballLayoutParams ?: return
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private suspend fun captureScreenshotForChat(): Result<String> {
        val projection = mediaProjection
            ?: return Result.failure(IllegalStateException("未授权屏幕录制，请在设置里重新开启悬浮小球"))
        if (!mediaProjectionActive) {
            return Result.failure(IllegalStateException("屏幕录制权限失效，请在设置里重新开启悬浮小球"))
        }
        val ball = ballView
        val dialog = dialogView
        val wasDialogVisible = dialogVisible
        val startTime = System.currentTimeMillis()

        ball?.visibility = View.INVISIBLE
        dialog?.visibility = View.INVISIBLE
        delay(90)
        val screenshot = runCatching { captureScreenshotToFile(projection) }
            .onFailure { Log.w(TAG, "capture failed", it) }
            .getOrNull()
        ball?.visibility = View.VISIBLE
        if (wasDialogVisible) {
            dialog?.visibility = View.VISIBLE
            dialog?.alpha = 1f
            dialog?.translationY = 0f
        } else {
            dialog?.visibility = View.GONE
        }

        Log.d(
            TAG,
            "capture done success=${screenshot != null} cost=${System.currentTimeMillis() - startTime}ms"
        )
        return if (screenshot == null) {
            Result.failure(IllegalStateException("截图失败"))
        } else {
            screenshotFiles.add(screenshot)
            Result.success(screenshot.toUri().toString())
        }
    }

    private suspend fun captureScreenshotToFile(projection: MediaProjection): File = withContext(Dispatchers.IO) {
        refreshDisplaySize()
        val (width, height, densityDpi) = getDisplaySize()
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val handlerThread = HandlerThread("FloatingBallCapture").apply { start() }
        val handler = Handler(handlerThread.looper)
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
            Log.d(TAG, "capture createVirtualDisplay size=${width}x${height} density=$densityDpi")
            delay(180)
            var bitmap: Bitmap? = null
            var lastError: Throwable? = null
            for (attempt in 0 until 5) {
                val image = runCatching { awaitImage(imageReader, handler) }
                    .onFailure { lastError = it }
                    .getOrNull()
                if (image == null) {
                    if (attempt < 4) delay(120)
                    continue
                }
                val candidate = imageToBitmap(image, width, height)
                val blank = isBitmapBlank(candidate)
                Log.d(TAG, "capture attempt=${attempt + 1} blank=$blank")
                if (!blank) {
                    bitmap = candidate
                    break
                }
                candidate.recycle()
                if (attempt < 4) delay(120)
            }
            val result = bitmap ?: throw (lastError ?: IllegalStateException("截图内容为空"))

            val dir = File(cacheDir, "floating_ball")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                result.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            result.recycle()
            file
        } finally {
            runCatching { virtualDisplay?.release() }
            runCatching { imageReader.close() }
            runCatching { handlerThread.quitSafely() }
        }
    }

    private suspend fun awaitImage(imageReader: ImageReader, handler: Handler): Image =
        imageReader.acquireLatestImage() ?: kotlinx.coroutines.withTimeout(3000) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
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

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        return try {
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
            buffer.rewind()
            raw.copyPixelsFromBuffer(buffer)
            Bitmap.createBitmap(raw, 0, 0, width, height).also { raw.recycle() }
        } finally {
            runCatching { image.close() }
        }
    }

    private fun isBitmapBlank(bitmap: Bitmap): Boolean {
        if (bitmap.width == 0 || bitmap.height == 0) return true
        val width = bitmap.width
        val height = bitmap.height
        val xs = intArrayOf(0, width / 4, width / 2, (width * 3) / 4, width - 1)
        val ys = intArrayOf(0, height / 4, height / 2, (height * 3) / 4, height - 1)
        for (x in xs) {
            for (y in ys) {
                val color = bitmap.getPixel(x, y)
                val alpha = Color.alpha(color)
                val luminance = Color.red(color) + Color.green(color) + Color.blue(color)
                if (alpha > 16 && luminance > 20) return false
            }
        }
        return true
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

    private fun refreshDisplaySize() {
        val (width, height, _) = getDisplaySize()
        if (width <= 0 || height <= 0) return
        screenWidth = width
        screenHeight = height
    }

    private fun updateDisplayBoundsAndClamp() {
        val (width, height, _) = getDisplaySize()
        if (width <= 0 || height <= 0) return
        if (width == screenWidth && height == screenHeight) return
        screenWidth = width
        screenHeight = height
        adjustDialogSizeForScreen()
        ensureDialogInBounds()
        clampBallInBounds()
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
            .setContentTitle("悬浮球运行中")
            .setContentText("点击悬浮球展开对话框")
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

private class FloatingOverlayViewTreeOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private var started = false

    init {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        if (started) return
        started = true
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store
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
