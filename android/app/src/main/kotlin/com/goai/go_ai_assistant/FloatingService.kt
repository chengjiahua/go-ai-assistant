package com.goai.go_ai_assistant

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class FloatingService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var rootLayout: FrameLayout? = null
    private var dismissLayer: View? = null
    private var cardContainer: FrameLayout? = null
    
    private var isCardVisible = false
    private var currentState = STATE_IDLE
    
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    private var resultData: Intent? = null
    private var resultCode: Int = 0
    
    private val CHANNEL_ID = "go_ai_floating"
    private val NOTIFICATION_ID = 1
    
    private var layoutParams: WindowManager.LayoutParams? = null
    private var savedBallX: Int? = null
    private var savedBallY: Int? = null
    
    private var currentSide = "B"
    private var currentModelId = ""
    private var currentModelName = ""
    private var currentServerUrl = ""
    private var models: MutableList<ModelInfo> = mutableListOf()
    private var lastRecommendations: ArrayList<String> = ArrayList()
    private var lastWinRates: ArrayList<Double> = ArrayList()

    companion object {
        const val STATE_IDLE = 0
        const val STATE_LOADING = 1
        const val STATE_RESULT = 2
        
        var instance: FloatingService? = null
        var methodChannel: MethodChannel? = null
        private const val TAG = "FloatingService"
        
        private var savedResultCode: Int = 0
        private var savedResultData: Intent? = null
        
        fun hasCapturePermission(): Boolean {
            return savedResultCode != 0 && savedResultData != null
        }
        
        fun saveCapturePermission(resultCode: Int, data: Intent) {
            savedResultCode = resultCode
            savedResultData = data
            instance?.let { service ->
                service.resultCode = resultCode
                service.resultData = data
                service.initMediaProjection()
            }
        }
    }

    data class ModelInfo(val id: String, val name: String)

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        val metrics = DisplayMetrics()
        val display = getSystemService(WINDOW_SERVICE) as WindowManager
        display.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        Handler(Looper.getMainLooper()).postDelayed({
            showFloatingBall()
        }, 500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.hasExtra("resultCode") && it.hasExtra("resultData")) {
                resultCode = it.getIntExtra("resultCode", 0)
                resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    it.getParcelableExtra("resultData", Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    it.getParcelableExtra("resultData")
                }
                initMediaProjection()
            }
            
            if (it.hasExtra("serverUrl")) {
                currentServerUrl = it.getStringExtra("serverUrl") ?: ""
            }
            if (it.hasExtra("modelId")) {
                currentModelId = it.getStringExtra("modelId") ?: ""
                currentModelName = models.find { m -> m.id == currentModelId }?.name ?: ""
            }
            if (it.hasExtra("side")) {
                currentSide = it.getStringExtra("side") ?: "B"
            }
            if (it.hasExtra("models")) {
                val modelsJson = it.getStringExtra("models") ?: "[]"
                parseModels(modelsJson)
                currentModelName = models.find { m -> m.id == currentModelId }?.name ?: ""
            }
        }
        return START_STICKY
    }

    private fun parseModels(json: String) {
        try {
            models.clear()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val model = ModelInfo(obj.getString("id"), obj.getString("name"))
                models.add(model)
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseModels error: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideCard()
        hideFloatingBall()
        releaseMediaProjection()
        instance = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "围棋AI助手",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("围棋AI助手")
                .setContentText("悬浮窗服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("围棋AI助手")
                .setContentText("悬浮窗服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBall() {
        if (floatingView != null) return
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedBallX ?: (screenWidth - 150)
            y = savedBallY ?: (screenHeight / 3)
        }

        floatingView = createFloatingBallView()
        windowManager?.addView(floatingView, layoutParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBallView(): View {
        val density = resources.displayMetrics.density
        val ballSize = (64 * density).toInt()
        
        val container = FrameLayout(this)
        
        val ball = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ballSize, ballSize)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x991e293b.toInt())
                setStroke((2 * density).toInt(), 0x1AFFFFFF.toInt())
            }
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            
            addView(createGoStonesIcon(density))
        }
        
        container.addView(ball)
        
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val touchSlop = 10 * resources.displayMetrics.density

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    
                    if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        layoutParams?.x = initialX + dx.toInt()
                        layoutParams?.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(floatingView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isDragging) {
                        onBallClicked()
                    }
                    true
                }
                else -> false
            }
        }
        
        return container
    }

    private fun createGoStonesIcon(density: Float): View {
        val stoneSize = (24 * density).toInt()
        val containerSize = (34 * density).toInt()
        
        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(containerSize, containerSize, Gravity.CENTER)
            
            addView(View(context).apply {
                layoutParams = FrameLayout.LayoutParams(stoneSize, stoneSize).apply {
                    gravity = Gravity.START or Gravity.TOP
                    setMargins(0, 0, 0, 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF000000.toInt())
                    setStroke((1 * density).toInt(), 0xFF444444.toInt())
                }
            })
            
            addView(View(context).apply {
                layoutParams = FrameLayout.LayoutParams(stoneSize, stoneSize).apply {
                    gravity = Gravity.END or Gravity.BOTTOM
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFFFFFFF.toInt())
                    setStroke((1 * density).toInt(), 0xFFDDDDDD.toInt())
                }
            })
        }
    }

    private fun onBallClicked() {
        try {
            if (currentState == STATE_LOADING) {
                return
            }
            
            if (isCardVisible) {
                hideCard()
            } else {
                showCard()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onBallClicked error: ${e.message}")
        }
    }

    private fun hideFloatingBall() {
        floatingView?.let {
            try {
                savedBallX = layoutParams?.x
                savedBallY = layoutParams?.y
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "hideFloatingBall: ${e.message}")
            }
            floatingView = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showCard() {
        if (rootLayout != null) {
            hideCard()
        }
        if (floatingView == null) {
            return
        }
        isCardVisible = true
        currentState = STATE_IDLE
        
        val rootLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    cardContainer?.let { card ->
                        val location = IntArray(2)
                        card.getLocationOnScreen(location)
                        val cardLeft = location[0].toFloat()
                        val cardTop = location[1].toFloat()
                        val cardRight = cardLeft + card.width
                        val cardBottom = cardTop + card.height
                        
                        val x = event.rawX
                        val y = event.rawY
                        
                        if (x < cardLeft || x > cardRight || y < cardTop || y > cardBottom) {
                            hideCard()
                            true
                        } else {
                            false
                        }
                    } ?: false
                } else {
                    false
                }
            }
        }
        
        dismissLayer = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x66000000.toInt())
            alpha = 0f
            isClickable = false
            isFocusable = false
        }
        rootLayout?.addView(dismissLayer)
        
        val density = resources.displayMetrics.density
        val cardWidth = (280 * density).toInt()
        
        cardContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                cardWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            isClickable = true
            isFocusable = true
        }
        
        cardContainer?.addView(createIdleView())
        rootLayout?.addView(cardContainer)
        
        windowManager?.addView(rootLayout, rootLayoutParams)
        
        dismissLayer?.animate()?.alpha(1f)?.setDuration(200)?.start()
        cardContainer?.apply {
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
            translationY = 30f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(250)
                .start()
        }
    }

    private fun hideCard() {
        rootLayout?.let { root ->
            dismissLayer?.animate()?.alpha(0f)?.setDuration(150)?.start()
            cardContainer?.animate()
                ?.alpha(0f)
                ?.scaleX(0.9f)
                ?.scaleY(0.9f)
                ?.translationY(30f)
                ?.setDuration(150)
                ?.setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            windowManager?.removeView(root)
                        } catch (e: Exception) {
                            Log.e(TAG, "hideCard: ${e.message}")
                        }
                        rootLayout = null
                        dismissLayer = null
                        cardContainer = null
                    }
                })
                ?.start()
        }
        isCardVisible = false
        currentState = STATE_IDLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createIdleView(): View {
        val density = resources.displayMetrics.density
        
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
            background = GradientDrawable().apply {
                setColor(0xF01e293b.toInt())
                cornerRadius = 30 * density
                setStroke((1 * density).toInt(), 0x1AFFFFFF.toInt())
            }
            
            addView(TextView(context).apply {
                text = "当前模型 / MODEL"
                textSize = 10f
                setTextColor(0xB3FFFFFF.toInt())
                letterSpacing = 0.1f
                setPadding(0, 0, 0, (4 * density).toInt())
            })
            
            addView(TextView(context).apply {
                text = if (currentModelName.isNotEmpty()) currentModelName else "未选择模型"
                textSize = 13f
                setTextColor(if (currentModelName.isNotEmpty()) 0xFF3b82f6.toInt() else 0xFF64748b.toInt())
                setPadding(0, 0, 0, (16 * density).toInt())
            })
            
            addView(TextView(context).apply {
                text = "执子方向 / SIDE"
                textSize = 10f
                setTextColor(0xB3FFFFFF.toInt())
                letterSpacing = 0.1f
                setPadding(0, 0, 0, (8 * density).toInt())
            })
            
            addView(createSideSelector(density))
            
            addView(createAnalyzeButton(density))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createSideSelector(density: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                setColor(0x60000000.toInt())
                cornerRadius = 12 * density
            }
            
            addView(createSideButton("黑棋 B", "B", currentSide == "B", density))
            addView(createSideButton("白棋 W", "W", currentSide == "W", density))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createSideButton(text: String, side: String, isSelected: Boolean, density: Float): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            
            background = GradientDrawable().apply {
                setColor(if (isSelected) 0xFFFFFFFF.toInt() else 0x00000000.toInt())
                cornerRadius = 8 * density
            }
            setTextColor(if (isSelected) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    currentSide = side
                    cardContainer?.removeAllViews()
                    cardContainer?.addView(createIdleView())
                }
                true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createAnalyzeButton(density: Float): TextView {
        return TextView(this).apply {
            text = "开始 AI 分析"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (20 * density).toInt(), 0, 0)
            }
            background = GradientDrawable().apply {
                setColor(0xFF2563eb.toInt())
                cornerRadius = 16 * density
            }
            
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        (v.background as? GradientDrawable)?.setColor(0xFF1d4ed8.toInt())
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        (v.background as? GradientDrawable)?.setColor(0xFF2563eb.toInt())
                        startAnalyze()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun createLoadingView(): View {
        val density = resources.displayMetrics.density
        
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (32 * density).toInt(), (24 * density).toInt(), (32 * density).toInt())
            background = GradientDrawable().apply {
                setColor(0xF01e293b.toInt())
                cornerRadius = 30 * density
                setStroke((1 * density).toInt(), 0x1AFFFFFF.toInt())
            }
            gravity = Gravity.CENTER
            
            addView(ProgressBar(context).apply {
                isIndeterminate = true
                layoutParams = FrameLayout.LayoutParams(
                    (40 * density).toInt(),
                    (40 * density).toInt()
                )
                indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFF2563eb.toInt())
            })
            
            addView(TextView(context).apply {
                text = "正在识别棋盘数据..."
                textSize = 14f
                setTextColor(0xB3FFFFFF.toInt())
                setPadding(0, (16 * density).toInt(), 0, 0)
            })
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createResultView(): View {
        val density = resources.displayMetrics.density
        
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
            background = GradientDrawable().apply {
                setColor(0xF01e293b.toInt())
                cornerRadius = 30 * density
                setStroke((1 * density).toInt(), 0x1AFFFFFF.toInt())
            }
            
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
                
                addView(TextView(context).apply {
                    text = "AI 推荐落子"
                    textSize = 12f
                    setTextColor(0xB3FFFFFF.toInt())
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    letterSpacing = 0.1f
                })
                
                addView(TextView(context).apply {
                    text = if (currentSide == "B") "黑棋" else "白棋"
                    textSize = 10f
                    setTextColor(if (currentSide == "B") 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
                    background = GradientDrawable().apply {
                        setColor(if (currentSide == "B") 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                        cornerRadius = 4 * density
                    }
                    setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins((8 * density).toInt(), 0, 0, 0)
                    }
                })
                
                addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
                
                if (lastWinRates.isNotEmpty()) {
                    addView(TextView(context).apply {
                        val score = ((lastWinRates[0] - 0.5) * 10).toInt() / 10.0
                        text = "Score: ${if (score >= 0) "+" else ""}${String.format("%.1f", score)}"
                        textSize = 10f
                        setTextColor(0xFFef4444.toInt())
                        background = GradientDrawable().apply {
                            setColor(0x20ef4444.toInt())
                            cornerRadius = 4 * density
                        }
                        setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
                    })
                }
            })
            
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (1 * density).toInt()
                ).apply {
                    setMargins(0, (12 * density).toInt(), 0, (12 * density).toInt())
                }
                setBackgroundColor(0x1AFFFFFF.toInt())
            })
            
            lastRecommendations.take(5).forEachIndexed { index, point ->
                addView(createResultRow(index, point, density))
            }
            
            addView(TextView(context).apply {
                text = "点击屏幕任意位置关闭"
                textSize = 9f
                setTextColor(0xFF64748b.toInt())
                gravity = Gravity.CENTER
                setPadding(0, (16 * density).toInt(), 0, 0)
            })
        }
    }

    private fun createResultRow(index: Int, point: String, density: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (4 * density).toInt(), 0, (4 * density).toInt())
            }
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(0x20000000.toInt())
                cornerRadius = 12 * density
            }
            
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams((6 * density).toInt(), (28 * density).toInt()).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setMargins(0, 0, (12 * density).toInt(), 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (index == 0) 0xFFef4444.toInt() else 0xFF3b82f6.toInt())
                    cornerRadius = 3 * density
                }
            })
            
            addView(TextView(context).apply {
                text = "${index + 1}. $point"
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            })
            
            addView(TextView(context).apply {
                val winRate = if (index < lastWinRates.size) lastWinRates[index] else 0.0
                text = "${(winRate * 100).toInt()}%"
                textSize = 12f
                setTextColor(if (index == 0) 0xFF22c55e.toInt() else 0xFF94a3b8.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            })
        }
    }

    private fun switchState(newState: Int) {
        if (currentState == newState) return
        currentState = newState
        
        cardContainer?.removeAllViews()
        
        when (newState) {
            STATE_IDLE -> cardContainer?.addView(createIdleView())
            STATE_LOADING -> cardContainer?.addView(createLoadingView())
            STATE_RESULT -> cardContainer?.addView(createResultView())
        }
    }

    private fun startAnalyze() {
        val savedFloatingX = layoutParams?.x
        val savedFloatingY = layoutParams?.y
        
        layoutParams?.x = screenWidth + 100
        layoutParams?.y = screenHeight + 100
        windowManager?.updateViewLayout(floatingView, layoutParams)
        
        rootLayout?.visibility = View.INVISIBLE
        
        Handler(Looper.getMainLooper()).postDelayed({
            if (mediaProjection == null || virtualDisplay == null) {
                layoutParams?.x = savedFloatingX ?: (screenWidth - 150)
                layoutParams?.y = savedFloatingY ?: (screenHeight / 3)
                windowManager?.updateViewLayout(floatingView, layoutParams)
                onAnalyzeError("屏幕录制权限已失效，请重新启动服务")
                return@postDelayed
            }
            
            Thread {
                try {
                    val base64Image = captureScreen()
                    
                    Handler(Looper.getMainLooper()).post {
                        layoutParams?.x = savedFloatingX ?: (screenWidth - 150)
                        layoutParams?.y = savedFloatingY ?: (screenHeight / 3)
                        windowManager?.updateViewLayout(floatingView, layoutParams)
                        
                        if (base64Image == null) {
                            onAnalyzeError("截图失败")
                            return@post
                        }
                        
                        switchState(STATE_LOADING)
                        rootLayout?.visibility = View.VISIBLE
                        
                        methodChannel?.invokeMethod("analyzeFromFloating", mapOf(
                            "base64Image" to base64Image,
                            "side" to currentSide
                        ), object : MethodChannel.Result {
                            override fun success(result: Any?) {
                                onAnalyzeResult(result as? String ?: "")
                            }
                            
                            override fun error(code: String, msg: String?, details: Any?) {
                                onAnalyzeError(msg ?: "未知错误")
                            }
                            
                            override fun notImplemented() {
                                onAnalyzeError("方法未实现")
                            }
                        })
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        layoutParams?.x = savedFloatingX ?: (screenWidth - 150)
                        layoutParams?.y = savedFloatingY ?: (screenHeight / 3)
                        windowManager?.updateViewLayout(floatingView, layoutParams)
                        onAnalyzeError(e.message ?: "分析失败")
                    }
                }
            }.start()
        }, 150)
    }

    fun onAnalyzeResult(result: String) {
        try {
            val json = JSONObject(result)
            val recommendations = json.optJSONArray("recommendations") ?: JSONArray()
            
            if (recommendations.length() == 0) {
                onAnalyzeError("未找到推荐落子")
                return
            }
            
            val points = ArrayList<String>()
            val winRates = ArrayList<Double>()
            for (i in 0 until recommendations.length()) {
                val rec = recommendations.getJSONObject(i)
                val point = "${rec.optString("x")}${rec.optInt("y")}"
                points.add(point)
                winRates.add(rec.optDouble("win_rate"))
            }
            
            lastRecommendations = points
            lastWinRates = winRates
            
            switchState(STATE_RESULT)
        } catch (e: Exception) {
            Log.e(TAG, "onAnalyzeResult error: ${e.message}")
            onAnalyzeError("解析结果失败: ${e.message}")
        }
    }

    fun onAnalyzeError(error: String) {
        Log.e(TAG, "onAnalyzeError: $error")
        showToast("分析失败: $error")
        switchState(STATE_IDLE)
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initMediaProjection() {
        if (resultCode != 0 && resultData != null) {
            if (mediaProjection == null) {
                try {
                    mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData!!)
                    
                    mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            mediaProjection = null
                            virtualDisplay?.release()
                            virtualDisplay = null
                            imageReader?.close()
                            imageReader = null
                        }
                    }, Handler(Looper.getMainLooper()))
                    
                    imageReader = ImageReader.newInstance(
                        screenWidth,
                        screenHeight,
                        PixelFormat.RGBA_8888,
                        2
                    )

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
                } catch (e: Exception) {
                    Log.e(TAG, "initMediaProjection: ${e.message}")
                }
            }
        } else {
            Log.e(TAG, "initMediaProjection: 缺少权限数据")
        }
    }

    fun captureScreen(): String? {
        if (mediaProjection == null || virtualDisplay == null) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "屏幕录制权限已失效，请重新启动服务", Toast.LENGTH_LONG).show()
            }
            return null
        }
        
        try {
            var base64Result: String? = null
            val latch = java.util.concurrent.CountDownLatch(1)

            Handler(Looper.getMainLooper()).postDelayed({
                var image: Image? = null
                try {
                    image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val bitmap = imageToBitmap(image)
                        base64Result = bitmapToBase64(bitmap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "captureScreen: 处理图片失败 - ${e.message}")
                } finally {
                    image?.close()
                }
                
                latch.countDown()
            }, 100)

            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            return base64Result

        } catch (e: Exception) {
            Log.e(TAG, "captureScreen: ${e.message}")
            return null
        }
    }

    private fun releaseMediaProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
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
        bitmap.compress(Bitmap.CompressFormat.WEBP, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
