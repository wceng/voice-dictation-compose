package com.wceng.dictation.platform

/**
 * 桌面通知服务：把通知转发到系统托盘气泡。
 * 托盘在 Koin 容器初始化后才创建，因此使用可变的接收器——
 * 托盘就绪后通过 [attach] 注入真正的显示函数；未就绪时仅打印日志。
 */
class DesktopNotificationService : NotificationService {

    @Volatile
    private var sink: ((title: String, message: String, isError: Boolean) -> Unit)? = null

    /** 托盘就绪后绑定真正的显示实现 */
    fun attach(sink: (title: String, message: String, isError: Boolean) -> Unit) {
        this.sink = sink
    }

    override fun show(title: String, message: String, isError: Boolean) {
        val s = sink
        if (s != null) {
            s(title, message, isError)
        } else {
            // 托盘未就绪:输出到控制台,不丢失事件
            println("[Notify] $title: $message")
        }
    }
}