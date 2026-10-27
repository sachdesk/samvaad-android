package com.samvaads.widget

/** Called on the main thread when the number of unread agent replies changes. */
fun interface UnreadListener {
    fun onUnreadChange(count: Int)
}
