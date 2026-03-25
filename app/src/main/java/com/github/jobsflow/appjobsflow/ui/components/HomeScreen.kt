package com.github.jobsflow.appjobsflow.ui.components

import android.content.SharedPreferences
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.github.jobsflow.appjobsflow.R
import com.github.jobsflow.appjobsflow.api.ApiClient
import com.github.jobsflow.appjobsflow.ui.home.CoverLetterTemplate
import com.github.jobsflow.appjobsflow.ui.home.HomeViewModel
import com.github.jobsflow.appjobsflow.ui.home.HomeViewModelFactory
import com.github.jobsflow.appjobsflow.ui.home.parseCsvList
import com.github.jobsflow.appjobsflow.ui.home.toCsvString
import kotlinx.coroutines.delay

private val WORK_FORMAT_OPTIONS = listOf("Удаленно", "Гибрид", "Офис")
private val EMPLOYMENT_FORM_OPTIONS = listOf("Трудовой договор", "ГПХ", "Самозанятость", "ИП")
private val SCHEDULE_OPTIONS = listOf("Полный день", "Сменный график", "Гибкий график", "Удаленная работа", "Вахтовый метод")
private val EMPLOYMENT_TYPE_OPTIONS = listOf("Полная занятость", "Частичная занятость", "Стажировка", "Проектная работа", "Подработка")

