package com.raman.focustimerkt

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.raman.focustimerkt.FocusTimerApplication.Companion.CHANNEL_ID
import com.raman.focustimerkt.TimerData.Companion.COOLDOWN_TIME
import com.raman.focustimerkt.TimerData.Companion.DEFAULT_COOLDOWN_TIME
import com.raman.focustimerkt.TimerData.Companion.DEFAULT_INTERVAL_TIME
import com.raman.focustimerkt.TimerData.Companion.INTERVAL_TIME
import com.raman.focustimerkt.TimerData.Companion.REPEAT_COUNTER
import com.raman.focustimerkt.TimerData.Companion.SECOND_MS
import com.raman.focustimerkt.TimerData.Companion.TIMER_COMPLETE
import com.raman.focustimerkt.TimerData.Companion.TIMER_PAUSE
import com.raman.focustimerkt.TimerData.Companion.TIMER_RESET
import com.raman.focustimerkt.TimerData.Companion.TIMER_RESUME
import com.raman.focustimerkt.TimerData.Companion.TIMER_SET_REPEAT_COUNTER
import com.raman.focustimerkt.TimerData.Companion.TIMER_START
import com.raman.focustimerkt.TimerData.Companion.TIMER_STATUS
import com.raman.focustimerkt.TimerData.Companion.TIMER_TICK
import com.raman.focustimerkt.TimerData.Companion.TIME_DELTA
import com.raman.focustimerkt.TimerData.Companion.getTimeFormattedString
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class BackgroundTimerService : Service() {

    companion object{
        const val MUTE_STATUS:String = "com.raman.focustimerkt.mute_flag"
    }

    private val notificationTitle: String by lazy{ resources.getString(R.string.notification_title)}
    private val notificationManager: NotificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private val notificationId: Int = 192

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var timerResponseHandler: Handler? = null

    private var isStarted: Boolean = false
    private var isRunning: Boolean = false
    private var inLimbo: Boolean = false
    private var isCoolDown: Boolean = false

    private val vibrator: Vibrator by lazy { getSystemService(VIBRATOR_SERVICE) as Vibrator }
    private var isMuted = false
    private var player: MediaPlayer? = null

    private var timeRemaining: Long = 0
    private var totalTime: Long = 0
    private var intervalsRemaining: Int = -1
    private var repeatCounter: Int = 2
    private var intervalTime: Long = DEFAULT_INTERVAL_TIME
    private var coolDownTime: Long = DEFAULT_COOLDOWN_TIME
    private var percentageComplete: Float = 0f

    private val stateLock: Semaphore = Semaphore(1)

    private  fun sendTimerDataResponse(timerData: TimerData,what: Int) {
        Message().also {
            it.what = what
            it.obj = timerData
            timerResponseHandler?.sendMessage(it)
        }
    }

    inner class TimerHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                TIMER_START -> {
                    //set this in case times have changed
                    totalTime = intervalTime + coolDownTime
                    //fresh repeatCounter
                    //repeatCounter is decremented everytime cooldown is completed
                    if(intervalsRemaining == -1){
                        intervalsRemaining = repeatCounter
                    }
                    timeRemaining = if (!isCoolDown) intervalTime else coolDownTime

                    sendEmptyMessageDelayed(TIMER_TICK, TIME_DELTA)
                    updateState(started = true, running = true,false)
                    sendTimerDataResponse(TimerData(intervalsRemaining!!,totalTime, timeRemaining, 0f, isCoolDown,getTimeFormattedString(timeRemaining)), TIMER_START)
                }
                TIMER_TICK -> {
                    //every 100 ms
                    timeRemaining -= 100
                    percentageComplete = if (isCoolDown){ (coolDownTime - timeRemaining).toFloat() / coolDownTime } else{
                            (intervalTime - timeRemaining).toFloat() / intervalTime }
                    //call publish notification every second
                    //grab minutes,how to grab minutes => time div minute_ms = minutes
                    if (timeRemaining % SECOND_MS == 0L) {
                        val timeString = getTimeFormattedString(timeRemaining)
                        publishNotification(timeString)
                        sendTimerDataResponse(TimerData(timeString), TIMER_TICK)
                    }
                    //then grab
                    if (timeRemaining <= 0) {
                        //a countdown has completed we don't know which it is
                        //need to vibrate() the phone
                        updateState(started = true, running = false)
                        sendEmptyMessage(TIMER_COMPLETE)
                    } else {
                        sendEmptyMessageDelayed(TIMER_TICK, TIME_DELTA)
                    }
                }
                TIMER_RESUME -> {
                    //received request to resume timer
                    if (isStarted) {
                        this.sendEmptyMessageDelayed(TIMER_TICK, TIME_DELTA)
                        updateState(running = true)
                        sendTimerDataResponse(TimerData(intervalsRemaining,totalTime, timeRemaining, percentageComplete, isCoolDown), TIMER_RESUME)
                    } else {
                        //start the timer
                        this.sendEmptyMessage(TIMER_START)
                    }
                }
                TIMER_PAUSE -> {
                    //received request to pause timer
                    //stop timer
                    removeCallbacksAndMessages(null)
                    updateState(running = false)
                    //send signal back to main
                    msg.obj?.also {
                        //means pause and modify times
                        val timerSettingData = it as Triple<Int?,Long?, Long?>
                        if(timerSettingData.second != null){
                            intervalTime = timerSettingData.second!!
                        }
                        if(timerSettingData.third != null){
                            coolDownTime = timerSettingData.third!!
                        }
                        if(timerSettingData.first != null){
                            repeatCounter = timerSettingData.first!!
                        }
                        editor.putLong(INTERVAL_TIME,intervalTime)
                        editor.putLong(COOLDOWN_TIME,coolDownTime)
                        editor.putInt(REPEAT_COUNTER,repeatCounter)
                        editor.apply()
                        //states need to be reset
                        sendEmptyMessage(TIMER_RESET)
                    }
                    timerResponseHandler?.sendEmptyMessage(TIMER_PAUSE)
                }
                TIMER_COMPLETE -> {
                    timerCompleteNotify()
                    //if cooldown complete then an entire interval has been completed
                    sendTimerDataResponse(TimerData(
                        repeatCounter= if (isCoolDown) intervalsRemaining - 1 else intervalsRemaining,totalTime,
                        timeRemaining= 0,
                        percentageComplete = 1f,
                        isCoolDown = isCoolDown,
                        timeString = if (!isCoolDown) getTimeFormattedString(coolDownTime) else getTimeFormattedString(intervalTime)),TIMER_COMPLETE)

                    if (!isCoolDown) {
                        //start the cooldown timer in 1 second
                        sendEmptyMessageDelayed(TIMER_START,SECOND_MS)
                        updateState(limbo = true)//now in limbo for 1 second
                    } else {
                        percentageComplete = 0f
                        updateState(started = false,running = false)
                        notificationManager.cancel(notificationId)
                        removeCallbacksAndMessages(null)
                        intervalsRemaining -= 1
                        if(intervalsRemaining == 0){
                            intervalsRemaining = -1
                        }else{
                            //restart the timer
                            Log.i("BackgroundTimerService","Restart Timer!!")
                            //start the cooldown timer in 1 second
                            sendEmptyMessageDelayed(TIMER_START,SECOND_MS)
                            updateState(limbo = true)//now in limbo for 1 second
                        }

                    }
                    //send message back to result handler
                    isCoolDown = !isCoolDown
                }
                TIMER_STATUS -> {
                    sendTimerDataResponse(TimerData(
                        if(intervalsRemaining == -1) repeatCounter else intervalsRemaining,totalTime, timeRemaining, percentageComplete, isCoolDown,isRunning).also {
                        if (timeRemaining == 0L){
                            it.timeString = getTimeFormattedString(intervalTime)
                        }else{
                            it.timeString = getTimeFormattedString(timeRemaining)
                        }
                    },TIMER_STATUS)
                }
                TIMER_RESET -> {
                    //stop the timer
                    removeCallbacksAndMessages(null)
                    updateState(started = false,running = false, limbo = true)
                    totalTime = intervalTime + coolDownTime
                    timeRemaining = intervalTime
                    intervalsRemaining = -1
                    //since the timer is reset
                    //set completion to 100 so radial resets
                    timerResponseHandler?.sendEmptyMessage(TIMER_PAUSE)
                    sendTimerDataResponse(TimerData(
                        intervalsRemaining,totalTime,timeRemaining,percentageComplete, isCoolDown).also{
                        it.timeString = getTimeFormattedString(intervalTime)
                    },TIMER_RESET)
                    publishNotification(getTimeFormattedString(timeRemaining))
                    isCoolDown = false
                    percentageComplete = 0f
                    //good little safety
                    updateState(limbo = false)
                }
                TIMER_SET_REPEAT_COUNTER -> {
                    val newIntervals = msg.obj as Int
                    intervalsRemaining = newIntervals
                    repeatCounter = newIntervals
                    //nothing else has changed no times or states need ot be changed
                    //since we can only change settings of a paused timer
                }
                else -> {
                    super.handleMessage(msg)
                }
            }
        }
    }

    fun timerCompleteNotify(){
        //vibrate phone
        vibrate()
        //play sound
        playTimerCompleteSound()
    }

    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(SECOND_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun playTimerCompleteSound() {
        stateLock.acquire()
        val muteSound = isMuted
        stateLock.release()
        //sound only plays if not muted
        if(muteSound) return
        player?.start()
    }

    fun updateState(started: Boolean = isStarted,running: Boolean = isRunning, limbo: Boolean = inLimbo){
        stateLock.acquire()
        this.isStarted = started
        this.isRunning = running
        this.inLimbo = limbo
        stateLock.release()
    }

    inner class TimerServiceBinder : Binder() {

        val intervalTime:Long get() = this@BackgroundTimerService.intervalTime
        val coolDownTime:Long get() = this@BackgroundTimerService.coolDownTime
        val repeatCounter: Int get() = this@BackgroundTimerService.repeatCounter

        fun setTimerResponseHandler(handler: Handler) {
            this@BackgroundTimerService.timerResponseHandler = handler
            //send timer data back to main from here
            this@BackgroundTimerService.handler?.sendEmptyMessage(TIMER_STATUS)
        }

        fun setTime(repeatCounter: Int?,intervalTime: Long?,coolDownTime: Long?){
            //set time so if the timer is running it must be stopped
            this@BackgroundTimerService.handler?.sendMessage(handler!!.obtainMessage().also {
                it.what = TIMER_PAUSE
                it.obj = Triple(repeatCounter,intervalTime,coolDownTime)
            })
        }

        fun timerStartToggle() {
            getState().also {
                if(!it.inLimbo){
                    if (it.isRunning){
                        this@BackgroundTimerService.pauseTimer()
                    }else {
                        this@BackgroundTimerService.resumeTimer()
                    }
                }
            }
        }

        fun getState(): TimerData {
            stateLock.acquire()
            val state =  TimerData(isStarted,isRunning,inLimbo)
            stateLock.release()
            return state
        }

        fun resetTimer(): Boolean {
            //this message is never received since all messages are cleared in timer pause
            getState().also {
                if(it.isRunning || it.isStarted) {
                    //do what also?
                    //i need to prevent or toss out
                    //any pause or play event
                    this@BackgroundTimerService.handler?.sendEmptyMessage(TIMER_RESET)
                    return true
                }
            }
            return false
        }

        fun toggleSound(): Boolean? {
            if(stateLock.tryAcquire(1, TimeUnit.SECONDS)) {
                this@BackgroundTimerService.isMuted = !this@BackgroundTimerService.isMuted
                val muteStatus = this@BackgroundTimerService.isMuted
                stateLock.release()
                return muteStatus
            }
            return null
        }

        fun setRepeatCounter(repeatCounter: Int) {
            //guaranteed to be paused
            this@BackgroundTimerService.handler?.sendMessage(Message().also {
                //times do not need to be reset just the repeatCounter
                it.what = TIMER_SET_REPEAT_COUNTER
                it.obj = repeatCounter
            })
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        //publish the notification when this shit starts
        val intent0 = Intent(this,MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this,0,intent0, FLAG_CANCEL_CURRENT)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_timer_24)
            .setContentTitle(notificationTitle)
            .setContentText("Timer Ready.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)

        startForeground(notificationId,builder.build())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i("BackgroundTimerService","Binding to Service!")
        return TimerServiceBinder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i("BackgroundTimerService","Unbinding from Service")
        return super.onUnbind(intent)
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun publishNotification(time: String) {
        //publish the notification when this shit starts
        val intent = Intent(this,MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this,0,intent, FLAG_CANCEL_CURRENT)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_timer_24)
            .setContentTitle(notificationTitle)
            .setContentText(time)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
        notificationManager.notify(notificationId,builder.build())
    }



    private val sharedPreferences: SharedPreferences by lazy{ getSharedPreferences(getString(R.string.preference_name), Context.MODE_PRIVATE) }
    private val editor: SharedPreferences.Editor by lazy { sharedPreferences.edit() }

    override fun onCreate() {
        super.onCreate()

        intervalTime = sharedPreferences.getLong(INTERVAL_TIME, DEFAULT_INTERVAL_TIME)
        coolDownTime = sharedPreferences.getLong(COOLDOWN_TIME, DEFAULT_COOLDOWN_TIME)
        repeatCounter = sharedPreferences.getInt(REPEAT_COUNTER,1)
        isMuted = sharedPreferences.getBoolean(MUTE_STATUS,false)

        Log.i("BackgroundTimerService", "Creating Service")
        handlerThread = HandlerThread("FocusTimerThread")
        handlerThread?.start()
        handler = TimerHandler(handlerThread!!.looper)

        player = MediaPlayer.create(applicationContext, R.raw.timer_beep)

        ServiceStateManager.setServiceInitState(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("BackgroundTimerService", "Destroying Service")

        ServiceStateManager.setServiceInitState(false)
        handler?.removeCallbacksAndMessages(null)
        handler = null

        handlerThread?.quit()
        handlerThread = null

        player?.release()
        player = null
    }

    fun resumeTimer() {
        handler?.sendEmptyMessage(TIMER_RESUME)
    }

    fun pauseTimer() {
        handler?.sendEmptyMessage(TIMER_PAUSE)
    }
}