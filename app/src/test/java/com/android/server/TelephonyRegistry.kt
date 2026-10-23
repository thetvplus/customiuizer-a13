package com.android.server

class TelephonyRegistry {
    fun notifyCallState(phoneId: Int, subId: Int, state: Int, incomingNumber: String) {}

    fun notifyCallStateForAllSubs(state: Int, incomingNumber: String) {}

    // API 33 ABI from both A13 services.jar samples; no legacy events bit mask.
    class Record(
        @JvmField var callingPackage: String?,
        @JvmField val eventList: Set<Int>,
        @JvmField var callbackPresent: Boolean = true
    ) {
        fun matchTelephonyCallbackEvent(event: Int): Boolean = callbackPresent && eventList.contains(event)
    }
}
