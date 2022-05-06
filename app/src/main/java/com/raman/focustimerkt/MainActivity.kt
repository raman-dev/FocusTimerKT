package com.raman.focustimerkt

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.*
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.*
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.raman.focustimerkt.BackgroundTimerService.Companion.MUTE_STATUS
import com.raman.focustimerkt.TimerData.Companion.COOLDOWN_TIME
import com.raman.focustimerkt.TimerData.Companion.DEFAULT_COOLDOWN_TIME
import com.raman.focustimerkt.TimerData.Companion.DEFAULT_INTERVAL_TIME
import com.raman.focustimerkt.TimerData.Companion.INTERVAL_TIME
import com.raman.focustimerkt.TimerData.Companion.MINUTE_MS
import com.raman.focustimerkt.TimerData.Companion.REPEAT_COUNTER
import com.raman.focustimerkt.TimerData.Companion.SECOND_MS
import com.raman.focustimerkt.TimerData.Companion.TIMER_COMPLETE
import com.raman.focustimerkt.TimerData.Companion.TIMER_PAUSE
import com.raman.focustimerkt.TimerData.Companion.TIMER_RESET
import com.raman.focustimerkt.TimerData.Companion.TIMER_RESUME
import com.raman.focustimerkt.TimerData.Companion.TIMER_START
import com.raman.focustimerkt.TimerData.Companion.TIMER_STATUS
import com.raman.focustimerkt.TimerData.Companion.TIMER_TICK
import com.raman.focustimerkt.TimerData.Companion.getMinutes
import com.raman.focustimerkt.TimerData.Companion.getSeconds
import com.raman.focustimerkt.TimerData.Companion.getTimeFormattedString


