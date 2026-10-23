package com.android.server.vibrator

import android.os.CombinedVibration
import android.os.IBinder
import android.os.VibrationAttributes

class VibratorManagerService {
    var readyCalls = 0
    var readyFailure: RuntimeException? = null
    var vibrateCalls = 0
    var vibrationFailure: RuntimeException? = null
    var lastArgs: List<Any?> = emptyList()

    fun systemReady() {
        readyCalls++
        readyFailure?.let { throw it }
    }

    fun vibrate(uid: Int, pkg: String?, effect: CombinedVibration?, attributes: VibrationAttributes?, reason: String?, token: IBinder?) {
        vibrateCalls++
        lastArgs = listOf(uid, pkg, effect, attributes, reason, token)
        vibrationFailure?.let { throw it }
    }

    // A14's extra deviceId is deliberately present to prove that it is never hooked on A13.
    fun vibrate(uid: Int, deviceId: Int, pkg: String?, effect: CombinedVibration?, attributes: VibrationAttributes?, reason: String?, token: IBinder?) {
        vibrateCalls++
        lastArgs = listOf(uid, deviceId, pkg, effect, attributes, reason, token)
    }
}
