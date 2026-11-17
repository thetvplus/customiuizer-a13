package com.android.systemui.statusbar

import android.media.MediaMetadata
import android.media.session.MediaController

class NotificationMediaManager {
    @JvmField var mMediaController: MediaController? = null
    @JvmField var mMediaMetadata: MediaMetadata? = null

    fun updateMediaMetaData(metaDataChanged: Boolean, allowEnterAnimation: Boolean) {}
}
