package com.github.jobsflow.appjobsflow.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.jobsflow.appjobsflow.R
import com.github.jobsflow.appjobsflow.api.ApiClient
import com.github.jobsflow.appjobsflow.states.UserProfileState
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    client: ApiClient,
    sharedPrefs: android.content.SharedPreferences,
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    title: String,
    content: @Composable (innerPadding: PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val userProfileState = remember { UserProfileState() }
    val context = LocalContext.current

    val githubRepositoryUrl = "https://github.com/danula-ded/AppJobsFlow"
    val telegramChannelUrl = "mailto:stupishin1597@yandex.ru"

    val logout: () -> Unit = {
        with(sharedPrefs.edit()) {
            clear()
            apply()
        }
        navController.navigate("auth") {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
        }
    }

    LaunchedEffect(Unit) {
        userProfileState.loadUserData(client)
    }

    val exportJsonContent = remember { mutableStateOf<String?>(null) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val content = exportJsonContent.value
                if (content.isNullOrEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar("Нет данных токена для экспорта") }
                } else {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                        os.flush()
                    }
                    scope.launch { snackbarHostState.showSnackbar("Токены экспортированы в JSON") }
                }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Ошибка экспорта: ${e.message}") }
            }
        }
    }

    fun onExportTokens() {
        val prefs = sharedPrefs
        val access = prefs.getString("access_token", null)
        val refresh = prefs.getString("refresh_token", null)
        if (access.isNullOrEmpty() && refresh.isNullOrEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Токены отсутствуют") }
            return
        }
        val json = JSONObject().apply {
            put("access_token", access)
            put("refresh_token", refresh)
            put("token_type", prefs.getString("token_type", null))
            put("scope", prefs.getString("scope", null))
            put("expires_in", prefs.getLong("expires_in", 0))
            put("token_issued_at", prefs.getLong("token_issued_at", 0))
            put("access_expires_at", prefs.getLong("access_expires_at", 0))
        }
        exportJsonContent.value = json.toString(2)
        val fileName = "hh_token_" + System.currentTimeMillis().toString() + ".json"
        createDocumentLauncher.launch(fileName)
    }

    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(
                            R.string.drawer_greeting,
                            userProfileState.firstName,
                            userProfileState.lastName
                        ),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.home)) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.home_content_description)) },
                        selected = navController.currentDestination?.route == "home",
                        onClick = {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                                launchSingleTop = true
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.advanced_options)) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.advanced_options_content_description)) },
                        selected = navController.currentDestination?.route == "advancedOptions",
                        onClick = {
                            navController.navigate("advancedOptions") {
                                launchSingleTop = true
                            }
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.project_page)) },
                        icon = { Icon(Icons.Filled.Link, contentDescription = stringResource(R.string.project_page_content_description)) },
                        selected = false,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubRepositoryUrl))
                            context.startActivity(intent)
                            scope.launch { drawerState.close() }
                        }
                    )

                    // Ссылка на поддержку (email)
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.telegram_channel)) },
                        icon = { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.telegram_channel_content_description)) },
                        selected = false,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramChannelUrl))
                            context.startActivity(intent)
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.export_tokens)) },
                        icon = { Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.export_tokens)) },
                        selected = false,
                        onClick = {
                            onExportTokens()
                            scope.launch { drawerState.close() }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.logout)) },
                        icon = { Icon(Icons.Filled.Logout, contentDescription = stringResource(R.string.logout_content_description)) },
                        selected = false,
                        onClick = {
                            logout()
                            scope.launch { drawerState.close() }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        drawerState = drawerState,
        gesturesEnabled = true,
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = title, color = MaterialTheme.colorScheme.onPrimary) },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                if (drawerState.isClosed) {
                                    drawerState.open()
                                } else {
                                    drawerState.close()
                                }
                            }
                        }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.menu_content_description),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp, 18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                content(innerPadding)
            }
        }
    }
}
