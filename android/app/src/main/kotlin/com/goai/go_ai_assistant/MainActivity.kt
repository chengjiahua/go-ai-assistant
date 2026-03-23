package com.goai.go_ai_assistant

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.goai.go_ai_assistant/floating"
    
    private var mediaProjectionManager: MediaProjectionManager? = null
    
    private val REQUEST_MEDIA_PROJECTION = 1001
    private val REQUEST_OVERLAY_PERMISSION = 1002
    private val REQUEST_CAPTURE_PERMISSION = 1003
    
    private var pendingConfig: Map<String, Any>? = null
    private var pendingCaptureResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        FloatingService.methodChannel = channel
        
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "checkOverlayPermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        result.success(Settings.canDrawOverlays(this))
                    } else {
                        result.success(true)
                    }
                }
                
                "requestOverlayPermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        )
                        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
                        result.success(false)
                    } else {
                        result.success(true)
                    }
                }
                
                "checkCapturePermission" -> {
                    result.success(FloatingService.hasCapturePermission())
                }
                
                "requestCapturePermission" -> {
                    pendingCaptureResult = result
                    val intent = mediaProjectionManager?.createScreenCaptureIntent()
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_CAPTURE_PERMISSION)
                }
                
                "startFloatingService" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                        result.error("NO_PERMISSION", "需要悬浮窗权限", null)
                    } else {
                        pendingConfig = call.arguments as? Map<String, Any>
                        val intent = mediaProjectionManager?.createScreenCaptureIntent()
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
                        result.success(true)
                    }
                }
                
                "stopFloatingService" -> {
                    stopService(Intent(this, FloatingService::class.java))
                    result.success(true)
                }
                
                "updateFloatingConfig" -> {
                    val config = call.arguments as? Map<String, Any>
                    if (config != null) {
                        updateFloatingServiceConfig(config)
                        result.success(true)
                    } else {
                        result.error("INVALID_ARGS", "无效的配置参数", null)
                    }
                }
                
                "captureScreen" -> {
                    val base64 = FloatingService.instance?.captureScreen()
                    if (base64 != null) {
                        result.success(base64)
                    } else {
                        result.error("CAPTURE_ERROR", "截图失败，请先授权屏幕录制", null)
                    }
                }
                
                else -> result.notImplemented()
            }
        }
    }
    
    private fun updateFloatingServiceConfig(config: Map<String, Any>) {
        val serviceIntent = Intent(this, FloatingService::class.java).apply {
            action = "UPDATE_CONFIG"
            putExtra("serverUrl", config["serverUrl"] as? String ?: "")
            putExtra("modelId", config["modelId"] as? String ?: "")
            putExtra("side", config["side"] as? String ?: "B")
            
            val modelsList = config["models"] as? List<Map<String, String>>
            if (modelsList != null) {
                val modelsJson = JSONArray(modelsList.map { model ->
                    JSONObject().apply {
                        put("id", model["id"] ?: "")
                        put("name", model["name"] ?: "")
                    }
                }).toString()
                putExtra("models", modelsJson)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_MEDIA_PROJECTION -> {
                if (resultCode == RESULT_OK && data != null) {
                    FloatingService.saveCapturePermission(resultCode, data)
                    
                    val serviceIntent = Intent(this, FloatingService::class.java).apply {
                        putExtra("resultCode", resultCode)
                        putExtra("resultData", data)
                        
                        pendingConfig?.let { config ->
                            putExtra("serverUrl", config["serverUrl"] as? String ?: "")
                            putExtra("modelId", config["modelId"] as? String ?: "")
                            putExtra("side", config["side"] as? String ?: "B")
                            
                            val modelsList = config["models"] as? List<Map<String, String>>
                            if (modelsList != null) {
                                val modelsJson = JSONArray(modelsList.map { model ->
                                    JSONObject().apply {
                                        put("id", model["id"] ?: "")
                                        put("name", model["name"] ?: "")
                                    }
                                }).toString()
                                putExtra("models", modelsJson)
                            }
                        }
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    
                    pendingConfig = null
                }
            }
            
            REQUEST_CAPTURE_PERMISSION -> {
                if (resultCode == RESULT_OK && data != null) {
                    FloatingService.saveCapturePermission(resultCode, data)
                    pendingCaptureResult?.success(true)
                } else {
                    pendingCaptureResult?.success(false)
                }
                pendingCaptureResult = null
            }
            
            REQUEST_OVERLAY_PERMISSION -> {
            }
        }
    }
}
