package com.example.ddvoice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.TargetApi
import android.app.AppOpsManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.media.AudioManager.USE_DEFAULT_STREAM_TYPE
import android.os.Build
import android.os.Handler
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.baidu.speech.EventManager
import com.baidu.speech.EventManagerFactory
import com.baidu.speech.asr.SpeechConstant
import com.example.ddvoice.action.*
import com.example.ddvoice.receiver.ScreenOffBroadcastReceiver
import com.example.ddvoice.receiver.ScreenOnBroadcastReceiver
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timerTask


var gWxContact = ""
var gWxContent = ""
//var gOpenningUsageAccess = false
private var gWakeup: EventManager? = null
//private var gBWakeupOn = false

/**
 * 测试参数填在这里
 */
fun startWakeUp() {
    printLog("gIsRecording：$gIsRecording")
    printLog("gBVoiceWakeUp：$gBVoiceWakeUp")
    
    if (gIsMainActActive || gIsRecording || !gBVoiceWakeUp) {
        return
    } else {
        val params = TreeMap<String, Any>()
        
        params[SpeechConstant.ACCEPT_AUDIO_VOLUME] = false
        params[SpeechConstant.WP_WORDS_FILE] = "assets:///WakeUp.bin"
        params[SpeechConstant.APP_ID] = "11676579"
        // "assets:///WakeUp.bin" 表示WakeUp.bin文件定义在assets目录下
        
        var json: String? = null // 这里可以替换成你需要测试的json
        json = JSONObject(params).toString()
        gWakeup?.send(SpeechConstant.WAKEUP_START, json, null, 0, 0)
        printLog("输入参数：$json")
    }
}

fun stopWakeUp() {
    //    if (!gBWakeupOn) {
    //        return //printLog("已为停止状态，无须再次停止")
    //    } else {
    gWakeup?.send(SpeechConstant.WAKEUP_STOP, null, null, 0, 0)
    //        printLog("stop")
    //    }
}


private fun printLog(text: String) {
    var text = text
    if (true) {
        text += "  ;time=" + System.currentTimeMillis()
    }
    text += "\n"
    Log.d("lyn:-------- wakeup:", text)
}


//音量控制,初始化定义
//lateinit var gAudioManager: AudioManager

class MyAccessibilityService : AccessibilityService() {
    private val info = AccessibilityServiceInfo()
    private var currntApplicationPackage = ""
    
    private var windowManager: WindowManager? = null
    private var showWindow = false
    
    //    Timer timer;
    //    var mKeyPressedLong: Long = 0
    private var mKeyDownTime: Long = 0
    
    
    private val screenOnBroadcastReceiver = ScreenOnBroadcastReceiver()
    
    private val screenOffBroadcastReceiver = ScreenOffBroadcastReceiver()
    
