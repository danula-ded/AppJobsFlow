package com.github.jobsflow.appjobsflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
fun AppJobsFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Фиксированная палитра из Color.md (без dynamic color)
    AppJobsFlowBaseTheme(darkTheme = darkTheme, useDynamicColor = false, content = content)
}
