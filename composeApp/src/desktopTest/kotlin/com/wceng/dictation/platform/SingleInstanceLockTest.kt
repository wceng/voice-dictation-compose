package com.wceng.dictation.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 单实例锁 + 命令通道验证。
 * 一律使用随机空闲端口:真机应用可能正占着默认端口 24680,
 * 测试不能依赖它是否在运行。
 */
class SingleInstanceLockTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `second acquire fails while first instance holds lock`() {
        val port = freePort()
        val first = SingleInstanceLock(port)
        try {
            assertTrue(first.acquire())
            val second = SingleInstanceLock(port)
            assertFalse(second.acquire())
            second.release()
        } finally {
            first.release()
        }
    }

    @Test
    fun `sendCommand delivers line-based commands to first instance`() {
        val port = freePort()
        val received = ArrayBlockingQueue<String>(8)
        val lock = SingleInstanceLock(port) { received.put(it) }
        try {
            assertTrue(lock.acquire())

            assertTrue(SingleInstanceLock.sendCommand(SingleInstanceLock.CMD_SHOW, port))
            assertEquals(SingleInstanceLock.CMD_SHOW, received.poll(2, TimeUnit.SECONDS))

            // 未知命令原样透传,白名单过滤由调用方负责
            assertTrue(SingleInstanceLock.sendCommand("ping", port))
            assertEquals("ping", received.poll(2, TimeUnit.SECONDS))
        } finally {
            lock.release()
        }
    }

    @Test
    fun `sendCommand returns false when no instance is listening`() {
        val port = freePort()
        assertFalse(SingleInstanceLock.sendCommand(SingleInstanceLock.CMD_SHOW, port))
    }

    @Test
    fun `release frees the port for re-acquire`() {
        val port = freePort()
        val first = SingleInstanceLock(port)
        assertTrue(first.acquire())
        first.release()

        val again = SingleInstanceLock(port)
        try {
            assertTrue(again.acquire(), "释放后端口必须可重新绑定")
        } finally {
            again.release()
        }
    }
}