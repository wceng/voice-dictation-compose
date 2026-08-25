package com.wceng.dictation.platform

import kotlin.system.exitProcess

/**
 * 崩溃 fail-fast:任何线程的未捕获异常一律立即终止进程。
 *
 * 背景:托盘(JPopupMenu/JWindow)与 AWT 会启动非守护线程,若主流程在
 * 托盘创建之后崩溃(如 DI 解析失败),main 线程虽死,JVM 却被 AWT 线程
 * 吊住——表现为「托盘在、界面死」的僵尸实例,还一直占用单实例锁端口,
 * 用户只能手动杀进程。宁可让进程干净地退出去重启,也不留半死状态。
 *
 * [install] 必须在 main() 最先调用,确保单实例锁之前的早期崩溃同样快速失败。
 */
object CrashGuard {

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            handle(thread.name, error) { exitProcess(1) }
        }
    }

    /**
     * 统一的致命异常处理:记录现场后执行 [exit]。
     * 退出动作参数化是为了可测试——测试中用记录器替代真实的 exitProcess。
     */
    fun handle(threadName: String, error: Throwable, exit: (Int) -> Unit) {
        System.err.println("[CrashGuard] 线程 \"$threadName\" 发生未捕获异常,进程将立即退出(fail-fast)")
        error.printStackTrace()
        exit(1)
    }
}