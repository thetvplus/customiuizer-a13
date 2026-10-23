package com.android.server.display

import android.animation.ObjectAnimator

class DisplayPowerController {
    constructor() {}
    constructor(initial: Float) {}

    fun clampScreenBrightness(value: Float): Float = value

    // Minimal API 33 initialization ABI from both complete services.jar inputs.
    @JvmField var mColorFadeEnabled = false
    @JvmField var mColorFadeFadesConfig = false
    @JvmField var mColorFadeOffAnimator: ObjectAnimator? = null
    @JvmField var mColorFadeOnAnimator: ObjectAnimator? = null
    var animatorFactory: () -> ObjectAnimator? = { null }
    var initializeCalls = 0
    var initialScreenState: Int? = null
    var initializedWithFade = false
    var initializeFailure: Throwable? = null

    private fun initialize(screenState: Int) {
        initializeCalls++
        initialScreenState = screenState
        initializeFailure?.let { throw it }
        initializedWithFade = mColorFadeEnabled && mColorFadeFadesConfig
        if (mColorFadeEnabled) {
            mColorFadeOnAnimator = animatorFactory()?.setDuration(250)
            mColorFadeOffAnimator = animatorFactory()?.setDuration(100)
        }
    }
}
