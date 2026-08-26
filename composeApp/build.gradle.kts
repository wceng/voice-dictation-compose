import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

// 打包(nativeDistributions)需要带 jpackage 的完整 JDK;
// Android Studio 自带的 JBR 没有它。这里自动在 Gradle 工具链缓存中寻找,
// 找不到时保持默认(使用运行 Gradle 的 JDK),仅影响打包任务。
val packageJdkHome: String? = run {
    val jdksRoot = File(System.getProperty("user.home"), ".gradle/jdks")
    if (!jdksRoot.isDirectory) return@run null
    jdksRoot.walkTopDown().maxDepth(4)
        .filter { it.isFile && (it.name.equals("jpackage.exe", true) || it.name == "jpackage") }
        // 只接受 JDK 21+：项目按 jvmToolchain(21) 编译，低版本 JDK 会让 run 任务
        // 抛 UnsupportedClassVersionError；不满足则继续找下一个候选
        .map { it.parentFile.parentFile }
        .firstOrNull { home ->
            val major = home.resolve("release").takeIf { it.isFile }
                ?.readLines()
                ?.firstOrNull { line -> line.startsWith("JAVA_VERSION=") }
                ?.substringAfter('"')?.substringBefore('"')
                ?.split('.')?.firstOrNull()?.toIntOrNull()
            major != null && major >= 21
        }
        ?.absolutePath
}

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    // 使用本机可用的 JDK(>=17);打包分发时 compose 插件会内嵌独立 JRE,
    // 最终用户无需安装任何 JDK
    jvmToolchain(21)

    // 目前只启用桌面目标;以后开发手机版时在此追加 androidTarget()/iosX64() 等,
    // commonMain 中的核心逻辑(模型/接口/状态机)无需改动
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            // 依赖注入(KMP)+ Compose Multiplatform 支持件(koinInject 等)
            implementation("io.insert-koin:koin-core:4.2.2")
            implementation("io.insert-koin:koin-compose:4.2.2")
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // STT API HTTP 客户端
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                // 全局键盘/鼠标钩子
                implementation("com.github.kwhat:jnativehook:2.2.2")
                // 托盘 DPI 换算等 Win32 调用
                implementation("net.java.dev.jna:jna-platform:5.14.0")
                // 配置持久化(Jetpack DataStore,KMP 支持 JVM 桌面/Android/iOS)
                implementation("androidx.datastore:datastore-preferences:1.1.7")
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter:5.10.2")
                implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
                // Koin 依赖图校验(checkModules 等)
                implementation("io.insert-koin:koin-test-junit5:4.2.2")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.wceng.dictation.MainKt"

        // 使用带 jpackage 的完整 JDK 打包(自动发现失败则用默认 JVM)
        packageJdkHome?.let { javaHome = it }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "VoiceDictation"
            packageVersion = "1.0.20"

            includeAllModules = true

            windows {
                menu = true
                shortcut = true
                dirChooser = false
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
