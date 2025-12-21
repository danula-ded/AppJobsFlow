package com.github.jobsflow.appjobsflow.worker

import android.content.Context
import android.util.Log
import androidx.work.WorkerParameters
import com.github.jobsflow.appjobsflow.api.ApiClient
import com.github.jobsflow.appjobsflow.api.ApiException

class ResumeUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : NotificationWorker(context, workerParams) {

    private val TAG = "ResumeUpdateWorker"

    private val resumeId = inputData.getString("resume_id")
    private val sharedPrefs = context.getSharedPreferences("appjobsflow", Context.MODE_PRIVATE)
    private val client = ApiClient(sharedPrefs = sharedPrefs)

    override suspend fun doWork(): Result {
        if (resumeId.isNullOrEmpty()) {
            val errorMessage = "Не задан resume_id для обновления."
            Log.e(TAG, errorMessage)
            showNotification("❗ $errorMessage")
            return Result.failure()
        }

        return try {
            client.api("POST", "/resumes/$resumeId/publish")
            val successMessage = "Резюме успешно обновлено."
            Log.d(TAG, successMessage)
            showNotification("✅ $successMessage")
            Result.success()
        } catch (e: ApiException) {
            val errorMessage = "Ошибка обновления резюме: ${e.message ?: "Unknown API Error"}"
            Log.e(TAG, errorMessage, e)
            showNotification("❗ $errorMessage")
            Result.success()
        } catch (e: Exception) {
            val errorMessage = "Неожиданная ошибка обновления резюме: ${e.message ?: "Unknown Error"}"
            Log.e(TAG, errorMessage, e)
            showNotification("❗ $errorMessage")
            // При failure периодический запуск будет отменен
            //Result.failure()
            Result.retry()
        }
    }
}
