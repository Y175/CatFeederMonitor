package com.example.catfeedermonitor.logic

import android.util.Log
import com.example.catfeedermonitor.data.DebugLog
import com.example.catfeedermonitor.data.DebugLogDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LogManager(private val dao: DebugLogDao) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun log(level: String, tag: String, message: String) {
        // Also log to Logcat
        when (level) {
            "INFO" -> Log.i(tag, message)
            "WARN" -> Log.w(tag, message)
            "ERROR" -> Log.e(tag, message)
            else -> Log.d(tag, message)
        }

        scope.launch {
            dao.insert(
                DebugLog(
                    timestamp = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = message
                )
            )
        }
    }

    fun info(tag: String, message: String) = log("INFO", tag, message)
    fun warn(tag: String, message: String) = log("WARN", tag, message)
    fun error(tag: String, message: String) = log("ERROR", tag, message)
}
