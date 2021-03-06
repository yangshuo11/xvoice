package com.example.ddvoice

import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.net.wifi.WifiManager
import android.preference.PreferenceManager
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.baidu.tts.chainofresponsibility.logger.LoggerProxy
import com.baidu.tts.client.SpeechSynthesizer
import com.baidu.tts.client.TtsMode
import com.example.ddvoice.action.setWakeUpAlarmClock
import com.example.ddvoice.util.TtsMessageListener
import com.github.stuxuhai.jpinyin.PinyinFormat
import com.github.stuxuhai.jpinyin.PinyinHelper
import com.iflytek.aiui.AIUIConstant
import com.iflytek.aiui.AIUIMessage
import com.iflytek.sunflower.FlowerCollector
import com.rvalerio.fgchecker.AppChecker
import com.tencent.smtt.sdk.QbSdk
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.util.*
import kotlin.concurrent.thread

/**
 * Created by Lyn on 8/2/18.
 */
const val LONG_PRESS_INTERVAL = 250L    //长按最短间隔
const val DOUBLE_CLICK_INTERVAL = 500L    //双击最长间隔
const val STOP_WAKE_UP_DELAY = 0L    //unlock stop wake delay

var gIsMainActActive = false
var gIsRecording = false
var gIsSpeaking = false
var gContactSyncOK = false
var gFromHeadset = false
var gIsPhoneLocked: Boolean = false
var gIsLynsPhone = false    //是否作者的手机
var gStartTime: Long = 0L

//var gIsHome: Boolean = false
val gAppNamePYPackageMap = mutableMapOf<String, String>()
val gContactNamePYNumMap = mutableMapOf<String, String>()

var gDeviceId: String = ""

fun postLog(message: String, service: String, action: String = "1") {
    gLogParams.clear()
    gLogParams["username"] = gDeviceId
    gLogParams["message"] = message
    //                    gLogParams["intent"] = resultStr
    gLogParams["service"] = service
    //                    gLogParams["tts"] = gStrTts
    gLogParams["action"] = action
    
    val request = JsonObjectRequest(
            Request.Method.POST, gLogUrl,
            JSONObject(gLogParams), { jsonObj -> }, { jsonObj -> })
    gVolleyQueue.add(request)
}


lateinit var gAudioManager: AudioManager

fun requestAudioFocus() {
    //    if (mAudioManager == null) {
    //
    //    }
    gAudioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
}

fun abandonAudioFocus() {
    gAudioManager.abandonAudioFocus(null)
}

