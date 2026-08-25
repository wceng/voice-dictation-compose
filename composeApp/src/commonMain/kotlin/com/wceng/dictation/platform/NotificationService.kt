package com.wceng.dictation.platform

/**
 * 系统通知/Toast 抽象。
 * 桌面端基于 TrayIcon.displayMessage 或 Toast 窗口；
 * Android 基于 NotificationManager / Toast；
 * iOS 基于 UNUserNotificationCenter / 本地推送。
 */
interface NotificationService {
    /** 显示通知，isError 为 true 时使用错误样式 */
    fun show(title: String, message: String, isError: Boolean)
}