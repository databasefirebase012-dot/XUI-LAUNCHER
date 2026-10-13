package com.movtery.zalithlauncher.feature.notice

import com.movtery.zalithlauncher.feature.log.Logging

/**
 * Upstream launcher notices are intentionally disabled in the XUI fork.
 * The callback is completed with null so callers can safely clear their notice UI.
 */
class CheckNewNotice {
    companion object {
        @JvmField
        var noticeInfo: NoticeInfo? = null

        @JvmStatic
        fun getNoticeInfo(): NoticeInfo? = noticeInfo

        @JvmStatic
        fun checkNewNotice(listener: CheckNoticeListener) {
            noticeInfo = null
            Logging.i("XUI Notice", "Upstream launcher notices are disabled.")
            listener.onSuccessful(null)
        }
    }
}