fun Activity.showTip(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun Activity.showTipLong(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}


/**
 * 注意此处为了说明流程，故意在UI线程中调用。
 * 实际集成中，该方法一定在新线程中调用，并且该线程不能结束。具体可以参考NonBlockSyntherizer的写法
 */
private fun initTTs() {
    fun checkResult(result: Int, method: String) {
        if (result != 0) {
            println("error code :$result method:$method, 错误码文档:http://yuyin.baidu" +
                    ".com/docs/tts/122 ")
        }
    }
    
    LoggerProxy.printable(true) // 日志打印在logcat中
    
    // 日志更新在UI中，可以换成MessageListener，在logcat中查看日志
    val listener = TtsMessageListener() //UiMessageListener(mainHandler)
    
    // 1. 获取实例
    gTts = SpeechSynthesizer.getInstance()
    
    
    gTts.setContext(gApplicationContext)
    
    // 2. 设置listener
    gTts.setSpeechSynthesizerListener(listener)
    
    // 3. 设置appId，appKey.secretKey
    var result = gTts.setAppId(appId)
    checkResult(result, "setAppId")
    result = gTts.setApiKey(appKey, secretKey)
    checkResult(result, "setApiKey")
    
    // 5. 以下setParam 参数选填。不填写则默认值生效
    // 设置在线发声音人： 0 普通女声（默认） 1 普通男声 2 特别男声 3 情感男声<度逍遥> 4 情感儿童声<度丫丫>
    gTts.setParam(SpeechSynthesizer.PARAM_SPEAKER, "4")
    // 设置合成的音量，0-9 ，默认 5
    gTts.setParam(SpeechSynthesizer.PARAM_VOLUME, "5")
    // 设置合成的语速，0-9 ，默认 5
    gTts.setParam(SpeechSynthesizer.PARAM_SPEED, "5")
    // 设置合成的语调，0-9 ，默认 5
    gTts.setParam(SpeechSynthesizer.PARAM_PITCH, "5")
    
    // 不使用压缩传输
    gTts.setParam(SpeechSynthesizer.PARAM_AUDIO_ENCODE, SpeechSynthesizer.AUDIO_ENCODE_PCM);
    gTts.setParam(SpeechSynthesizer.PARAM_AUDIO_RATE, SpeechSynthesizer.AUDIO_BITRATE_PCM);
    
    gTts.setAudioStreamType(AudioManager.MODE_CURRENT)
    
    // 6. 初始化
    result = gTts.initTts(ttsMode)
    checkResult(result, "initTts")
    
}


// ================== 初始化参数设置开始 ==========================
/**
 * 发布时请替换成自己申请的appId appKey 和 secretKey。注意如果需要离线合成功能,请在您申请的应用中填写包名。
 * 本demo的包名是com.baidu.tts.sample，定义在build.gradle中。
 */
var appId = "11676579"

var appKey = "6kZVDwPB3CB4MK7E9BDafUuL"

var secretKey = "ZjpCq3X6kQ5pbxieI07LmrPKkZWPCp0P"

// TtsMode.MIX; 离在线融合，在线优先； TtsMode.ONLINE 纯在线； 没有纯离线
private val ttsMode = TtsMode.ONLINE

// ================选择TtsMode.ONLINE  不需要设置以下参数; 选择TtsMode.MIX 需要设置下面2个离线资源文件的路径
/*private val TEMP_DIR = "/sdcard/baiduTTS" // 重要！请手动将assets目录下的3个dat 文件复制到该目录

// 请确保该PATH下有这个文件
private val TEXT_FILENAME = "$TEMP_DIR/bd_etts_text.dat"

// 请确保该PATH下有这个文件 ，m15是离线男声
private val MODEL_FILENAME =
        "$TEMP_DIR/bd_etts_common_speech_m15_mand_eng_high_am-mix_v3.0.0_20170505.dat"*/

// ===============初始化参数设置完毕，更多合成参数请至getParams()方法中设置 =================

var gTts: SpeechSynthesizer = SpeechSynthesizer.getInstance()

fun saySorry() {
    gBAction = false
    speak("抱歉主人，我暂时还不会这个，玩命学习中")
}

val gOkAnswerList = arrayListOf("好的", "遵命", /*"Yes sir", */"明白")
fun sayOK() {
    speak(gOkAnswerList.shuffled()[0])
}


//val gWakeAnswerList = arrayListOf("是"/*, "在呢", "苗"*/)
//var gBWakeAnswerSaying = false
fun sayWakeAnswer() {
    //    gBWakeAnswerSaying = true
    
    //    speak(gWakeAnswerList.shuffled()[0])
    //        speak("我在")
    
    thread {
        gAudioTrack!!.write(gWakeAnswerBuffer, 0, gWakeAnswerBuffer.size)
        gAudioTrack.flush()
    }
    
    //    gPlayer?.start()
}


fun speak(msg: String?) {
    /* 以下参数每次合成时都可以修改
         *  mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEAKER, "0");
         *  设置在线发声音人： 0 普通女声（默认） 1 普通男声 2 特别男声 3 情感男声<度逍遥> 4 情感儿童声<度丫丫>
         *  mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_VOLUME, "5"); 设置合成的音量，0-9 ，默认 5
         *  mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEED, "5"); 设置合成的语速，0-9 ，默认 5
         *  mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_PITCH, "5"); 设置合成的语调，0-9 ，默认 5
         *
         *  mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_MIX_MODE, SpeechSynthesizer.MIX_MODE_DEFAULT);
         *  MIX_MODE_DEFAULT 默认 ，wifi状态下使用在线，非wifi离线。在线状态下，请求超时6s自动转离线
         *  MIX_MODE_HIGH_SPEED_SYNTHESIZE_WIFI wifi状态下使用在线，非wifi离线。在线状态下， 请求超时1.2s自动转离线
         *  MIX_MODE_HIGH_SPEED_NETWORK ， 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线
         *  MIX_MODE_HIGH_SPEED_SYNTHESIZE, 2G 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线
         */
    
    Log.i("lyn-----------", "speak:" + msg)
    
    if (gTts == null) {
        print("[ERROR], 初始化失败")
        return
    }
    
    if (msg != null) {
        if (msg!!.length > 512) {
            speak("抱歉主子，这段文字太长了，我暂时还不会说哦")
        } else {
            gTts!!.speak(msg)
        }
    }
    
    gStrTts += msg
}

var gBNlpCanceled = false

fun startVoiceNlp() {
    if (null == gAIUIAgent) {
        println("AIUIAgent为空，请先创建")
        return
    }
    
    Log.i(TAG, "start voice nlp")
    //        mNlpText.setText("")
    
    gBNlpCanceled = false
    
    // 先发送唤醒消息，改变AIUI内部状态，只有唤醒状态才能接收语音输入
    // 默认为oneshot模式，即一次唤醒后就进入休眠。可以修改aiui_phone.cfg中speech参数的interact_mode为continuous以支持持续交互
    //		if (AIUIConstant.STATE_WORKING != mAIUIState)
    //        run {
    val wakeupMsg = AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null)
    gAIUIAgent!!.sendMessage(wakeupMsg)
    //        }
    
    // 打开AIUI内部录音机，开始录音。若要使用上传的个性化资源增强识别效果，则在参数中添加pers_param设置
    // 个性化资源使用方法可参见http://doc.xfyun.cn/aiui_mobile/的用户个性化章节
    // 在输入参数中设置tag，则对应结果中也将携带该tag，可用于关联输入输出
    val params = "sample_rate=16000,data_type=audio,pers_param={\"uid\":\"\"},tag=audio-tag"
    val startRecord = AIUIMessage(AIUIConstant.CMD_START_RECORD, 0, 0, params, null)
    
    gAIUIAgent!!.sendMessage(startRecord)
    
    //        gIsRecording = true
}


