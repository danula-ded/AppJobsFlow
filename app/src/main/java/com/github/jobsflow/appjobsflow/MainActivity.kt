package com.github.jobsflow.appjobsflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.jobsflow.appjobsflow.api.ApiClient
import com.github.jobsflow.appjobsflow.ui.components.AppNavigation
import com.github.jobsflow.appjobsflow.ui.theme.AppJobsFlowTheme

class MainActivity : ComponentActivity() {
    private val sharedPrefs by lazy {
        getSharedPreferences("appjobsflow", Context.MODE_PRIVATE)
    }
    private lateinit var client: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        client = ApiClient(sharedPrefs = sharedPrefs)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        setContent {
            AppJobsFlowTheme {
                AppNavigation(client, sharedPrefs)
            }
        }
    }
}
