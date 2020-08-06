package com.example.wikipedialist.util

import android.util.Log
import com.example.wikipedialist.BuildConfig

object MLog {
    var displayLog = BuildConfig.DEBUG
    private val mTag: String? = "MLog "
    fun i(tag: String, message: String) {
        if (displayLog) Log.i(mTag + tag, message)
    }

    fun w(tag: String, message: String) {
        if (displayLog) Log.w(mTag + tag, message)
    }

    fun w(tag: String?, message: String?, e: Exception?) {
        if (displayLog) Log.w(mTag + tag, message, e)
    }

    fun w(tag: String?, message: String?, t: Throwable?) {
        if (displayLog) Log.w(mTag + tag, message, t)
    }

    fun d(tag: String, message: String) {
        if (displayLog) Log.d(mTag + tag, message)
    }

    fun e(tag: String, message: String) {
        if (displayLog) Log.e(mTag + tag, message)
    }

    fun e(tag: String?, message: String?, t: Throwable?) {
        if (displayLog) Log.e(mTag + tag, message, t)
    }

    fun v(tag: String, message: String) {
        if (displayLog) Log.v(mTag + tag, message)
    }
}