fun updateAppNamePackageMap() {
    Log.i(TAG, "updateAppNamePackageMap")
    
    var intent: Intent? = Intent()
    intent!!.action = "android.intent.action.MAIN"
    intent.addCategory("android.intent.category.LAUNCHER")
    val pm = gApplicationContext.packageManager
    val installAppList = pm.queryIntentActivities(intent, 0)
    
    gAppNamePYPackageMap.clear()  //先重置map
    for (info in installAppList) {
        val name = info.loadLabel(pm).toString()
        
        val namePinYin = PinyinHelper.convertToPinyinString(name.toLowerCase(), "", PinyinFormat
                .WITHOUT_TONE)
        
        val pkgname = info.activityInfo.packageName
        
        gAppNamePYPackageMap.put(namePinYin, pkgname)
    }
}


fun genContactNameNumStr(ctx: Context): String? {
    val sb = StringBuilder()
    //联系人的Uri，也就是content://com.android.contacts/contacts
    val uri = ContactsContract.Contacts.CONTENT_URI
    //指定获取_id和display_name两列数据，display_name即为姓名
    val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME)
    //根据Uri查询相应的ContentProvider，cursor为获取到的数据集
    val cursor = ctx.contentResolver.query(uri, projection, null, null, null)
    val arr = arrayOfNulls<String>(cursor!!.count)
    var i = 0
    //先重置map
    gContactNamePYNumMap.clear()
    if (cursor != null && cursor.moveToFirst()) {
        do {
            val id = cursor.getLong(0)
            //获取姓名
            val name = cursor.getString(1)
            //指定获取NUMBER这一列数据
            val phoneProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            //            arr[i] = id + " , 姓名：" + name;
            
            //根据联系人的ID获取此人的电话号码
            val phonesCusor = ctx.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    phoneProjection,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=" + id, null, null)
            
            //pick the first number
            var num = ""
            if (phonesCusor != null && phonesCusor.moveToFirst()) {
                //               do {
                num = phonesCusor.getString(0)
                //                  arr[i] += " , 电话号码：" + num;
                //               }while (phonesCusor.moveToNext());
            }
            
            val namePinYin = PinyinHelper.convertToPinyinString(name.toLowerCase(), "", PinyinFormat
                    .WITHOUT_TONE)
            gContactNamePYNumMap.put(namePinYin, num)
            sb.append("{\"name\": \"${name}\", \"phoneNumber\": \"${num}\"}\n")
            //            gContactNameNumMap.put(name, num)
            i++
        } while (cursor.moveToNext())
        
        return sb.toString()
    }
    return ""
}


