package com.wceng.dictation.platform

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * 单实例保护:绑定 127.0.0.1:[port],被占用说明已有实例。
 * 锁对象必须被长期持有(不关闭),否则会被 GC 回收导致锁失效。
 *
 * 在纯端口锁之上扩展为轻量命令通道:第一实例在守护线程里 accept 连接,
 * 按行读取命令文本并回调 [onCommand];第二实例经 [sendCommand] 发送,
 * 实现「双开即唤起已有实例的主窗口」。仅回环地址可达,命令白名单由调用方过滤。
 */
class SingleInstanceLock(
    private val port: Int = PORT,
    private val onCommand: (String) -> Unit = {}
) {

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var released = false

    fun acquire(): Boolean {
        released = false
        return try {
            val ss = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = ss
            Thread({ listen(ss) }, "single-instance-command-listener").apply {
                isDaemon = true
                start()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun release() {
        released = true
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun listen(ss: ServerSocket) {
        while (!released) {
            val client = try {
                ss.accept()
            } catch (e: Exception) {
                break // release() 关闭套接字后正常走到这里
            }
            handle(client)
        }
    }

    private fun handle(client: Socket) {
        client.use { c ->
            runCatching {
                // 读超时防呆:连接后不发数据的客户端不能阻塞后续命令
                c.soTimeout = CLIENT_TIMEOUT_MS
                val command = c.getInputStream().bufferedReader().readLine()?.trim()
                if (!command.isNullOrEmpty()) onCommand(command)
            }
        }
    }

    companion object {
        const val PORT = 24680
        const val CMD_SHOW = "show"

        private const val CLIENT_TIMEOUT_MS = 2_000

        /** 第二实例调用:向运行中的第一实例发送一行命令,成功返回 true */
        fun sendCommand(command: String, port: Int = PORT, timeoutMs: Int = 1_000): Boolean {
            return runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), timeoutMs)
                    s.getOutputStream().write((command + "\n").toByteArray(Charsets.UTF_8))
                    s.getOutputStream().flush()
                }
                true
            }.getOrDefault(false)
        }
    }
}