class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG: String = "MainActivity"
        const val FOCUS_MODE: Int = 1
        const val COOLDOWN_MODE: Int = 2
    }

    private lateinit var editor: SharedPreferences.Editor
    private lateinit var sharedPreferences: SharedPreferences
    private var resumeTimerAfterInit: Boolean = false
    private var isResetting: Boolean = false
    private var timerCurrentState: TimerData? = null

    private lateinit var radialTimerDisplay: RadialTimeDisplay
    private lateinit var timerControlButton: FloatingActionButton
    private lateinit var timerControlButtonLabel: TextView
    private lateinit var toggleSoundButton: FloatingActionButton
    private lateinit var pauseDrawable: Drawable
    private lateinit var playDrawable: Drawable
    private lateinit var soundOnDrawable: Drawable
    private lateinit var soundOffDrawable: Drawable
    private lateinit var timerTextDisplay: TextView
    private lateinit var commitTimerSettingButton: Button

    private lateinit var minuteNumberPicker: NumberPicker
    private lateinit var secondsNumberPicker: NumberPicker
    private lateinit var coolDownMinuteNumberPicker: NumberPicker
    private lateinit var coolDownSecondNumberPicker: NumberPicker
    private lateinit var repetitionTextInputLayout: TextInputLayout
    private lateinit var repetitionTextInputEditText: TextInputEditText

    private var modeSelect = FOCUS_MODE

    private var intervalTime: Long = 0L
    private var coolDownTime: Long = 0L
    private var repeatCounter: Int = 1

    private lateinit var timerResponseHandler: TimerResponseHandler
    private var timerService: BackgroundTimerService.TimerServiceBinder? = null
    private var serviceBound = false
    private val serviceConnection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, serviceBinder: IBinder) {
            serviceBound = true
            timerService = (serviceBinder as BackgroundTimerService.TimerServiceBinder)
            //need to grab time immediately
            timerService?.setTimerResponseHandler(timerResponseHandler)
            intervalTime = timerService!!.intervalTime
            coolDownTime = timerService!!.coolDownTime
            repeatCounter = timerService!!.repeatCounter
            Log.i(TAG,"Service Bound!")
            updateTimePickerValues()
            repetitionTextInputEditText.setText(getEditTextFormattedString("$repeatCounter"))

            if (resumeTimerAfterInit) {
                timerService?.timerStartToggle()
                //since the timer service is now created
                resumeTimerAfterInit = false
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
            //Log.i(TAG,"Service Unbound!")
        }
    }

    private val bottomSheetCallback = object : BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            when (newState) {
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    ////Log.i(TAG,"Settings Open")
                    //the spinners were changed without committing
                    updateTimePickerValues()
                }
                BottomSheetBehavior.STATE_EXPANDED -> {
                    //Log.i(TAG,"Settings Closed")
                }
                else -> {
                    return
                }
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
        }

    }

    private fun updateTimePickerValues() {
        minuteNumberPicker.value = getMinutes(intervalTime)
        secondsNumberPicker.value = getSeconds(intervalTime)
        coolDownMinuteNumberPicker.value = getMinutes(coolDownTime)
        coolDownSecondNumberPicker.value = getSeconds(coolDownTime)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        /*
         interval timer
         run while app is closed
         pause play buttons
         show current tim
         animation while time is ticking down

         use a single timer and query it for updates?
         yes to maintain synchronization
         i want a custom view to show the countdown
         DBC20003733282

         (289) 946-2685
         647 389 6589
         1 866 856 9132
         6 guesses at a  6 letter word? or variable letter
         correct guess  game over
         incorrect guess num guess -= 1
         incorrect word with letter in correct word at wrong position is yellow
         incorrect word with letter in correct position isz green

        */


        initViews()

        val bottomSheet = BottomSheetBehavior.from(findViewById(R.id.bottomSheet))
        bottomSheet.addBottomSheetCallback(bottomSheetCallback)

        commitTimerSettingButton = findViewById(R.id.commitTimerSettingButton)
        commitTimerSettingButton.setOnClickListener {
            //grab all setting values and push to timer
            val newIntervalTime =
                minuteNumberPicker.value * MINUTE_MS + secondsNumberPicker.value * SECOND_MS
            val newCoolDownTime =
                coolDownMinuteNumberPicker.value * MINUTE_MS + coolDownSecondNumberPicker.value * SECOND_MS

            //at least 1 time has changed
            if (newIntervalTime != intervalTime || newCoolDownTime != coolDownTime) {
                //timer needs to stop before this happens
                intervalTime = newIntervalTime
                coolDownTime = newCoolDownTime
                //since service is not started times won't be committed to the disk
                //so we must commit them ourselves
                if (timerService == null) {
                    editor.putLong(INTERVAL_TIME, intervalTime)
                    editor.putLong(COOLDOWN_TIME, coolDownTime)
                    editor.apply()
                    //we must also reflect the change in ui since
                    //timer will not send us a response that times have changed
                    timerTextDisplay.text = getTimeFormattedString(intervalTime)
                } else {
                    timerService!!.setTime(null, intervalTime, coolDownTime)
                }
            }
        }

        (findViewById<FloatingActionButton>(R.id.resetTimerButton)).also {
            it.setOnClickListener {
                //reset the timer
                isResetting = timerService?.resetTimer() ?: isResetting
            }
        }

        toggleSoundButton = (findViewById<FloatingActionButton>(R.id.soundToggleButton)).also {
            it.setOnClickListener {
                //should flip the status
                //if timer service is not yet initialized grab the saved mute status
                //and then flip since the user has toggle the mute button
                val muteStatus: Boolean =
                    timerService?.toggleSound() ?: !sharedPreferences.getBoolean(MUTE_STATUS, false)
                if (muteStatus) {
                    //change the drawable
                    toggleSoundButton.setImageDrawable(soundOffDrawable)
                } else {
                    toggleSoundButton.setImageDrawable(soundOnDrawable)
                }
                editor.putBoolean(MUTE_STATUS, muteStatus)
                editor.commit()
            }
        }

        timerResponseHandler = TimerResponseHandler(Looper.getMainLooper())
        initResources()
        //don't need to format while editing need to format when done editing
        repetitionTextInputEditText.setOnFocusChangeListener { v, hasFocus ->
            //Log.i(TAG, "EditText focus changed!")
            if (!hasFocus) {
                hideKeyboard(v)
                //format the text if it has changed
                setRepeatCounter()
            }
        }

        repetitionTextInputEditText.setOnEditorActionListener { v, actionId, event ->
            //Log.i("EditText", "actionId => $actionId")
            //click back or next or whatever
            if (actionId == EditorInfo.IME_ACTION_NEXT || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                hideKeyboard(v)
                //remove focus from edit text
                repetitionTextInputLayout.clearFocus()
                //propagate value everytime focus changes
                //can only change value when not running
                setRepeatCounter()
                true
            } else {
                false
            }
        }

        timerControlButton.setOnClickListener {
            if (timerService != null) {
                //timer interaction results in a pause or resume
                //since it is not null we can check if it is resetting
                if (!isResetting) timerService?.timerStartToggle()
            } else {
                //kinda wanna run this in a coroutine
                connectAndBindTimerService()
                resumeTimerAfterInit = true
            }
        }

        sharedPreferences =
            getSharedPreferences(getString(R.string.preference_name), Context.MODE_PRIVATE)
        editor = sharedPreferences.edit()

        intervalTime = sharedPreferences.getLong(INTERVAL_TIME, DEFAULT_INTERVAL_TIME)
        coolDownTime = sharedPreferences.getLong(COOLDOWN_TIME, DEFAULT_COOLDOWN_TIME)
        repeatCounter = sharedPreferences.getInt(REPEAT_COUNTER, 1)

        sharedPreferences.getBoolean(MUTE_STATUS, false).also {
            if (!it) {
                toggleSoundButton.setImageDrawable(soundOnDrawable)
            } else {
                toggleSoundButton.setImageDrawable(soundOffDrawable)
            }
        }

        timerTextDisplay.text = getTimeFormattedString(intervalTime)
        updateTimePickerValues()
        repetitionTextInputEditText.setText(getEditTextFormattedString("$repeatCounter"))

    }

    private fun initViews() {
        val configureNumberPicker = { numberPicker: NumberPicker, isMinutePicker: Boolean -> numberPicker.maxValue = if (isMinutePicker) 45 else 59
            numberPicker.setFormatter { value ->
                if (value < 10) {
                    "0$value"
                } else {
                    "$value"
                }
            }
        }

        minuteNumberPicker = findViewById<NumberPicker>(R.id.minuteNumberPicker).also { configureNumberPicker(it, true) }
        secondsNumberPicker = findViewById<NumberPicker>(R.id.secondsNumberPicker).also { configureNumberPicker(it, false) }
        coolDownMinuteNumberPicker = findViewById<NumberPicker>(R.id.coolDownMinuteNumberPicker).also { configureNumberPicker(it, true) }
        coolDownSecondNumberPicker = findViewById<NumberPicker>(R.id.coolDownSecondsNumberPicker).also { configureNumberPicker(it, false) }

        repetitionTextInputLayout = findViewById(R.id.repTextInputLayout)
        repetitionTextInputEditText = findViewById(R.id.repEditText)

        radialTimerDisplay = findViewById(R.id.radialTimerDisplay)
        timerControlButton = findViewById(R.id.timerControlButton)
        timerControlButtonLabel = findViewById(R.id.timerControlButtonLabel)
        timerTextDisplay = findViewById(R.id.timerTextDisplay)
        //exits early when cooldown complete
    }

    //observe edits in rep edit text view
    //
