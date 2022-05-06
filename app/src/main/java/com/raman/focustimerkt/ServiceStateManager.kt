package com.raman.focustimerkt

import java.util.concurrent.Semaphore

object ServiceStateManager {

    private val stateLock: Semaphore = Semaphore(1)
    private var serviceInitialized: Boolean = false

    fun isInitialized(): Boolean{
        stateLock.acquire()
        return serviceInitialized.also {
            stateLock.release()
        }
    }

    fun setServiceInitState(state: Boolean){
        stateLock.acquire()
        serviceInitialized = state
        stateLock.release()
    }
}