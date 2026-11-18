package com.android.server.wm

import android.view.animation.Animation
import android.view.animation.Interpolator

class ScreenRotationAnimationImpl {
    fun createRotation180Enter(): Animation = error("native animation supplied by the test")
    fun createRotation180Exit(): Animation = error("native animation supplied by the test")
    fun createRotationEnterAnimation(width: Int, height: Int, start: Int, end: Int): Animation =
        error("native animation supplied by the test")
    fun createRotationExitAnimation(width: Int, height: Int, start: Int, end: Int): Animation =
        error("native animation supplied by the test")

    fun createRotationExitAnimation(): Animation = error("unrelated overload")
}

class AppTransitionInjector {
    companion object {
        @JvmField val QUART_EASE_OUT_INTERPOLATOR = Interpolator { it }
    }
}
