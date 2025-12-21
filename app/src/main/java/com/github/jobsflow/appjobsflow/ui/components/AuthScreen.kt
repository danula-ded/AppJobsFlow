package com.github.jobsflow.appjobsflow.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.jobsflow.appjobsflow.api.ApiClient
import com.github.jobsflow.appjobsflow.api.ApiConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthScreen(
    client: ApiClient,
    sharedPrefs: android.content.SharedPreferences,
    snackbarHostState: SnackbarHostState,
    onAuthorized: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        private fun isRedirectUrl(url: String, redirect: String): Boolean {
                            val u = Uri.parse(url)
                            val r = Uri.parse(redirect)
                            val sameScheme = (u.scheme ?: "").equals(r.scheme ?: "", ignoreCase = true)
                            val sameHost = (u.host ?: "").equals(r.host ?: "", ignoreCase = true)
                            val samePort = (u.port) == (r.port)
                            val uPath = (u.path ?: "/").trimEnd('/')
                            val rPath = (r.path ?: "/").trimEnd('/')
                            val pathOk = uPath.startsWith(rPath)
                            return sameScheme && sameHost && samePort && pathOk
                        }
                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            url: String?
                        ): Boolean {
                            val redirect = ApiConstants.REDIRECT_URI.ifEmpty { "hhandroid://oauthresponse" }
                            if (url != null && isRedirectUrl(url, redirect)) {
                                val uri = Uri.parse(url)
                                val code = uri.getQueryParameter("code")
                                if (code != null) {
                                    coroutineScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                client.authenticate(code)
                                            }
                                            //client.saveToPrefs(sharedPrefs)
                                            onAuthorized()
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Ошибка: ${e.message}")
                                        }
                                    }
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Код авторизации не найден")
                                    }
                                }
                                return true
                            }
                            return false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val url = request.url.toString()
                            val redirect = ApiConstants.REDIRECT_URI.ifEmpty { "hhandroid://oauthresponse" }
                            if (isRedirectUrl(url, redirect)) {
                                val uri = Uri.parse(url)
                                val code = uri.getQueryParameter("code")
                                if (code != null) {
                                    coroutineScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                client.authenticate(code)
                                            }
                                            onAuthorized()
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Ошибка: ${e.message}")
                                        }
                                    }
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Код авторизации не найден")
                                    }
                                }
                                return true
                            }
                            return false
                        }
                    }
                    val authUrl = client.getAuthorizeUrl()
                    System.err.println("Authorize URL: $authUrl")
                    System.err.println("Using redirect: ${ApiConstants.REDIRECT_URI}")
                    loadUrl(authUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
