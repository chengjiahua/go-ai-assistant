package com.goai.go_ai_assistant

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.util.Base64
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream

class FloatingWindowManager(
    private val activity: Activity,
    private val flutterEngine: FlutterEngine
) {
    private val context: Context = activity.applicationContext
    private lateinit var windowManager: WindowManager
    private var floatingBallView: View? = null
    private var configPanelView: View? = null
    private var resultOverlayView: View? = null
    
    private var isBallVisible = false
    private var isPanelVisible = false
    private var isResultVisible = false
    
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    private val MEDIA_PROJECTION_REQUEST_CODE = 1001
    
    private var currentConfig: Map<String, Any> = mapOf(
        "side" to "B",
        "device" to "Samsung S24 Ultra",
        "software" to "野狐围棋"
    )
    
    private var analysisPoints: List<Map<String, Any>> = emptyList()
    
    private val methodChannel = MethodChannel(
        flutterEngine.dartExecutor.binaryMessenger,
        "com.goai.floating_window"
    )

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        initViews()
    }

    private fun initViews() {
        initFloatingBall()
        initConfigPanel()
        initResultOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingBall() {
        floatingBallView = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setBackgroundColor(Color.argb(128, 100, 100, 100))
            setPadding(24, 24, 24, 24)
            
            setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isMoved = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = (floatingBallView?.layoutParams as? WindowManager.LayoutParams)?.x ?: 0
                            initialY = (floatingBallView?.layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isMoved = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                isMoved = true
                                floatingBallView?.let { view ->
                                    val params = view.layoutParams as WindowManager.LayoutParams
                                    params.x = initialX + dx
                                    params.y = initialY + dy
                                    windowManager.updateViewLayout(view, params)
                                }
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!isMoved) {
                                toggleConfigPanel()
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }
    }

    private fun initConfigPanel() {
        configPanelView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.argb(200, 30, 30, 30))
            
            addView(TextView(context).apply {
                text = "AI BOARD VISION"
                setTextColor(Color.GRAY)
                textSize = 12f
            })
            
            addView(TextView(context).apply {
                text = "执子方向 / SIDE"
                setTextColor(Color.LTGRAY)
                textSize = 10f
                setPadding(0, 32, 0, 8)
            })
            
            val sideSwitch = Switch(context).apply {
                text = "黑棋"
                setTextColor(Color.WHITE)
                isChecked = currentConfig["side"] == "B"
                setOnCheckedChangeListener { _, isChecked ->
                    text = if (isChecked) "黑棋" else "白棋"
                    currentConfig = currentConfig.toMutableMap().apply {
                        this["side"] = if (isChecked) "B" else "W"
                    }
                }
            }
            addView(sideSwitch)
            
            addView(TextView(context).apply {
                text = "开始 AI 分析"
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(0, 32, 0, 0)
                setOnClickListener {
                    startCapture()
                }
            })
        }
    }

    private fun initResultOverlay() {
        resultOverlayView = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    fun hasMediaProjectionPermission(): Boolean {
        return mediaProjection != null
    }

    fun requestMediaProjectionPermission() {
        val intent = mediaProjectionManager?.createScreenCaptureIntent()
        activity.startActivityForResult(intent, MEDIA_PROJECTION_REQUEST_CODE)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                @Suppress("DEPRECATION")
                mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
            }
        }
    }

    fun showFloatingBall() {
        if (!isBallVisible && floatingBallView != null) {
            val params = WindowManager.LayoutParams(
                150,
                150,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = screenWidth - 180
                y = screenHeight / 2
            }
            
            windowManager.addView(floatingBallView, params)
            isBallVisible = true
        }
    }

    fun hideFloatingBall() {
        if (isBallVisible && floatingBallView != null) {
            windowManager.removeView(floatingBallView)
            isBallVisible = false
        }
    }

    fun showConfigPanel() {
        if (!isPanelVisible && isBallVisible && configPanelView != null) {
            val ballParams = floatingBallView?.layoutParams as? WindowManager.LayoutParams
            val params = WindowManager.LayoutParams(
                700,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (ballParams?.x ?: screenWidth) - 750
                y = (ballParams?.y ?: screenHeight / 2) - 100
            }
            
            windowManager.addView(configPanelView, params)
            isPanelVisible = true
        }
    }

    fun hideConfigPanel() {
        if (isPanelVisible && configPanelView != null) {
            windowManager.removeView(configPanelView)
            isPanelVisible = false
        }
    }

    private fun toggleConfigPanel() {
        if (isPanelVisible) {
            hideConfigPanel()
        } else {
            showConfigPanel()
        }
    }

    fun startCapture() {
        hideConfigPanel()
        
        if (mediaProjection == null) {
            requestMediaProjectionPermission()
            return
        }
        
        performCapture()
    }

    private fun performCapture() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
        
        Handler(Looper.getMainLooper()).postDelayed({
            captureScreen()
        }, 100)
    }

    private fun captureScreen() {
        val image = imageReader?.acquireLatestImage()
        if (image != null) {
            val bitmap = imageToBitmap(image)
            image.close()
            
            val base64 = bitmapToBase64(bitmap)
            
            methodChannel.invokeMethod("onImageCaptured", base64)
            
            virtualDisplay?.release()
            imageReader?.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth
        
        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        return Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun drawResultPoints(canvas: Canvas) {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        
        analysisPoints.forEachIndexed { index, point ->
            val x = (point["x"] as? Double ?: 0.0) * screenWidth
            val y = (point["y"] as? Double ?: 0.0) * screenHeight
            
            paint.color = if (index == 0) Color.RED else Color.BLUE
            paint.alpha = 200
            
            canvas.drawCircle(x.toFloat(), y.toFloat(), 50f, paint)
            canvas.drawText("${index + 1}", x.toFloat(), y.toFloat() + 15, textPaint)
        }
    }

    fun showResults(points: List<Map<String, Any>>) {
        analysisPoints = points
        
        if (!isResultVisible) {
            val overlayView = object : View(context) {
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    drawResultPoints(canvas)
                }
            }
            
            overlayView.setOnClickListener {
                hideResults()
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            
            resultOverlayView = overlayView
            windowManager.addView(resultOverlayView, params)
            resultOverlayView?.invalidate()
            isResultVisible = true
            
            floatingBallView?.alpha = 0.3f
        }
    }

    fun hideResults() {
        if (isResultVisible && resultOverlayView != null) {
            windowManager.removeView(resultOverlayView)
            isResultVisible = false
            
            floatingBallView?.alpha = 1.0f
        }
    }

    fun updateConfig(config: Map<String, Any>) {
        currentConfig = config
    }
}
