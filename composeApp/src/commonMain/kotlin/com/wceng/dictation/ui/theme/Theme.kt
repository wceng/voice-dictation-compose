package com.wceng.dictation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.wceng.dictation.core.model.ThemeMode

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/**
 * 应用主题:按 [themeMode] 决定亮暗;SYSTEM 档跟随操作系统并随系统切换实时重组。
 * 目前使用 Material 3 基线配色,两种模式下组件均可读。
 */
@Composable
fun DictationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
