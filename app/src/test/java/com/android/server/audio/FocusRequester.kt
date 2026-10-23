package com.android.server.audio

/** Models the control-flow branches read from both A13 ROMs; no actual audio or Binder. */
class FocusRequester(val uid: Int = 10001) {
    var lossReceived = 0
    var lossWasNotified = false
    var parentCalls = 0
    var frameworkCalls = 0
    var duckCalls = 0
    var fadeCalls = 0
    var gainCalls = 0
    var grantFlags = 0
    var sdk = 33
    var mustNotifyOwnerOnDuck = true
    var ignoreFocusRequest = false
    var frameworkResult = true
    var frameworkFailure: RuntimeException? = null
    val policyLossNotifications = mutableListOf<Boolean>()
    val dispatchedLosses = mutableListOf<Int>()
    var lastFrameworkArgs: List<Any?> = emptyList()
    var frameworkInvoker: (Int, FocusRequester, Boolean) -> Boolean = { loss, winner, force -> frameworkHandleFocusLoss(loss, winner, force) }

    fun handleFocusLoss(loss: Int, winner: FocusRequester?, forceDuck: Boolean) {
        parentCalls++
        if (loss == lossReceived) return
        val previous = lossReceived
        lossReceived = loss
        lossWasNotified = false
        if (!mustNotifyOwnerOnDuck && loss == -3 && grantFlags and 2 == 0) {
            policyLossNotifications.add(false)
            return
        }
        if (ignoreFocusRequest) {
            lossReceived = previous
            return
        }
        if (winner != null && frameworkInvoker(loss, winner, forceDuck)) {
            policyLossNotifications.add(false)
            return
        }
        policyLossNotifications.add(true)
        lossWasNotified = true
        dispatchedLosses.add(loss)
    }

    private fun frameworkHandleFocusLoss(loss: Int, winner: FocusRequester?, forceDuck: Boolean): Boolean {
        frameworkCalls++
        lastFrameworkArgs = listOf(loss, winner, forceDuck)
        frameworkFailure?.let { throw it }
        if (winner!!.uid == uid) return false
        if (loss == -3) {
            if (!forceDuck && (grantFlags and 2 != 0 || sdk <= 25)) return false
            duckCalls++
            return frameworkResult
        }
        if (loss == -1) {
            fadeCalls++
            return frameworkResult
        }
        return false
    }

    fun handleFocusGain(gain: Int) {
        gainCalls++
        lossReceived = 0
    }
}
