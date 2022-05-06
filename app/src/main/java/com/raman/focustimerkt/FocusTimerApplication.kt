package com.raman.focustimerkt

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class FocusTimerApplication: Application() {

    companion object{
        const val CHANNEL_ID:String = "com.raman.focustimerkt.notification_channel"
    }


    override fun onCreate() {
        super.onCreate()
        // Create the NotificationChannel
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
        mChannel.description = descriptionText
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }
}