package com.wceng.dictation.platform

import java.net.InetAddress
import java.net.ServerSocket

/**
 * 单实例保护:绑定 127.0.0.1:24680,被占用说明已有实例。
 * 锁对象必须被长期持有(不关闭),否则会被 GC 回收导致锁失效。
 */
class SingleInstanceLock {

    @Volatile private var lock: ServerSocket? = null

    fun acquire(): Boolean {
        return try {
            lock = ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun release() {
        runCatching { lock?.close() }
        lock = null
    }

    companion object {
        const val PORT = 24680
    }
}