fun syncContacts(ctx: Context) {
    if (null == gAIUIAgent) {
        //        showTip("AIUIAgent 为空，请先创建")
        Log.e("lyn-------" + "syncContacts:", "AIUIAgent 为空")
        return
    }
    
    try {
        // 从文件中读取联系人示例数据
        //        val dataStr = FucUtil.readFile(this, "data_contact.txt", "utf-8")
        //        mNlpText.setText(dataStr)
        
        // 数据进行no_wrap Base64编码
        val contactNameNumStr = genContactNameNumStr(ctx)
        if (contactNameNumStr.isNullOrEmpty()) {
            Log.e("lyn------" + "syncContacts:", "contactNameNumStr is null")
            return
        }
        
        val dataStrBase64 = Base64.encodeToString(contactNameNumStr!!.toByteArray(charset
        ("utf-8")), Base64.NO_WRAP)
        
        val syncSchemaJson = JSONObject()
        val dataParamJson = JSONObject()
        
        // 设置id_name为uid，即用户级个性化资源
        // 个性化资源使用方法可参见http://doc.xfyun.cn/aiui_mobile/的用户个性化章节
        dataParamJson.put("id_name", "uid")
        
        // 设置res_name为联系人
        dataParamJson.put("res_name", "IFLYTEK.telephone_contact")
        
        syncSchemaJson.put("param", dataParamJson)
        syncSchemaJson.put("data", dataStrBase64)
        
        // 传入的数据一定要为utf-8编码
        val syncData = syncSchemaJson.toString().toByteArray(charset("utf-8"))
        
        // 给该次同步加上自定义tag，在返回结果中可通过tag将结果和调用对应起来
        val paramJson = JSONObject()
        paramJson.put("tag", "sync-tag")
        
        // 用schema数据同步上传联系人
        // 注：数据同步请在连接服务器之后进行，否则可能失败
        val syncAthena = AIUIMessage(AIUIConstant.CMD_SYNC,
                AIUIConstant.SYNC_DATA_SCHEMA, 0, paramJson.toString(), syncData)
        
        gAIUIAgent?.sendMessage(syncAthena)
    } catch (e: JSONException) {
        e.printStackTrace()
    } catch (e: UnsupportedEncodingException) {
        e.printStackTrace()
    }
}


//private fun syncQuery() {
//    if (null == gAIUIAgent) {
//        Log.e("lyn-------" + "syncContacts:", "AIUIAgent 为空")
//        return
//    }
//
//
//    if (TextUtils.isEmpty(mSyncSid)) {
//        showTip("sid 为空")
//        return
//    }
//
//    try {
//        // 构造查询json字符串，填入同步schema数据返回的sid
//        val queryJson = JSONObject()
//        queryJson.put("sid", mSyncSid)
//
//        // 发送同步数据状态查询消息，设置arg1为schema数据类型，params为查询字符串
//        val syncQuery = AIUIMessage(AIUIConstant.CMD_QUERY_SYNC_STATUS,
//                AIUIConstant.SYNC_DATA_SCHEMA, 0, queryJson.toString(), null)
//        gAIUIAgent.sendMessage(syncQuery)
//    } catch (e: JSONException) {
//        e.printStackTrace()
//    }
//}


/**
 * 初始化监听。
 */
/*private val gTtsInitListener = InitListener { code ->
    //    Log.d(TAG, "InitListener init() code = " + code)
    if (code != ErrorCode.SUCCESS) {
        println("初始化失败,错误码：" + code)
    } else {
        // 初始化成功，之后可以调用startSpeaking方法
        // 注：有的开发者在onCreate方法中创建完合成对象之后马上就调用startSpeaking进行合成，
        // 正确的做法是将onCreate中的startSpeaking调用移至这里
    }
}*/

lateinit var gApplicationContext: Context
lateinit var gAccessibilityService: MyAccessibilityService


private var mSharedPreferences: SharedPreferences? = null

fun loadStr(key: String, defStr: String): String {
    if (mSharedPreferences == null) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(gApplicationContext)
    }
    val value = mSharedPreferences!!.getString(key, defStr)
    Log.v("TAG", "load str:" + value!!)
    return value
}

fun saveStr(key: String, value: String) {
    Log.v("TAG", "save str:$value")
    if (mSharedPreferences == null) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(gApplicationContext)
    }
    mSharedPreferences!!.edit().putString(key, value).commit()
}

fun loadBool(key: String): Boolean {
    if (mSharedPreferences == null) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(gApplicationContext)
    }
    return mSharedPreferences!!.getBoolean(key, false)
}

fun loadBool(key: String, value: Boolean): Boolean {
    if (mSharedPreferences == null) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(gApplicationContext)
    }
    return mSharedPreferences!!.getBoolean(key, value)
}

