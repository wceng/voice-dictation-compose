// 根构建脚本:插件版本统一在 settings.gradle.kts 的 pluginManagement 中声明,
// 这里只负责把插件加载到根 classpath,避免各子项目重复加载
plugins {
    kotlin("multiplatform").apply(false)
    id("org.jetbrains.kotlin.plugin.compose").apply(false)
    id("org.jetbrains.kotlin.plugin.serialization").apply(false)
    id("org.jetbrains.compose").apply(false)
}
