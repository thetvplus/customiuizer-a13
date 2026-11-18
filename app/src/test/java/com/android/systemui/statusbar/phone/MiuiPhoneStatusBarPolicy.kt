package com.android.systemui.statusbar.phone

import android.media.AudioManager

open class PhoneStatusBarPolicy {
    @JvmField var mMuteVisible = false
}

class MiuiPhoneStatusBarPolicy : PhoneStatusBarPolicy() {
    fun updateVolumeZen() {
        mMuteVisible = ringerMode == AudioManager.RINGER_MODE_SILENT ||
            ringerMode == AudioManager.RINGER_MODE_VIBRATE
    }

    var ringerMode = AudioManager.RINGER_MODE_NORMAL
}