    override fun onCreate() {
        super.onCreate()
        
        println("lyn_________:my service created!")
        
        gAccessibilityService = this
        //        gAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        
        //        if (BuildConfig.DEBUG) {
        //            Handler().postDelayed({ wxRedBag("") }, 1000L)
        //        }
        
        registerReceiver(screenOnBroadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(screenOffBroadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        
        gWakeup = EventManagerFactory.create(this, "wp")
        gWakeup!!.registerListener { name: String?, params: String?, data: ByteArray?, i: Int, i1:
        Int ->
            when (name) {
                "wp.data" -> {
                    when {
                        params!!.contains("拍照拍照") -> {
                            //                            turnOnScreen()
                            //                            sayOK()
                            //                            val starter = Intent()
                            //                            starter.action = "android.media.action.STILL_IMAGE_CAMERA_SECURE"
                            //                            starter.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            //                            startActivity(starter)
                            launchCamera()
                        }
                        params!!.contains("打开手电筒") -> {
                            trunOnFlash()
                        }
                        params!!.contains("关闭手电筒") -> {
                            turnOffFlash()
                        }
                        params!!.contains("播放") -> {
                            //                            sayOK()
                            replayMusic()
                        }
                        params!!.contains("暂停") -> {
                            //                            sayOK()
                            pauseMusic()
                        }
                        params!!.contains("下一首") -> {
                            //                            sayOK()
                            nextMusic()
                        }
                        params!!.contains("上一首") -> {
                            prevMusic()
                        }
                        params!!.contains("微信扫码") -> {
                            wxScan()
                        }
                        params!!.contains("开灯开灯") -> {
                            turnOnLight()
                        }
                        params!!.contains("关灯关灯") -> {
                            turnOffLight()
                        }
                        else -> //                    stopWakeUp()
                            startMainAct()
                    }
                    
                    val message = "{" + JSONObject(params).optString("word") + "}"
                    postLog(message, "{wakeup}")
                    //post log
                    /*gLogParams.clear()
                    gLogParams["username"] = gDeviceId
                    gLogParams["message"] =
                    //                    gLogParams["intent"] = resultStr
                    gLogParams["service"] =
                    //                    gLogParams["tts"] = gStrTts
                    gLogParams["action"] = "1"
                    
                    val request = JsonObjectRequest(
                            Request.Method.POST, gLogUrl,
                            JSONObject(gLogParams), { jsonObj -> }, { jsonObj -> })
                    gVolleyQueue.add(request)*/
                }
                "wp.error" -> {
                    if (JSONObject(params).optInt("error") == 3)/*拿不到mic？*/ startWakeUp()
                }
                //                "wp.exit" -> gBWakeupOn = false
                //                "wp.ready" -> gBWakeupOn = true
            }
            
            var logTxt = "name: $name"
            if (params != null && !params.isEmpty()) {
                logTxt += " ;params :$params"
            } else if (data != null) {
                logTxt += " ;data length=" + data.size
            }
            printLog(logTxt)
        } //  EventListener 中 onEvent方法
        //        registerReceiver(ScreenOnBroadcastReceiver(), IntentFilter(Intent.ACTION_SCREEN_ON))
        
        if (needsUsageStatsPermission()) {
            requestUsageStatsPermission()
            //            return
        }
        //        else {
        startChecker()
        //        }
        
    }
    
    
    private fun postLollipop(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        stopWakeUp()
        stopChecker()
        unregisterReceiver(screenOnBroadcastReceiver)
        unregisterReceiver(screenOffBroadcastReceiver)
    }
    
    fun needsUsageStatsPermission(): Boolean {
        return postLollipop() && !hasUsageStatsPermission(this)
    }
    
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun requestUsageStatsPermission() {
        if (!hasUsageStatsPermission(this)) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Handler().postDelayed({
                turnOnUsageAccess()
            }, 1500L)
            //            gOpenningUsageAccess = true
        }
    }
    
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow("android:get_usage_stats",
                android.os.Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("lyn_________:my service started!")
        //        Toast.makeText(this, "lyn_________:my service started!", Toast.LENGTH_SHORT).show();
        //        startWakeUp()
        when (intent?.action) {
            "do_alarm" -> {
                var content = "主人，" + (intent!!.getStringExtra("content") ?: "提醒时间到啦") + "。"
                for (i in 0..2) content += content
                speak(content)
            }
            "do_wakeup_alarm" -> {
                val date = Calendar.getInstance().time
                val dateStr = SimpleDateFormat("yyyyMMdd").format(date)
//                Log.d("lyn- onStartCommand", dateStr)
                val request = JsonObjectRequest(
                        Request.Method.GET, "http://api.k780.com/?app=life" +
                        ".workday&date=$dateStr&appkey=10003&sign" +
                        "=b59bc3ef6191eb9f747dd4e83c99f2a4&format=json",
                        JSONObject(gLogParams), { jsonObj ->
                    val workmk = jsonObj.getJSONObject("result")["workmk"]
                    if (workmk == "1") {
//                        Log.d("lyn- onStartCommand", "work day alarm trig!")
                        turnOnLight()
//                        var content = "主人，起床啦"
//                        for (i in 0..1) content += content
//                        speak(content)
                    } /*else {
                        Log.d("lyn- onStartCommand", "work day alarm not trig!")
                    }*/
                }, {})
                gVolleyQueue.add(request)
                
                setWakeUpAlarmClock()
            }
            "observe_contacts" -> //            println("lyn_________:observe_contacts")
                //联系人变动监测
                contentResolver.registerContentObserver(
                        ContactsContract.Contacts.CONTENT_URI, true, mContactsObserver)
        }
        return Service.START_STICKY //super.onStartCommand(intent, flags, startId)
        //super.onStartCommand(intent, flags, startId)
    }
    
    private val mContactsObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            gContactSyncOK = false
        }
    }
    
    public override fun onServiceConnected() {
        val info = serviceInfo ?: return
        
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
    }
    
    //    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    //        println("lyn:my service started!")
    //
    //        registerReceiver(ScreenOnBroadcastReceiver(), IntentFilter(Intent.ACTION_SCREEN_ON))
    //
    //        return Service.START_STICKY
    //    }
    
    
    /**
     * 判断当前界面是否是桌面
     */
    //    private fun isHome(): Boolean {
    //        val mActivityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    //        val rti = mActivityManager.getRunningTasks(1)
    //        Log.i(TAG, "rti[0].topActivity.packageName:" + rti[0].topActivity
    //                .packageName)
    //        val strs = getHomes()
    //        return if (strs != null && strs.isNotEmpty()) {
    //            strs.contains(rti[0].topActivity.packageName)
    //        } else {
    //            false
    //        }
    //    }
    
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        Log.d(TAG, "onAccessibilityEvent:" + eventType)
        val sourcePackageName = event.packageName as String? ?: ""
        //        currntApplicationPackage = sourcePackageName
        Log.d(TAG, "sourcePackageName:$sourcePackageName")
        Log.d(TAG, "parcelable:" + event.text?.toString())
        //        Log.d(TAG, "isHome:" + isHome())
        
        //        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (eventType != TYPE_WINDOW_CONTENT_CHANGED) {
            gStartTime = Date().time
        }
        
        when (eventType) {
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION -> Log.d(TAG, "CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION")
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "TYPE_WINDOW_STATE_CHANGED");
                
            }
            AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE -> Log.d(TAG, "CONTENT_CHANGE_TYPE_SUBTREE")
            AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT -> Log.d(TAG, "CONTENT_CHANGE_TYPE_TEXT")
            AccessibilityEvent.INVALID_POSITION -> Log.d(TAG, "INVALID_POSITION")
            AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED -> Log.d(TAG, "CONTENT_CHANGE_TYPE_UNDEFINED")
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> Log.d(TAG, "TYPE_ANNOUNCEMENT")
            AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT -> Log.d(TAG, "TYPE_ASSIST_READING_CONTEXT")
            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> Log.d(TAG, "TYPE_GESTURE_DETECTION_END")
            AccessibilityEvent.TYPE_VIEW_CLICKED -> Log.d(TAG, "TYPE_VIEW_CLICKED")
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> Log.d(TAG, "TYPE_TOUCH_EXPLORATION_GESTURE_START")
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> Log.d(TAG, "TYPE_GESTURE_DETECTION_START")
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> Log.d(TAG, "TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED")
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> Log.d(TAG, "TYPE_VIEW_ACCESSIBILITY_FOCUSED")
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> Log.d(TAG, "TYPE_WINDOWS_CHANGED")
            
            /*if (event.packageName == null || !(event.packageName == "com.bsb.hike" || !(event.packageName == "com.whatsapp" || event.packageName == "com.facebook.orca" || event.packageName == "com.twitter.android" || event.packageName == "com.facebook.katana" || event.packageName == "com.facebook.lite")))
                showWindow = false
            
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                Log.d(TAG, "TYPE_VIEW_TEXT_CHANGED")
                //            if (windowController == null)
                //                windowController = new WindowPositionController(windowManager, getApplicationContext());
                //            showWindow = true;
                //            windowController.notifyDatasetChanged(event.getText().toString(), currntApplicationPackage);
            } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                Log.d(TAG, "TYPE_WINDOW_STATE_CHANGED:" + event.contentDescription)
                
                if (event.packageName == "com.whatsapp" && (event.contentDescription == null || event.contentDescription != "Type a message"))
                    showWindow = false
                if (event.packageName == "com.facebook.katana" && (event.text.toString() == "[What's on your mind?]" || event.text.toString() == "[Search]"))
                    showWindow = false
                if (event.packageName == "com.twitter.android" && (event.text.toString() == "[What\u2019s happening?]" || event.text.toString() == "[Search Twitter]"))
                    showWindow = false
                if (event.contentDescription != null && event.contentDescription.toString() == "Textbox in chat thread")
                    showWindow = true
                
                
                //remove window when keyboard closed or user moved from chatting to other things
                //            if (windowController != null && !showWindow)
                //                windowController.onDestroy();
            }*/
        }
        
        /*if (event.packageName == null || !(event.packageName == "com.bsb.hike" || !(event.packageName == "com.whatsapp" || event.packageName == "com.facebook.orca" || event.packageName == "com.twitter.android" || event.packageName == "com.facebook.katana" || event.packageName == "com.facebook.lite")))
            showWindow = false
        
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            Log.d(TAG, "TYPE_VIEW_TEXT_CHANGED")
            //            if (windowController == null)
            //                windowController = new WindowPositionController(windowManager, getApplicationContext());
            //            showWindow = true;
            //            windowController.notifyDatasetChanged(event.getText().toString(), currntApplicationPackage);
        } */
    }
    
    override fun onInterrupt() {
        
    }
    
    
    lateinit var timer: Timer
    lateinit var timer_dbc: Timer
    //    private var isVolumeKeyPressed = false
    var dbcTimerRunning = false
    
    override fun onKeyEvent(event: KeyEvent): Boolean {
        println("key event______________:" + KeyEvent.keyCodeToString(event.keyCode) + "------count:" + event
                .repeatCount)
        //        Toast.makeText(this, KeyEvent.keyCodeToString(event.keyCode) + "------count:" + event
        //                .repeatCount, Toast.LENGTH_SHORT).show()
        
        val keyCode = event.keyCode
        
        if (gBVolumeKeyWakeUp && intArrayOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent
                        .KEYCODE_VOLUME_DOWN/*, KeyEvent
                        .KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_HEADSETHOOK*/)
                        .contains(keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                mKeyDownTime = Calendar.getInstance().time.time
                //                isVolumeKeyPressed = true
                timer = Timer()
                //长按则此timer不会被取消
                timer.schedule(timerTask {
                    //                    print("lyn____________:key long pressed!")
                    
                    //                    startActivity(Intent("STOP_WEB_ACT"))
                    
                    startMainAct()
                    
                    //post log
                    gLogParams.clear()
                    gLogParams["username"] = gDeviceId
                    gLogParams["message"] = "{" + KeyEvent.keyCodeToString(keyCode) + "}"
                    gLogParams["service"] = "wakeup"
                    gLogParams["action"] = "1"
                    
                    val request = JsonObjectRequest(
                            Request.Method.POST, gLogUrl,
                            JSONObject(gLogParams), { jsonObj -> }, { jsonObj -> })
                    gVolleyQueue.add(request)
                    
                }, LONG_PRESS_INTERVAL)
                
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                
                if (Calendar.getInstance().time.time - mKeyDownTime > LONG_PRESS_INTERVAL * 1.5) {
                    //长按，不处理
                    //                    Toast.makeText(this, "lyn____________:key long pressed!", Toast.LENGTH_SHORT).show()
                    return true
                } else {
                    //非长按，取消timer，调节音量
                    timer.cancel()
                    if (keyCode == KeyEvent
                                    .KEYCODE_VOLUME_DOWN) {
                        gAudioManager.adjustSuggestedStreamVolume(AudioManager
                                .ADJUST_LOWER, USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
                        
                    } else if (keyCode == KeyEvent
                                    .KEYCODE_VOLUME_UP) {   //增加音量，调出系统音量控制
                        gAudioManager.adjustSuggestedStreamVolume(AudioManager
                                .ADJUST_RAISE, USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
                    }
                }
            }
        } else if (gBHomeKeyWakeUp && KeyEvent.KEYCODE_HOME == keyCode) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                mKeyDownTime = Calendar.getInstance().time.time
                //                isVolumeKeyPressed = true
                timer = Timer()
                //长按则此timer不会被取消
                timer.schedule(timerTask {
                    //                    print("lyn____________:key long pressed!")
                    
                    //                    startActivity(Intent("STOP_WEB_ACT").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    startMainAct()
                    
                    //post log
                    gLogParams.clear()
                    gLogParams["username"] = gDeviceId
                    gLogParams["message"] = "{" + KeyEvent.keyCodeToString(keyCode) + "}"
                    gLogParams["service"] = "wakeup"
                    gLogParams["action"] = "1"
                    val request = JsonObjectRequest(
                            Request.Method.POST, gLogUrl,
                            JSONObject(gLogParams), { jsonObj -> }, { jsonObj -> })
                    gVolleyQueue.add(request)
                    
                }, LONG_PRESS_INTERVAL)
                
            } else if (event.action == KeyEvent.ACTION_UP) {
                //                isVolumeKeyPressed = false
                //                println("lyn____________time gap:" + (Calendar.getInstance().time.time -
                //                        mKeyDownTime))
                if (Calendar.getInstance().time.time - mKeyDownTime > LONG_PRESS_INTERVAL) {
                    //长按，不处理
                    //                    Toast.makeText(this, "lyn____________:key long pressed!", Toast.LENGTH_SHORT).show()
                    //                    return true
                } else {
                    //非长按，取消timer，模拟按键
                    timer.cancel()
                    //                    performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    //                    clickCount++
                    
                    //上次单击设置的定时器已经停止，说明上次是单击，否则说明上次是双击的第一次
                    if (dbcTimerRunning) {  //执行双击动作，取消定时器
                        if (gIsLynsPhone) {  //Author's phone
                            //                        stAct("com.microsoft.launcher", "com.microsoft.launcher.Launcher")
                            stAct("ch.deletescape.lawnchair", "ch.deletescape.lawnchair.Launcher")
                        } else {
                            performGlobalAction(AccessibilityService
                                    .GLOBAL_ACTION_RECENTS)
                        }
                        timer_dbc.cancel()
                    } else { //执行单击动作
                        if (gIsLynsPhone) {  //Author's phone
                            //                        stAct("com.microsoft.launcher", "com.microsoft.launcher.Launcher")
                            stAct("ch.deletescape.lawnchair", "ch.deletescape.lawnchair.Launcher")
                        } else {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                    }
                    
                    timer_dbc = Timer()
                    timer_dbc.schedule(timerTask {
                        
                        dbcTimerRunning = false
                    }, DOUBLE_CLICK_INTERVAL)
                    dbcTimerRunning = true
                }
            }
            
            return true
        }
        
        return super.onKeyEvent(event)
    }
    
    
    private val TAG: String = "MyAccessibilityService:"
    
    
}