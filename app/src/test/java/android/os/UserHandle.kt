package android.os

// Functional API 33 shadow: the SDK's mockable jar returns null for this factory.
class UserHandle(val identifier: Int) {
    companion object {
        @JvmStatic fun getUserHandleForUid(uid: Int): UserHandle = UserHandle(getUserId(uid))
        @JvmStatic fun getUserId(uid: Int): Int = uid / 100000
        @JvmField val CURRENT = UserHandle(-2)
        @JvmField val ALL = UserHandle(-1)
        @JvmField val SYSTEM = UserHandle(0)
    }
}