@Composable
fun TemplateEditorScreen(
  client: ApiClient,
  sharedPrefs: SharedPreferences,
  navController: NavController,
  snackbarHostState: SnackbarHostState,
  templateId: String?,
) {
  val context = LocalContext.current
  val viewModel: HomeViewModel = viewModel(
    factory = HomeViewModelFactory(context, client, sharedPrefs, snackbarHostState)
  )

  val resumes by viewModel.resumes.collectAsState()
  val templates by viewModel.coverLetterTemplates.collectAsState()
  val selectedResumeId by viewModel.selectedResumeId.collectAsState()
  val searchQuery by viewModel.searchQuery.collectAsState()
  val filters by viewModel.filters.collectAsState()
  val coverLetter by viewModel.coverLetter.collectAsState()
  val alwaysAttach by viewModel.alwaysAttachCoverLetter.collectAsState()
  val draftPreview by viewModel.draftTemplatePreview.collectAsState()
  val draftPreviewLoading by viewModel.draftTemplatePreviewLoading.collectAsState()
  val availableAreaNames by viewModel.availableAreaNames.collectAsState()
  val availableCompanyNames by viewModel.availableCompanyNames.collectAsState()
  val isVacancyApplyRunning by viewModel.isVacancyApplyRunning.collectAsState()

  val isCreate = templateId == "new"
  val template = templates.firstOrNull { it.id == templateId }

  var initialized by remember(templateId, template?.id) { mutableStateOf(false) }
  var name by remember(templateId) { mutableStateOf("") }
  var content by remember(templateId) { mutableStateOf("") }
  var localResumeId by remember(templateId) { mutableStateOf<String?>(null) }
  var localSearchText by remember(templateId) { mutableStateOf("") }
  var localFilters by remember(templateId) { mutableStateOf(com.github.jobsflow.appjobsflow.ui.home.VacancyFilters()) }
  var localAlwaysAttach by remember(templateId) { mutableStateOf(false) }
  var areaQuery by remember(templateId) { mutableStateOf("") }
  var whitelistQuery by remember(templateId) { mutableStateOf("") }
  var blacklistQuery by remember(templateId) { mutableStateOf("") }
  var resumeDropdown by remember(templateId) { mutableStateOf(false) }
  var includeKeywordsInput by remember(templateId) { mutableStateOf("") }
  var excludeKeywordsInput by remember(templateId) { mutableStateOf("") }
  var pausedByEditor by remember(templateId) { mutableStateOf(false) }
  var previewVisibleLimit by remember(templateId) { mutableStateOf(5) }
  val isGeneralMode = localFilters.vacancySource.equals("general", ignoreCase = true)

  androidx.compose.runtime.LaunchedEffect(isCreate, template?.id, template?.enabled, isVacancyApplyRunning) {
    if (isCreate || template?.enabled != true) return@LaunchedEffect
    if (isVacancyApplyRunning && !pausedByEditor) {
      viewModel.stopVacancyApplyAutomation(showSnackbar = false)
      pausedByEditor = true
    }
  }

  val pausedByEditorState = androidx.compose.runtime.rememberUpdatedState(pausedByEditor)
  androidx.compose.runtime.DisposableEffect(Unit) {
    onDispose {
      if (pausedByEditorState.value) {
        viewModel.startVacancyApplyAutomation(enqueueImmediate = true, showSnackbar = false)
      }
    }
  }

  androidx.compose.runtime.LaunchedEffect(isCreate, template?.id, selectedResumeId, searchQuery, filters, coverLetter, alwaysAttach, initialized) {
    if (initialized) return@LaunchedEffect
    if (!isCreate && template == null) return@LaunchedEffect

    if (isCreate) {
      name = "Новый шаблон"
      content = coverLetter
      localResumeId = selectedResumeId
      localSearchText = searchQuery
      localFilters = filters.copy(searchText = searchQuery)
      localAlwaysAttach = alwaysAttach
    } else {
      name = template?.name.orEmpty()
      content = template?.content.orEmpty()
      localResumeId = template?.resumeId
      localSearchText = template?.searchText.orEmpty()
      localFilters = template?.filters?.copy(searchText = template.searchText) ?: com.github.jobsflow.appjobsflow.ui.home.VacancyFilters()
      localAlwaysAttach = template?.alwaysAttach == true
    }
    includeKeywordsInput = toCsvString(localFilters.includeKeywords)
    excludeKeywordsInput = toCsvString(localFilters.excludeTitleKeywords)
    initialized = true
  }

  androidx.compose.runtime.LaunchedEffect(initialized, localResumeId, localSearchText, localFilters) {
    if (!initialized) return@LaunchedEffect
    delay(1100)
    viewModel.refreshDraftTemplatePreview(
      resumeId = localResumeId,
      searchText = localSearchText,
      templateFilters = localFilters
    )
  }

  androidx.compose.runtime.LaunchedEffect(draftPreview.topVacancyTitles) {
    previewVisibleLimit = 5
  }

  AppScaffold(
    client = client,
    sharedPrefs = sharedPrefs,
    navController = navController,
    snackbarHostState = snackbarHostState,
    title = if (isCreate) "Создание шаблона" else "Редактирование шаблона",
    scrollableContent = false,
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Название шаблона") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(onClick = { resumeDropdown = true }, modifier = Modifier.fillMaxWidth()) {
            val resumeTitle = resumes.firstOrNull { it["id"]?.toString() == localResumeId }?.get("title")?.toString()
            Text(resumeTitle ?: "Выберите резюме", modifier = Modifier.fillMaxWidth())
          }
          DropdownMenu(expanded = resumeDropdown, onDismissRequest = { resumeDropdown = false }) {
            resumes.forEach { resume ->
              DropdownMenuItem(
                text = { Text(resume["title"]?.toString() ?: "") },
                onClick = {
                  localResumeId = resume["id"]?.toString()
                  resumeDropdown = false
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (!isGeneralMode) {
          OutlinedTextField(
            value = localSearchText,
            onValueChange = { localSearchText = it },
            label = { Text("Поисковый запрос для рекомендованных HH") },
            modifier = Modifier.fillMaxWidth()
          )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Источник вакансий", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          InputChip(
            selected = !isGeneralMode,
            onClick = { localFilters = localFilters.copy(vacancySource = "recommended") },
            label = { Text("Рекомендованные HH") }
          )
          InputChip(
            selected = isGeneralMode,
            onClick = { localFilters = localFilters.copy(vacancySource = "general") },
            label = { Text("Общий список") }
          )
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = content,
          onValueChange = { content = it },
          label = { Text("Сопроводительное") },
          modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(checked = localAlwaysAttach, onCheckedChange = { localAlwaysAttach = it })
          Spacer(modifier = Modifier.width(8.dp))
          Text("Всегда прикладывать письмо")
        }

        if (isGeneralMode) {
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = includeKeywordsInput,
            onValueChange = {
              includeKeywordsInput = it
              localFilters = localFilters.copy(includeKeywords = parseCsvList(it))
            },
            label = { Text("Ключевые слова для сбора (через запятую)") },
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = excludeKeywordsInput,
            onValueChange = {
              excludeKeywordsInput = it
              localFilters = localFilters.copy(excludeTitleKeywords = parseCsvList(it))
            },
            label = { Text("Слова-исключения (через запятую)") },
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = localFilters.salaryFrom?.toString() ?: "",
            onValueChange = { raw -> localFilters = localFilters.copy(salaryFrom = raw.filter { it.isDigit() }.toIntOrNull()) },
            label = { Text("ЗП от") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = localFilters.onlyWithSalary,
              onCheckedChange = { localFilters = localFilters.copy(onlyWithSalary = it) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Требовать указанную зарплату")
          }

          SearchableCatalogSelector(
            title = "Города/регионы",
            query = areaQuery,
            onQueryChange = {
              areaQuery = it
              viewModel.suggestAreas(it)
            },
            selected = localFilters.areaNames,
            catalog = availableAreaNames,
            enabled = true,
            onSelectedChange = { localFilters = localFilters.copy(areaNames = it) }
          )

          MultiOptionSelector(
            title = "Формат работы",
            options = WORK_FORMAT_OPTIONS,
            selected = localFilters.workFormats,
            enabled = true,
            onSelectedChange = { localFilters = localFilters.copy(workFormats = it) }
          )
          MultiOptionSelector(
            title = "Форма трудоустройства",
            options = EMPLOYMENT_FORM_OPTIONS,
            selected = localFilters.employmentForms,
            enabled = true,
            onSelectedChange = { localFilters = localFilters.copy(employmentForms = it) }
          )
          MultiOptionSelector(
            title = "График",
            options = SCHEDULE_OPTIONS,
            selected = localFilters.schedules,
            enabled = true,
            onSelectedChange = { localFilters = localFilters.copy(schedules = it) }
          )
          MultiOptionSelector(
            title = "Тип занятости",
            options = EMPLOYMENT_TYPE_OPTIONS,
            selected = localFilters.employmentTypes,
            enabled = true,
            onSelectedChange = { localFilters = localFilters.copy(employmentTypes = it) }
          )

          SearchableCatalogSelector(
            title = "Компании: только эти",
            query = whitelistQuery,
            onQueryChange = {
              whitelistQuery = it
              viewModel.suggestCompanies(it)
            },
            selected = localFilters.companyWhitelistNames,
            catalog = availableCompanyNames,
            enabled = true,
            onSelectedChange = { localFilters = localFilters.copy(companyWhitelistNames = it) }
          )
          SearchableCatalogSelector(
            title = "Компании: исключить",
            query = blacklistQuery,
            onQueryChange = {
              blacklistQuery = it
              viewModel.suggestCompanies(it)
            },
            selected = localFilters.companyBlacklistNames,
            catalog = availableCompanyNames,
            enabled = true,
            onSelectedChange = { localFilters = localFilters.copy(companyBlacklistNames = it) }
          )

        } else {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Для рекомендованных HH фильтры отключены: шаблон работает без пользовательской фильтрации.",
            style = MaterialTheme.typography.bodySmall
          )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = {
              val effectiveSearchText = if (isGeneralMode) "" else localSearchText
              if (isCreate) {
                val createdId = viewModel.createCoverLetterTemplate(
                  name = name,
                  content = content,
                  resumeId = localResumeId,
                  searchText = effectiveSearchText,
                  templateFilters = localFilters,
                  alwaysAttach = localAlwaysAttach,
                )
                if (createdId != null) navController.popBackStack()
              } else {
                val currentId = template?.id ?: return@Button
                viewModel.updateCoverLetterTemplate(
                  id = currentId,
                  name = name,
                  content = content,
                  resumeId = localResumeId,
                  searchText = effectiveSearchText,
                  templateFilters = localFilters,
                  alwaysAttach = localAlwaysAttach,
                )
                navController.popBackStack()
              }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraSmall
          ) {
            Text(if (isCreate) "Создать" else "Сохранить")
          }
        }

        if (draftPreview.topVacancyTitles.isNotEmpty()) {
          Spacer(modifier = Modifier.height(12.dp))
          Text("Примеры подходящих вакансий", style = MaterialTheme.typography.titleSmall)
          Spacer(modifier = Modifier.height(6.dp))
          draftPreview.topVacancyTitles.take(previewVisibleLimit).forEach { title ->
            Text("• $title", style = MaterialTheme.typography.bodySmall)
          }
          if (draftPreview.topVacancyTitles.size > previewVisibleLimit) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = {
                previewVisibleLimit = (previewVisibleLimit + 100).coerceAtMost(draftPreview.topVacancyTitles.size)
              },
              shape = MaterialTheme.shapes.extraSmall
            ) {
              val remaining = draftPreview.topVacancyTitles.size - previewVisibleLimit
              Text("Показать ещё (${remaining.coerceAtMost(100)})")
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }

      AssistChip(
        onClick = {},
        enabled = false,
        label = {
          Text(
            if (draftPreviewLoading) {
              "Считаем..."
            } else {
              "Подходящих вакансий: ${draftPreview.estimate.matched}"
            }
          )
        },
        modifier = Modifier
          .align(Alignment.TopEnd)
          .zIndex(10f)
      )
    }
  }
}

@Composable
fun HomeScreen(
  client: ApiClient,
  sharedPrefs: SharedPreferences,
  navController: NavController,
  snackbarHostState: SnackbarHostState,
) {
  val context = LocalContext.current
  val viewModel: HomeViewModel = viewModel(
    factory = HomeViewModelFactory(context, client, sharedPrefs, snackbarHostState)
  )

  val coverLetterTemplates by viewModel.coverLetterTemplates.collectAsState()
  val templatePreviewById by viewModel.templatePreviewById.collectAsState()
  val templatePreviewLoadingIds by viewModel.templatePreviewLoadingIds.collectAsState()
  val isVacancyApplyRunning by viewModel.isVacancyApplyRunning.collectAsState()

  androidx.compose.runtime.LaunchedEffect(
    coverLetterTemplates.map { template ->
      "${template.id}:${template.resumeId}:${template.searchText}:${template.filters.toJsonString()}"
    }
  ) {
    viewModel.refreshAllTemplatePreviews()
  }

  AppScaffold(
    client = client,
    sharedPrefs = sharedPrefs,
    navController = navController,
    snackbarHostState = snackbarHostState,
    title = stringResource(R.string.home),
  ) {
    Button(
      onClick = { navController.navigate("templateEditor/new") },
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.extraSmall
    ) {
      Text("Создать шаблон")
    }

    Spacer(modifier = Modifier.height(16.dp))

    coverLetterTemplates.forEach { template ->
      val preview = templatePreviewById[template.id]
      val isLoading = templatePreviewLoadingIds.contains(template.id)
      TemplateCard(
        template = template,
        previewText = if (isLoading) {
          "Считаем..."
        } else {
          "Подходящих вакансий: ${preview?.estimate?.matched ?: 0}"
        },
        topVacancies = preview?.topVacancyTitles ?: emptyList(),
        onDelete = { viewModel.deleteCoverLetterTemplate(template.id) },
        onToggleSearch = {
          if (template.enabled && isVacancyApplyRunning) {
            viewModel.stopVacancyApplyAutomation(showSnackbar = true)
          } else {
            if (!template.enabled) viewModel.activateCoverLetterTemplate(template.id)
            viewModel.startVacancyApplyAutomation(enqueueImmediate = true, showSnackbar = true)
          }
        },
        isSearchRunning = template.enabled && isVacancyApplyRunning,
        onEdit = { navController.navigate("templateEditor/${template.id}") }
      )
      Spacer(modifier = Modifier.height(10.dp))
    }

  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateCard(
  template: CoverLetterTemplate,
  previewText: String,
  topVacancies: List<String>,
  onDelete: () -> Unit,
  onToggleSearch: () -> Unit,
  isSearchRunning: Boolean,
  onEdit: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = if (template.enabled) "${template.name} (активный)" else template.name,
        style = MaterialTheme.typography.titleMedium
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(previewText, style = MaterialTheme.typography.bodySmall)
      if (topVacancies.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        topVacancies.take(5).forEach { title ->
          Text("• $title", style = MaterialTheme.typography.bodySmall)
        }
      }
      Spacer(modifier = Modifier.height(10.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onEdit) {
          Icon(Icons.Filled.Edit, contentDescription = "Редактировать")
        }
        IconButton(onClick = onToggleSearch) {
          if (isSearchRunning) {
            Icon(Icons.Filled.Stop, contentDescription = "Остановить поиск")
          } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Запустить поиск")
          }
        }
        IconButton(onClick = onDelete) {
          Icon(Icons.Filled.Delete, contentDescription = "Удалить")
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiOptionSelector(
  title: String,
  options: List<String>,
  selected: List<String>,
  enabled: Boolean,
  onSelectedChange: (List<String>) -> Unit,
) {
  Text(title, style = MaterialTheme.typography.titleSmall)
  Spacer(modifier = Modifier.height(4.dp))
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEach { option ->
      val isSelected = selected.any { it.equals(option, ignoreCase = true) }
      AssistChip(
        onClick = {
          if (!enabled) return@AssistChip
          val updated = if (isSelected) {
            selected.filterNot { it.equals(option, ignoreCase = true) }
          } else {
            selected + option
          }
          onSelectedChange(updated.distinct())
        },
        enabled = enabled,
        label = { Text(option) },
        leadingIcon = {
          if (isSelected) {
            Icon(Icons.Filled.Check, contentDescription = null)
          }
        }
      )
    }
  }
  Spacer(modifier = Modifier.height(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchableCatalogSelector(
  title: String,
  query: String,
  onQueryChange: (String) -> Unit,
  selected: List<String>,
  catalog: List<String>,
  enabled: Boolean,
  onSelectedChange: (List<String>) -> Unit,
) {
  Text(title, style = MaterialTheme.typography.titleSmall)
  Spacer(modifier = Modifier.height(4.dp))
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    label = { Text("Поиск") },
    enabled = enabled,
    singleLine = true,
    modifier = Modifier.fillMaxWidth()
  )

  val filtered = if (query.isBlank()) {
    emptyList()
  } else {
    catalog
      .asSequence()
      .filter { it.contains(query.trim(), ignoreCase = true) }
      .filter { option -> selected.none { it.equals(option, ignoreCase = true) } }
      .take(10)
      .toList()
  }

  if (filtered.isNotEmpty()) {
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      filtered.forEach { option ->
        AssistChip(
          onClick = {
            if (!enabled) return@AssistChip
            onSelectedChange((selected + option).distinct())
            onQueryChange("")
          },
          enabled = enabled,
          label = { Text(option) },
          leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
        )
      }
    }
  }

  val manualValue = query.trim()
  val canAddManualValue = manualValue.isNotBlank() && selected.none { it.equals(manualValue, ignoreCase = true) }
  if (canAddManualValue) {
    Spacer(modifier = Modifier.height(6.dp))
    AssistChip(
      onClick = {
        if (!enabled) return@AssistChip
        onSelectedChange((selected + manualValue).distinct())
        onQueryChange("")
      },
      enabled = enabled,
      label = { Text("Добавить: $manualValue") },
      leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
    )
  }

  if (selected.isNotEmpty()) {
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      selected.forEach { item ->
        InputChip(
          selected = true,
          onClick = {
            if (!enabled) return@InputChip
            onSelectedChange(selected.filterNot { it == item })
          },
          enabled = enabled,
          label = { Text(item) },
          trailingIcon = { Icon(Icons.Filled.Delete, contentDescription = "Удалить") }
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverLetterTemplatesTab(
  templates: List<CoverLetterTemplate>,
  activeContent: String,
  enabled: Boolean,
  onAddTemplate: (String, String) -> Unit,
  onUpdateTemplate: (String, String, String) -> Unit,
  onActivateTemplate: (String) -> Unit,
  onDeleteTemplate: (String) -> Unit,
) {
  var newName by remember { mutableStateOf("") }
  var newContent by remember(activeContent) { mutableStateOf(activeContent) }

  OutlinedTextField(
    value = newName,
    onValueChange = { newName = it },
    enabled = enabled,
    singleLine = true,
    label = { Text("Название нового шаблона") },
    modifier = Modifier.fillMaxWidth()
  )
  Spacer(modifier = Modifier.height(8.dp))
  OutlinedTextField(
    value = newContent,
    onValueChange = { newContent = it },
    enabled = enabled,
    label = { Text("Текст нового шаблона") },
    modifier = Modifier.fillMaxWidth()
  )
  Spacer(modifier = Modifier.height(8.dp))
  Button(
    onClick = {
      onAddTemplate(newName, newContent)
      newName = ""
    },
    enabled = enabled && newName.isNotBlank(),
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraSmall
  ) {
    Text("Сохранить шаблон")
  }

  Spacer(modifier = Modifier.height(16.dp))

  templates.forEach { template ->
    var editedName by remember(template.id) { mutableStateOf(template.name) }
    var editedContent by remember(template.id) { mutableStateOf(template.content) }
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(12.dp)) {
        Text(
          text = if (template.enabled) "${template.name} (активный)" else template.name,
          style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = editedName,
          onValueChange = { editedName = it },
          enabled = enabled,
          singleLine = true,
          label = { Text("Название") },
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = editedContent,
          onValueChange = { editedContent = it },
          enabled = enabled,
          label = { Text("Текст") },
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { onUpdateTemplate(template.id, editedName, editedContent) },
            enabled = enabled,
            shape = MaterialTheme.shapes.extraSmall
          ) {
            Text("Сохранить")
          }
          OutlinedButton(
            onClick = { onActivateTemplate(template.id) },
            enabled = enabled && !template.enabled,
            shape = MaterialTheme.shapes.extraSmall
          ) {
            Text("Сделать активным")
          }
          OutlinedButton(
            onClick = { onDeleteTemplate(template.id) },
            enabled = enabled && templates.size > 1,
            shape = MaterialTheme.shapes.extraSmall
          ) {
            Text("Удалить")
          }
        }
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
  }
}
