package com.wceng.dictation.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * CrashGuard 行为验证:致命异常必须以退出码 1 结束(fail-fast 契约),
 * 且安装动作真实生效。退出动作用记录器替代 exitProcess,避免杀死测试 JVM。
 */
class CrashGuardTest {

    @Test
    fun `fatal exception triggers exit with code 1`() {
        val exitCodes = mutableListOf<Int>()
        val error = IllegalStateException("模拟 DI 解析失败")

        CrashGuard.handle("main", error) { code -> exitCodes += code }

        assertEquals(listOf(1), exitCodes)
    }

    @Test
    fun `install registers default uncaught exception handler`() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        try {
            CrashGuard.install()
            assertNotNull(Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            // 还原全局状态,避免污染同 JVM 内的其他测试
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}