//    these don't catch the back button when keyboard is open
//    override fun onBackPressed()
//    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean

    private fun initResources() {
        pauseDrawable = ResourcesCompat.getDrawable(resources, R.drawable.pause_24, null)!!
        playDrawable = ResourcesCompat.getDrawable(resources, R.drawable.play_arrow_24, null)!!
        soundOnDrawable = ResourcesCompat.getDrawable(resources, R.drawable.sound_on_24, null)!!
        soundOffDrawable = ResourcesCompat.getDrawable(resources, R.drawable.sound_off_24, null)!!
    }

    /**
     * @return Return the value in the repetition counter guaranteed to be within 1...99
     */
    private fun getEditTextValue(): Int{
        val text = repetitionTextInputEditText.text.toString()
        //if value is 0 or non existent then value
        //is as if unchanged
        if(text.isEmpty() || text.toInt() == 0){
            return repeatCounter
        }
        return text.toInt()
    }

    /**
     * @param text string guaranteed to be a string from 1...99
     */
    private fun getEditTextFormattedString(text: String): String {
        if (text.length == 1) {
            return "0$text"
        }
        return text
    }

    private fun setRepeatCounter(){
        Log.i(TAG,"Setting Repeat Counter")
        val value = getEditTextValue()
        //display the text in the repcounter
        //can't exit early here since we have
        //to reset text if it is completely gone
        repetitionTextInputEditText.setText(
            getEditTextFormattedString(value.toString()))
        //send value to the timer service if not null
        if(repeatCounter == value) return //exit early if value hasn't changed
        repeatCounter = value
        if(timerService != null) {
            timerService!!.setRepeatCounter(repeatCounter)
        }else{
            //commit to disk if timer service not available
            editor.putInt(REPEAT_COUNTER,repeatCounter)
            editor.apply()
        }
    }

    private fun hideKeyboard(view: View) {
        val inputMethodManager: InputMethodManager =
            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    inner class TimerResponseHandler(looper: Looper) : Handler(looper) {
        @SuppressLint("SetTextI18n")
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                TIMER_START -> {
                    //start the animation here
                    repetitionTextInputEditText.isEnabled = false
                    repetitionTextInputLayout.isEnabled = false

                    val timerData: TimerData = msg.obj as TimerData

                    Log.i(TAG, "Timer Started")
                    //Log.i(TAG, timerData.toString())

                    startRadialAnimation(timerData)
                    timerControlButton.setImageDrawable(pauseDrawable)
                    timerControlButtonLabel.text = "pause"
                    timerTextDisplay.text = timerData.timeString
                }
                TIMER_RESUME -> {
                    //resume any animation
                    (msg.obj as TimerData).also {
                        radialTimerDisplay.resumeAnimation(
                            it.timeRemaining,
                            it.percentageComplete,
                            it.isCoolDown
                        )
                        //Log.i(TAG, it.toString())
                    }
                    timerControlButton.setImageDrawable(pauseDrawable)
                    timerControlButtonLabel.text = "pause"

                    repetitionTextInputEditText.isEnabled = false
                    repetitionTextInputLayout.isEnabled = false

                    Log.i(TAG, "Timer Resumed")
                }
                TIMER_TICK -> {
                    val timeString = (msg.obj as TimerData).timeString
                    //update display
                    timerTextDisplay.text = timeString
                }
                TIMER_PAUSE -> {
                    repetitionTextInputEditText.isEnabled = true
                    repetitionTextInputLayout.isEnabled = true

                    radialTimerDisplay.pauseAnimation()
                    timerControlButton.setImageDrawable(playDrawable)
                    timerControlButtonLabel.text = "play"
                    Log.i(TAG, "Timer Paused")
                }

                TIMER_COMPLETE -> {
                    //whenever the timer is complete
                    Log.i(TAG, "Timer Complete")
                    Log.i(TAG, (msg.obj as TimerData).toString())
                    (msg.obj as TimerData).also {
                        if (it.isCoolDown) {
                            if (it.repeatCounter == 0) {
                                //no more repetitions
                                //restore ui elements
                                repetitionTextInputEditText.setText(getEditTextFormattedString("0"))
                                timerControlButton.setImageDrawable(playDrawable)
                                timerControlButtonLabel.text = "play"
                                //restore ui after 1 second vibration and/or sound
                                this.postDelayed({
                                    repetitionTextInputEditText.isEnabled = true
                                    repetitionTextInputLayout.isEnabled = true
                                    timerTextDisplay.text = it.timeString
                                    repetitionTextInputEditText.setText(getEditTextFormattedString("$repeatCounter"))
                                }, 1000)
                            } else {
                                repetitionTextInputEditText.setText(getEditTextFormattedString("${it.repeatCounter}"))
                            }

                        }
                    }
                }
                TIMER_STATUS -> {
                    Log.i(TAG, "Timer Status!")
                    val timerData = msg.obj as TimerData
                    Log.i(TAG,"completion => " + timerData.percentageComplete)
                    Log.i(TAG,"running.state => "+timerData.isRunning)
                    //Log.i(TAG,"repeatCounter => ${timerData.repeatCounter}")
                    //status is requested when?
                    //status is requested when connecting to a running or paused timerService
                    //or when initially starting the timerService
                    //when requested while running
                    repetitionTextInputEditText.setText(
                        getEditTextFormattedString("${timerData.repeatCounter}"))
                    if (timerData.isRunning) {
                        //timer is running need to set ui elements to correct values
                        //repeat counter needs to be set to the correct value
                        //need to start the animation

                        startRadialAnimation(timerData)
                        timerControlButton.setImageDrawable(pauseDrawable)
                        timerControlButtonLabel.text = "pause"
                    } else {
                        //not running then change the radial to the percentage that is complete
                        setRadialCompletion(timerData)
                        timerControlButton.setImageDrawable(playDrawable)
                        timerControlButtonLabel.text = "play"
                    }
                    timerTextDisplay.text = timerData.timeString
                }
                TIMER_RESET -> {
                    Log.i(TAG, "Timer Reset")

                    repetitionTextInputLayout.isEnabled = true
                    repetitionTextInputEditText.isEnabled = true

                    (msg.obj as TimerData).also {
                        if (it.isCoolDown) {
                            radialTimerDisplay.startTickUpAnimation(500, it.percentageComplete)
                        } else {
                            radialTimerDisplay.startTickUpAnimation(500, 1 - it.percentageComplete)
                        }
                        timerTextDisplay.text = it.timeString
                        repetitionTextInputEditText.setText(getEditTextFormattedString("$repeatCounter"))
                    }
                }
                else -> {
                    super.handleMessage(msg)
                }
            }
        }
    }

    private fun setRadialCompletion(timerData: TimerData) {
        //Log.i(TAG, "Setting radial completion!")
        //should accept a completion
        if (timerData.isCoolDown) {
            radialTimerDisplay.setArcTickUpCompletion(timerData.percentageComplete)
        } else {
            radialTimerDisplay.setArcTickDownCompletion(timerData.percentageComplete)
        }
    }

    private fun startRadialAnimation(timerData: TimerData) {
        if (timerData.isCoolDown) {
            radialTimerDisplay.startTickUpAnimation(timerData.timeRemaining, timerData.percentageComplete)
        } else {
            radialTimerDisplay.startTickDownAnimation(timerData.timeRemaining, timerData.percentageComplete)
        }
    }

    //start timer service when?
    //user press play
    //to start timer so start timer service
    private fun connectAndBindTimerService() {
        if (!ServiceStateManager.isInitialized()) {
            Intent(this, BackgroundTimerService::class.java).also {
                Log.i(TAG, "Starting Service!")
                startForegroundService(it)
            }
            Intent(this, BackgroundTimerService::class.java).also {
                bindService(it, serviceConnection, 0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //Log.i(TAG, "ON_RESUME")
        volumeControlStream = AudioManager.STREAM_MUSIC
        //need to bind to the service everytime
        if (ServiceStateManager.isInitialized()) {
            if (timerService == null) {
                Intent(this, BackgroundTimerService::class.java).also {
                    bindService(it, serviceConnection, 0)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        //Log.i(TAG, "ON_PAUSE")
        //get state before closing
        timerService?.also {
            timerCurrentState = it.getState()
        }
        if (serviceBound) {
            unbindService(serviceConnection)
        }
        serviceBound = false
        timerService = null
    }

    override fun onStop() {
        super.onStop()

        if (timerCurrentState != null) {
            if (!timerCurrentState!!.isStarted && !timerCurrentState!!.inLimbo) {
                Intent(this, BackgroundTimerService::class.java).also {
//                    Log.i(TAG,"Stopping Service")
                    stopService(it)
                }
            }
        }
    }

    //need this one off method for reset animation signal
    val resetListener: Animator.AnimatorListener = object : SimpleAnimatorListener() {
        override fun onAnimationEnd(animation: Animator?, isReverse: Boolean) {
            isResetting = false
        }
    }
}