fun saveBool(key: String, value: Boolean) {
    if (mSharedPreferences == null) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(gApplicationContext)
    }
    mSharedPreferences!!.edit().putBoolean(key, value).commit()
}

fun loadInt(key: String, defVal: Int): Int {
    if (mSharedPreferences == null) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(gApplicationContext)
    }
    return mSharedPreferences!!.getInt(key, defVal)
}

fun saveInt(key: String, value: Int) {
    // Log.e(TAG, "int val:" + value);
    if (mSharedPreferences == null) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(gApplicationContext)
    }
    mSharedPreferences!!.edit().putInt(key, value).commit()
}


fun turnOnBluetooth(): Boolean {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    if (bluetoothAdapter != null) {
        return bluetoothAdapter.enable()
    }
    return false
}


fun turnOffBluetooth(): Boolean {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    if (bluetoothAdapter != null) {
        return bluetoothAdapter.disable()
    }
    return false
}

fun setWifiEnabled(enabled: Boolean): Boolean {
    val wifiManager = gApplicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    return wifiManager.setWifiEnabled(enabled)
}


fun turnOnHomeKeyWake() {
    gBHomeKeyWakeUp = true
    saveBool(SP_HOME_KEY_WAKE, gBHomeKeyWakeUp)
}

fun turnOffHomeKeyWake() {
    gBHomeKeyWakeUp = false
    saveBool(SP_HOME_KEY_WAKE, gBHomeKeyWakeUp)
}

fun showMyAppDetailsActiviry() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    val uri = Uri.fromParts("package", gApplicationContext.getPackageName(), null)
    intent.setData(uri)
    gApplicationContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

const val SP_WAKE_UP_TIP_SHOWN = "wakeuptipshown"
const val SP_HOME_KEY_WAKE = "pref_home_key_wakeup"
const val SP_VOLUME_KEY_WAKE = "pref_volume_key_wakeup"
const val SP_VOICE_WAKE = "pref_voice_wakeup"
const val SP_EXCLUDE_FROM_RECENTS = "pref_exclude_from_recents"
const val SP_ACCESSIBILITY_ENABLED = "accessibilityEnabled"
const val SP_HB_API_KEY = "pref_hb_api_key"
const val SP_LIGHT_ON_URL = "pref_light_on_url"
const val SP_LIGHT_OFF_URL = "pref_light_off_url"

var gWakeUpTipShown = false     //only show once on home
var gAccessibilityEnabled = false
var gBHomeKeyWakeUp = true
var gBVolumeKeyWakeUp = true
var gBVoiceWakeUp = true
var gBExcludeFromRecents = false
var gHbApiKey = ""
var gLightOnUrl = ""
var gLightOffUrl = ""

//var gIsRecording = false

fun loadSharedPrefs() {
    gBHomeKeyWakeUp = loadBool(SP_HOME_KEY_WAKE, true)
    gBVolumeKeyWakeUp = loadBool(SP_VOLUME_KEY_WAKE, true)
    gBVoiceWakeUp = loadBool(SP_VOICE_WAKE, true)
    gBExcludeFromRecents = loadBool(SP_EXCLUDE_FROM_RECENTS, false)
    gWakeUpTipShown = loadBool(SP_WAKE_UP_TIP_SHOWN, false)
    gAccessibilityEnabled = loadBool(SP_ACCESSIBILITY_ENABLED, false)
    gHbApiKey = loadStr(SP_HB_API_KEY, "")
    gLightOnUrl = loadStr(SP_LIGHT_ON_URL, "")
    gLightOffUrl = loadStr(SP_LIGHT_OFF_URL, "")
}

var mWakeOnPackages: ArrayList<String> = ArrayList()

/**
 * 获得属于桌面的应用的应用包名称
 * @return 返回包含所有包名的字符串列表
 */
private fun getHomes(): ArrayList<String> {
    val names = ArrayList<String>()
    val packageManager = gApplicationContext.packageManager
    //属性
    val intent = Intent(Intent.ACTION_MAIN)
    intent.addCategory(Intent.CATEGORY_HOME)
    val resolveInfo = packageManager.queryIntentActivities(intent,
            PackageManager.MATCH_DEFAULT_ONLY)
    for (ri in resolveInfo) {
        names.add(ri.activityInfo.packageName)
        //        Log.i(TAG, "packageName =" + ri.activityInfo.packageName)
    }
    return names
}

