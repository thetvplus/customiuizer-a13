package com.android.server.power

class PowerGroup

class PowerManagerService {
    fun readConfigurationLocked() {}
    fun setStayOnSettingInternal(value: Int) {}
    private fun wakePowerGroupLocked(group: PowerGroup, time: Long, reasonCode: Int, reason: String, uid: Int, pkg: String, opUid: Int) {}
}
