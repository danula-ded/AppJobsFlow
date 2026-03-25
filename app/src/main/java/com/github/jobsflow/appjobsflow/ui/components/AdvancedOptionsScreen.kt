package com.github.jobsflow.appjobsflow.ui.components

import android.content.SharedPreferences
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.github.jobsflow.appjobsflow.R
import com.github.jobsflow.appjobsflow.api.ApiClient
import com.github.jobsflow.appjobsflow.api.ApiException
import com.github.jobsflow.appjobsflow.logging.AppFileLogger
import com.github.jobsflow.appjobsflow.ui.home.HomeViewModel
import com.github.jobsflow.appjobsflow.ui.home.HomeViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdvancedOptionsScreen(
    client: ApiClient,
    sharedPrefs: SharedPreferences,
    navController: NavController,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(context, client, sharedPrefs, snackbarHostState)
    )
    val automationSettings by homeViewModel.automationSettings.collectAsState()
    val isVacancyApplyRunning by homeViewModel.isVacancyApplyRunning.collectAsState()
    val isResumeUpdateRunning by homeViewModel.isResumeUpdateRunning.collectAsState()
    val selectedResumeId by homeViewModel.selectedResumeId.collectAsState()

    Log.d("AdvancedOptionsScreen", "Компонент загружен")

    val accessExpiresAtDate = if (client.accessExpiresAt > 0) {
        Date(client.accessExpiresAt)
    } else {
        null
    }

    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    val formattedExpiryDate = accessExpiresAtDate?.let { dateFormat.format(it) } ?: "Неизвестно"
    var logFileSizeBytes by remember { mutableStateOf(AppFileLogger.getLogFileSizeBytes(context)) }
    val logFilePath = remember { AppFileLogger.getLogFilePath(context) }

    fun reloadLogInfo() {
        logFileSizeBytes = AppFileLogger.getLogFileSizeBytes(context)
    }

    fun shareLogFile() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val exportFile = AppFileLogger.prepareExportFile(context)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )
                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Выгрузить лог"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Не удалось выгрузить лог: ${e.message ?: "ошибка"}")
                }
            }
        }
    }

    fun refreshAccessToken() {
        coroutineScope.launch(Dispatchers.IO) {  // переключаем корутину на IO
            try {
                Log.d("AdvancedOptionsScreen", "Начало обновления токена")
                client.refreshAccessToken()
                Log.d("AdvancedOptionsScreen", "Токен успешно обновлён!")
                //client.saveToPrefs(sharedPrefs)

                // переключаемся обратно на главный поток перед вызовом UI-операций
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(context.getString(R.string.token_refresh_success))
                }
            } catch (e: ApiException) {
                Log.e("AdvancedOptionsScreen", "Ошибка обновления токена: ${e.json}")

                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(context.getString(R.string.token_refresh_error, e.json))
                }
            }
        }
    }


    AppScaffold(
        navController = navController,
        sharedPrefs = sharedPrefs,
        title = stringResource(R.string.advanced_options),
        client = client,
        snackbarHostState = snackbarHostState,
    ) {
        SelectionContainer {
            Column {
                OutlinedTextField(
                    value = client.accessToken ?: "Нет токена",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.access_token)) },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = client.refreshToken ?: "Нет токена",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.refresh_token)) },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = formattedExpiryDate,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.token_expiry)) },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Фоновая автоматизация", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = automationSettings.backgroundApplyEnabled,
                        onCheckedChange = homeViewModel::toggleBackgroundApplyEnabled,
                        enabled = selectedResumeId != null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Фоновый автоотклик")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = automationSettings.backgroundResumeUpdateEnabled,
                        onCheckedChange = homeViewModel::toggleBackgroundResumeUpdateEnabled,
                        enabled = selectedResumeId != null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Фоновое автоподнятие резюме")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Статус откликов: ${if (isVacancyApplyRunning) "активен" else "остановлен"}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Статус автоподнятия: ${if (isResumeUpdateRunning) "активен" else "остановлен"}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { homeViewModel.startVacancyApplyAutomation(enqueueImmediate = true, showSnackbar = true) },
                        enabled = selectedResumeId != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Запустить отклики")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { homeViewModel.stopVacancyApplyAutomation(showSnackbar = true) },
                        enabled = selectedResumeId != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Остановить отклики")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Диагностический лог", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Файл: $logFilePath",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Размер: ${logFileSizeBytes / 1024} КБ",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            shareLogFile()
                            reloadLogInfo()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Выгрузить лог")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            AppFileLogger.clearLogFile(context)
                            reloadLogInfo()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Очистить лог")
                    }
                }
            }
        }
    }
}