private var gAppChecker: AppChecker? = null

fun startChecker() {
    if (gAppChecker == null) {
        gAppChecker = AppChecker().whenOther { it: String ->
            Log.d(TAG, "Foreground: $it")
            var found = false
            for (item in mWakeOnPackages) {
                if (it.contains(item)) {
                    found = true
                    break
                }
            }
            
            if (found) startWakeUp() else stopWakeUp()
        }.timeout(2000)
        
        gAppChecker!!.start(gApplicationContext)
    } /*else {
        stopChecker()
    }*/
}


fun startMainAct() {
    val intent = Intent(gApplicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (gBExcludeFromRecents) intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    gApplicationContext.startActivity(intent)
}

const val TAG: String = "MainApp"

fun stopChecker() {
    gAppChecker?.stop()
    gAppChecker = null
    Log.d(TAG, "appCheckerStopped")
}

lateinit var gAudioTrack: AudioTrack
var gWakeAnswerBuffer = ByteArray(19 * 1000)

private fun initAudioTrack() {
    val minBufferSize = AudioTrack.getMinBufferSize(8000, AudioFormat
            .CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT)
    gAudioTrack = AudioTrack(AudioManager.MODE_CURRENT, 8000,
            AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize * 2, AudioTrack.MODE_STREAM)
    
    //    println("minBufferSize = $minBufferSize")
    //    audioTrack!!.setStereoVolume(1.0f, 1.0f) // 设置当前音量大小
    println("initAudioTrack over")
    gAudioTrack!!.play()
    
    //    val audioFile = File("/sdcard/shi.pcm")
    //    System.out.println(audioFile!!.length())
    try {
        val fileInputStream = gApplicationContext.resources.openRawResource(R.raw.shi)
        //FileInputStream
        // (audioFile)
        fileInputStream!!.skip(0x2c)
        fileInputStream!!.read(gWakeAnswerBuffer)
    } catch (e: Exception) {
    }
}


val gLogParams = HashMap<String, String>()
lateinit var gVolleyQueue: RequestQueue
val gLogUrl = "http://121.40.106.47:6000/log/asr"


class MainApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        FlowerCollector.setCaptureUncaughtException(true)    //统计
        //xun fei online parameters
        FlowerCollector.updateOnlineConfig(this, {
            //回调仅在参数有变化时发生
        })
        
        gApplicationContext = this
        
        loadSharedPrefs()
        
        mWakeOnPackages.addAll(getHomes())
        //地图
        mWakeOnPackages.add("com.baidu.BaiduMap")
        mWakeOnPackages.add("com.autonavi")
        
        gVolleyQueue = Volley.newRequestQueue(gApplicationContext)
        
        startChecker()
        //        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        
        //tts初始化
        //        SpeechUtility.createUtility(this, "appid=539e78ed")
        // 初始化合成对象
        //        gTts = SpeechSynthesizer.createSynthesizer(this, gTtsInitListener)
        initTTs()
        
        gAudioManager = gApplicationContext!!.getSystemService(Context.AUDIO_SERVICE) as
                AudioManager
        
        //        gPlayer = MediaPlayer.create(this, R.raw.yes)
        //        gPlayer = MediaPlayer.create(this, Uri.parse("/sdcard/Bar.pcm"))
        initAudioTrack()
        
        //所有app名的拼音和包名映射
        Thread({
            updateAppNamePackageMap()
        }).start()
        
        if (gIsLynsPhone) setWakeUpAlarmClock()
        
        //所有contact name的拼音和number映射
        //        updateContactNameNumMap(this)
        
        // 以下语句用于设置日志开关（默认开启），设置成false时关闭语音云SDK日志打印
        //        Setting.setShowLog(true)
        
        
        //搜集本地tbs内核信息并上报服务器，服务器返回结果决定使用哪个内核。
        val cb = object : QbSdk.PreInitCallback {
            
            override fun onViewInitFinished(arg0: Boolean) {
                // TODO Auto-generated method stub
                //x5內核初始化完成的回调，为true表示x5内核加载成功，否则表示x5内核加载失败，会自动切换到系统内核。
                Log.d("app", " onViewInitFinished is $arg0")
            }
            
            override fun onCoreInitFinished() {
                // TODO Auto-generated method stub
            }
        }
        //x5内核初始化接口
        QbSdk.initX5Environment(applicationContext, cb)
    }
    
}