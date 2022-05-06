package com.raman.focustimerkt

data class TimerData(val repeatCounter: Int = 1,val totalTime: Long, val timeRemaining: Long, val percentageComplete: Float, val isCoolDown: Boolean) {

    var isStarted = false
    var isRunning = false
    var timeString = "00:00"
    var inLimbo = false

    constructor(repeatCounter: Int,totalTime: Long, timeRemaining: Long, percentageComplete: Float, isCoolDown: Boolean, isRunning: Boolean) : this(repeatCounter,totalTime, timeRemaining, percentageComplete, isCoolDown) {
        this.isRunning = isRunning
    }

    constructor(repeatCounter: Int,totalTime: Long, timeRemaining: Long, percentageComplete: Float, isCoolDown: Boolean, timeString: String) : this(repeatCounter,totalTime, timeRemaining, percentageComplete, isCoolDown) {
        this.timeString = timeString
    }

    constructor(timeString: String) : this(-1,0, 0, 0f, false) {
        this.timeString = timeString
    }

    constructor(started: Boolean, running: Boolean) : this(-1,0,0,0f,false){
        this.isStarted = started
        this.isRunning = running
    }

    constructor(started: Boolean, running: Boolean,limbo: Boolean) : this(-1,0,0,0f,false){
        this.isStarted = started
        this.isRunning = running
        this.inLimbo = limbo
    }

    override fun toString(): String{
        return "{\nrepeatCounter => $repeatCounter\n" +
                "totalTime => $totalTime\n" +
                "percentageComplete => $percentageComplete\n" +
                "timeRemaining => $timeRemaining\n" +
                "isCoolDown => $isCoolDown\n}"
    }

    companion object {
        const val TIMER_START = 0
        const val TIMER_TICK = 1
        const val TIMER_RESUME = 2
        const val TIMER_PAUSE = 3
        const val TIMER_COMPLETE = 4
        const val TIMER_STATUS = 5
        const val TIMER_RESET = 7
        const val TIMER_SET_REPEAT_COUNTER = 8

        const val TIME_DELTA: Long = 100
        const val DEFAULT_INTERVAL_TIME: Long = 10000
        const val DEFAULT_COOLDOWN_TIME: Long = 5000
        const val INTERVAL_TIME: String = "com.raman.focustimerkt.interval_time"
        const val COOLDOWN_TIME: String = "com.raman.focustimerkt.cooldown_time"
        const val REPEAT_COUNTER: String = "com.raman.focustimerkt.repeat_counter"

        const val SECOND_MS: Long = 1000
        const val MINUTE_MS: Long = 60 * SECOND_MS


        fun getTimeFormattedString(time: Long): String{
            val minutes: Long = time / MINUTE_MS
            var seconds: Long = (time % MINUTE_MS) / SECOND_MS
            val milliseconds: Long = time % SECOND_MS
            seconds = if(seconds == 0L && milliseconds > 0L)  1 else seconds

            val minuteString: String = if (minutes < 10) "0$minutes" else "$minutes"
            val secondsString: String = if (seconds < 10) "0$seconds" else "$seconds"

            return "$minuteString:$secondsString"
        }

        fun getMinutes (time: Long): Int {
            return (time / MINUTE_MS).toInt()
        }

        fun getSeconds (time: Long): Int{
            return ((time % MINUTE_MS )/ SECOND_MS).toInt()
        }
    }
}