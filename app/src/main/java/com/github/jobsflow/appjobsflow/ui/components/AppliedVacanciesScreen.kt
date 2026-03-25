package com.github.jobsflow.appjobsflow.ui.components

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.github.jobsflow.appjobsflow.R
import com.github.jobsflow.appjobsflow.api.ApiClient
import com.github.jobsflow.appjobsflow.ui.home.HomeViewModel
import com.github.jobsflow.appjobsflow.ui.home.HomeViewModelFactory
import java.text.DateFormat
import java.util.Date

@Composable
fun AppliedVacanciesScreen(
  client: ApiClient,
  sharedPrefs: SharedPreferences,
  navController: NavController,
  snackbarHostState: SnackbarHostState,
) {
  val context = LocalContext.current
  val viewModel: HomeViewModel = viewModel(
    factory = HomeViewModelFactory(context, client, sharedPrefs, snackbarHostState)
  )

  val appliedVacancies by viewModel.appliedVacancies.collectAsState()
  var searchQuery by rememberSaveable { mutableStateOf("") }
  val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

  LaunchedEffect(Unit) {
    viewModel.refreshAppliedVacanciesHistory()
  }

  val filtered = remember(appliedVacancies, searchQuery) {
    val query = searchQuery.trim().lowercase()
    if (query.isBlank()) {
      appliedVacancies
    } else {
      appliedVacancies.filter { record ->
        record.vacancyName.lowercase().contains(query) ||
          record.companyName.lowercase().contains(query) ||
          record.areaName.lowercase().contains(query) ||
          record.vacancyId.lowercase().contains(query) ||
          record.source.lowercase().contains(query)
      }
    }
  }

  AppScaffold(
    client = client,
    sharedPrefs = sharedPrefs,
    navController = navController,
    snackbarHostState = snackbarHostState,
    title = stringResource(R.string.applied_vacancies),
    scrollableContent = false,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.applied_vacancies_search_label)) },
        singleLine = true
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { viewModel.refreshAppliedVacanciesHistory() }) {
          Text(stringResource(R.string.refresh))
        }
        Button(onClick = { viewModel.clearAppliedVacanciesHistory() }) {
          Text(stringResource(R.string.clear_history))
        }
      }

      Text(
        text = stringResource(R.string.applied_vacancies_count, filtered.size, appliedVacancies.size),
        style = MaterialTheme.typography.bodyMedium
      )

      if (filtered.isEmpty()) {
        Text(
          text = stringResource(R.string.applied_vacancies_empty),
          style = MaterialTheme.typography.bodyMedium
        )
      }

      filtered.forEach { record ->
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = record.vacancyName.ifBlank { "ID ${record.vacancyId}" },
              style = MaterialTheme.typography.titleSmall
            )
            if (record.companyName.isNotBlank()) {
              Text(text = record.companyName, style = MaterialTheme.typography.bodyMedium)
            }
            if (record.areaName.isNotBlank()) {
              Text(text = record.areaName, style = MaterialTheme.typography.bodySmall)
            }
            Text(
              text = stringResource(
                R.string.applied_vacancies_applied_at,
                dateFormatter.format(Date(record.appliedAtMs))
              ),
              style = MaterialTheme.typography.bodySmall
            )
            Row(modifier = Modifier.fillMaxWidth()) {
              Text(
                text = stringResource(R.string.applied_vacancies_source, record.source),
                style = MaterialTheme.typography.bodySmall
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.applied_vacancies_resume_id, record.resumeId),
                style = MaterialTheme.typography.bodySmall
              )
            }
            if (record.vacancyUrl.isNotBlank() && record.vacancyUrl != "n/a") {
              OutlinedButton(
                onClick = {
                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(record.vacancyUrl)))
                }
              ) {
                Text(stringResource(R.string.open_vacancy))
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
