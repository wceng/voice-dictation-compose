package com.wceng.dictation.data.network

import com.wceng.dictation.core.model.AppConfig
import com.wceng.dictation.core.model.TranscriptionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容 /audio/transcriptions 客户端(OkHttp,桌面实现)。
 * 配置由调用方传入,本类不感知任何仓库。
 *
 * 失败自动重试: 网络抖动(IOException)或服务端 5xx 时指数退避重试最多 2 次(1s→2s);
 * HTTP 4xx 与解析失败不重试。
 */
class OkHttpSttNetworkDataSource : SttNetworkDataSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private enum class ErrorKind { NETWORK, SERVER, CLIENT, PARSE, UNKNOWN }

    private sealed interface Attempt {
        data class Success(val text: String) : Attempt
        data class Failure(val kind: ErrorKind, val reason: String) : Attempt {
            fun shouldRetry(): Boolean = kind == ErrorKind.NETWORK || kind == ErrorKind.SERVER
        }
    }

    override suspend fun transcribe(config: AppConfig, wavBytes: ByteArray): TranscriptionResult =
        withContext(Dispatchers.IO) {
            var last: Attempt.Failure? = null
            repeat(MAX_ATTEMPTS) { attempt ->
                when (val r = attemptOnce(config, wavBytes)) {
                    is Attempt.Success -> return@withContext TranscriptionResult.Success(r.text)
                    is Attempt.Failure -> {
                        last = r
                        if (!r.shouldRetry()) {
                            return@withContext TranscriptionResult.Failure(r.reason)
                        }
                        if (attempt < MAX_ATTEMPTS - 1) {
                            val delayMs = BASE_DELAY_MS * (1L shl attempt) // 1s, 2s
                            System.err.println(
                                "[Stt] 第 ${attempt + 1} 次尝试失败, ${delayMs}ms 后重试..."
                            )
                            delay(delayMs)
                        }
                    }
                }
            }
            TranscriptionResult.Failure("${last?.reason} (已重试 ${MAX_ATTEMPTS - 1} 次)")
        }

    private fun attemptOnce(config: AppConfig, wavBytes: ByteArray): Attempt {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", config.model)
            .addFormDataPart("language", config.language)
            .addFormDataPart(
                "file",
                "dictation.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = bodyStr.take(200)
                    System.err.println("[Stt] API 请求失败: HTTP ${resp.code} - $detail")
                    val kind = if (resp.code in 400..499) ErrorKind.CLIENT else ErrorKind.SERVER
                    return Attempt.Failure(
                        kind,
                        "API 返回 HTTP ${resp.code}${if (detail.isNotBlank()) ": $detail" else ""}"
                    )
                }
                try {
                    Attempt.Success(json.decodeFromString<TranscriptionResponse>(bodyStr).text)
                } catch (e: Exception) {
                    System.err.println("[Stt] 解析响应失败: $bodyStr")
                    Attempt.Failure(ErrorKind.PARSE, "响应解析失败")
                }
            }
        } catch (e: IOException) {
            Attempt.Failure(ErrorKind.NETWORK, "网络错误: ${e.message ?: "未知"}")
        } catch (e: Exception) {
            Attempt.Failure(ErrorKind.UNKNOWN, "未知错误: ${e.message ?: "未知"}")
        }
    }

    @Serializable
    private data class TranscriptionResponse(val text: String = "")

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val BASE_DELAY_MS = 1000L
    }
}
