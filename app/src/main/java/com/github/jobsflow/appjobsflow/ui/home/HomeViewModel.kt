package com.github.jobsflow.appjobsflow.ui.home

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.jobsflow.appjobsflow.api.ApiClient
import com.github.jobsflow.appjobsflow.api.ApiException
import com.github.jobsflow.appjobsflow.worker.ResumeUpdateWorker
import com.github.jobsflow.appjobsflow.worker.VacancyApplyWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.UUID

data class VacancyEstimate(
  val total: Int = 0,
  val matched: Int = 0,
)

data class TemplateVacancyPreview(
  val estimate: VacancyEstimate = VacancyEstimate(),
  val topVacancyTitles: List<String> = emptyList(),
)

private data class PreviewApiCacheEntry(
  val found: Int,
  val items: List<Map<String, Any>>,
  val fetchedAtMs: Long,
)

class HomeViewModel(
  private val context: Context,
  private val client: ApiClient,
  private val sharedPrefs: SharedPreferences,
  private val snackbarHostState: SnackbarHostState,
) : ViewModel() {

  companion object {
    private const val TAG = "HomeViewModel"
    private const val PREVIEW_API_CACHE_TTL_MS = 15_000L
    private const val PREVIEW_TITLES_LIMIT = 2_000
  }

  fun suggestAreas(query: String) {
    areaSuggestJob?.cancel()
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return
    areaSuggestJob = viewModelScope.launch {
      delay(350)
      try {
        val allAreaNames = withContext(Dispatchers.IO) {
          allAreaNamesCache ?: run {
            val response = client.api("GET", "/areas")
            val rootAreas = response["items"] as? List<*> ?: emptyList<Any?>()
            val accumulator = linkedSetOf<String>()
            collectAreaNames(rootAreas, accumulator)
            accumulator.toList().sorted().also { allAreaNamesCache = it }
          }
        }
        val suggestions = allAreaNames
          .asSequence()
          .filter { it.contains(normalizedQuery, ignoreCase = true) }
          .take(100)
          .toList()
        _availableAreaNames.value = (_availableAreaNames.value + suggestions).distinct().sorted()
      } catch (e: Exception) {
        Log.w(TAG, "Area suggestions failed: query='$normalizedQuery' error=${e.message}")
      }
    }
  }

  fun suggestCompanies(query: String) {
    companySuggestJob?.cancel()
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return
    companySuggestJob = viewModelScope.launch {
      delay(350)
      try {
        val names = withContext(Dispatchers.IO) {
          val params = mapOf(
            "text" to normalizedQuery,
            "page" to "0",
            "per_page" to "100"
          )

          val employersResponse = client.api("GET", "/employers", params)
          val employerNames = (employersResponse["items"] as? List<Map<String, Any>>)
            .orEmpty()
            .mapNotNull { it["name"]?.toString()?.trim()?.takeIf { value -> value.isNotBlank() } }

          if (employerNames.isNotEmpty()) {
            employerNames
          } else {
            val vacanciesResponse = client.api("GET", "/vacancies", params)
            (vacanciesResponse["items"] as? List<Map<String, Any>>)
              .orEmpty()
              .mapNotNull { vacancy ->
                (vacancy["employer"] as? Map<String, Any>)
                  ?.get("name")
                  ?.toString()
                  ?.trim()
                  ?.takeIf { value -> value.isNotBlank() }
              }
          }
        }
        val suggestions = names.distinct().take(100)
        _availableCompanyNames.value = (_availableCompanyNames.value + suggestions).distinct().sorted()
      } catch (e: Exception) {
        Log.w(TAG, "Company suggestions failed: query='$normalizedQuery' error=${e.message}")
      }
    }
  }

  private fun collectAreaNames(raw: Any?, acc: MutableSet<String>) {
    when (raw) {
      is Map<*, *> -> {
        raw["name"]?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { acc += it }
        collectAreaNames(raw["areas"], acc)
      }
      is List<*> -> raw.forEach { item -> collectAreaNames(item, acc) }
    }
  }

  private fun normalizeForMatch(value: String): String {
    return value
      .replace(Regex("<[^>]*>"), " ")
      .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
      .lowercase()
      .replace('ё', 'е')
      .trim()
  }

  private fun canonicalTokens(value: String): Set<String> {
    val normalized = normalizeForMatch(value)
    if (normalized.isBlank()) return emptySet()
    val tokens = mutableSetOf(normalized)

    val aliases = mapOf(
      "удаленно" to setOf("удаленная работа", "remote", "remote work", "home office", "remotejob"),
      "гибрид" to setOf("hybrid", "гибридный"),
      "офис" to setOf("office", "onsite", "on site", "офисный"),
      "полный день" to setOf("full day", "fulltime", "full time", "fullday"),
      "сменный график" to setOf("shift", "shift work"),
      "гибкий график" to setOf("flexible", "flex", "flexible schedule"),
      "вахтовый метод" to setOf("rotation", "вахта"),
      "полная занятость" to setOf("full", "full time", "fulltime"),
      "частичная занятость" to setOf("part", "part time", "parttime"),
      "стажировка" to setOf("intern", "internship"),
      "проектная работа" to setOf("project"),
      "подработка" to setOf("temporary", "gig", "side job"),
      "трудовой договор" to setOf("labor contract", "employment contract"),
      "самозанятость" to setOf("self employed", "self-employment"),
      "ип" to setOf("sole proprietor", "individual entrepreneur")
    )

    aliases[normalized]?.forEach { alias ->
      val normalizedAlias = normalizeForMatch(alias)
      if (normalizedAlias.isNotBlank()) tokens += normalizedAlias
    }

    return tokens
  }

  private val SOURCE_RECOMMENDED = "recommended"
  private val SOURCE_GENERAL = "general"

  private val VACANCY_APPLY_PERIODIC_WORK_NAME = "vacancy_apply_periodic_work"
  private val VACANCY_APPLY_ONE_TIME_WORK_NAME = "vacancy_apply_one_time_work"
  private val RESUME_UPDATE_PERIODIC_WORK_NAME = "resume_update_periodic_work"
  private val RESUME_UPDATE_ONE_TIME_WORK_NAME = "resume_update_one_time_work"

  private val DEFAULT_COVER_LETTER = "{Здравствуйте|Добрый день|Приветствую}! {Меня заинтересовала|Понравилась} ваша {вакансия|позиция} %vacancyName%. {Мои компетенции соответствуют требованиям|Уверен в своих силах, основываясь на опыте|Я обладаю всем необходимым}, чтобы {эффективно|успешно} {выполнять|исполнять} {поставленные задачи|ваши требования}. {С радостью обсужу детали|С нетерпением жду возможности пообщаться|Готов ответить на вопросы} {на собеседовании|в удобное для вас время}. {С уважением|С наилучшими пожеланиями|Благодарю за внимание}, %firstName%."
  private val SHARED_PREFS_SELECTED_RESUME_ID = "selected_resume_id"
  private val SHARED_PREFS_SEARCH_QUERY = "search_query"
  private val SHARED_PREFS_ALWAYS_ATTACH_COVER_LETTER = "always_attach_cover_letter"
  private val SHARED_PREFS_COVER_LETTER = "cover_letter"
  private val SHARED_PREFS_COVER_LETTER_TEMPLATES = "cover_letter_templates_json"
  private val SHARED_PREFS_FILTERS_JSON = "vacancy_filters_json"
  private val SHARED_PREFS_AUTOMATION_JSON = "automation_settings_json"

  private val _resumes = MutableStateFlow<List<Map<String, Any>>>(emptyList())
  val resumes: StateFlow<List<Map<String, Any>>> = _resumes.asStateFlow()

  private val _selectedResumeId = MutableStateFlow<String?>(null)
  val selectedResumeId: StateFlow<String?> = _selectedResumeId.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _filters = MutableStateFlow(VacancyFilters())
  val filters: StateFlow<VacancyFilters> = _filters.asStateFlow()

  private val _automationSettings = MutableStateFlow(AutomationSettings())
  val automationSettings: StateFlow<AutomationSettings> = _automationSettings.asStateFlow()

  private val _alwaysAttachCoverLetter = MutableStateFlow(false)
  val alwaysAttachCoverLetter: StateFlow<Boolean> = _alwaysAttachCoverLetter.asStateFlow()

  private val _coverLetter = MutableStateFlow(DEFAULT_COVER_LETTER)
  val coverLetter: StateFlow<String> = _coverLetter.asStateFlow()

  private val _coverLetterTemplates = MutableStateFlow<List<CoverLetterTemplate>>(emptyList())
  val coverLetterTemplates: StateFlow<List<CoverLetterTemplate>> = _coverLetterTemplates.asStateFlow()

  private val _vacancyEstimate = MutableStateFlow(VacancyEstimate())
  val vacancyEstimate: StateFlow<VacancyEstimate> = _vacancyEstimate.asStateFlow()

  private val _vacancyEstimateLoading = MutableStateFlow(false)
  val vacancyEstimateLoading: StateFlow<Boolean> = _vacancyEstimateLoading.asStateFlow()

  private val _availableAreaNames = MutableStateFlow<List<String>>(emptyList())
  val availableAreaNames: StateFlow<List<String>> = _availableAreaNames.asStateFlow()

  private val _availableCompanyNames = MutableStateFlow<List<String>>(emptyList())
  val availableCompanyNames: StateFlow<List<String>> = _availableCompanyNames.asStateFlow()

  private val _templatePreviewById = MutableStateFlow<Map<String, TemplateVacancyPreview>>(emptyMap())
  val templatePreviewById: StateFlow<Map<String, TemplateVacancyPreview>> = _templatePreviewById.asStateFlow()

  private val _templatePreviewLoadingIds = MutableStateFlow<Set<String>>(emptySet())
  val templatePreviewLoadingIds: StateFlow<Set<String>> = _templatePreviewLoadingIds.asStateFlow()

  private val _draftTemplatePreview = MutableStateFlow(TemplateVacancyPreview())
  val draftTemplatePreview: StateFlow<TemplateVacancyPreview> = _draftTemplatePreview.asStateFlow()

  private val _draftTemplatePreviewLoading = MutableStateFlow(false)
  val draftTemplatePreviewLoading: StateFlow<Boolean> = _draftTemplatePreviewLoading.asStateFlow()

  private val _isVacancyApplyRunning = MutableStateFlow(false)
  val isVacancyApplyRunning: StateFlow<Boolean> = _isVacancyApplyRunning.asStateFlow()

  private val _isResumeUpdateRunning = MutableStateFlow(false)
  val isResumeUpdateRunning: StateFlow<Boolean> = _isResumeUpdateRunning.asStateFlow()

  private val _appliedVacancies = MutableStateFlow<List<AppliedVacancyRecord>>(emptyList())
  val appliedVacancies: StateFlow<List<AppliedVacancyRecord>> = _appliedVacancies.asStateFlow()

  private var estimateRefreshJob: Job? = null
  private var vacancyEstimateJob: Job? = null
  private var vacancyEstimateRequestSeq: Long = 0
  private var draftTemplatePreviewJob: Job? = null
  private var draftTemplatePreviewRequestSeq: Long = 0
  private var areaSuggestJob: Job? = null
  private var companySuggestJob: Job? = null
  private val previewApiCache = mutableMapOf<String, PreviewApiCacheEntry>()
  private var allAreaNamesCache: List<String>? = null

  init {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        _selectedResumeId.value = sharedPrefs.getString(SHARED_PREFS_SELECTED_RESUME_ID, null)
        _alwaysAttachCoverLetter.value = sharedPrefs.getBoolean(SHARED_PREFS_ALWAYS_ATTACH_COVER_LETTER, false)
        val persistedCoverLetter = sharedPrefs.getString(SHARED_PREFS_COVER_LETTER, DEFAULT_COVER_LETTER) ?: DEFAULT_COVER_LETTER
        _coverLetter.value = if (isBrokenEncoding(persistedCoverLetter)) DEFAULT_COVER_LETTER else persistedCoverLetter
        val templates = coverLetterTemplatesFromJson(sharedPrefs.getString(SHARED_PREFS_COVER_LETTER_TEMPLATES, null))
        _coverLetterTemplates.value = normalizeTemplates(
          if (templates.isEmpty()) {
            listOf(
              CoverLetterTemplate(
                id = UUID.randomUUID().toString(),
                name = "Основной шаблон",
                content = _coverLetter.value,
                enabled = false
              )
            )
          } else {
            templates
          }
        )

        val repairedTemplates = _coverLetterTemplates.value.map { template ->
          val repairedName = if (isBrokenEncoding(template.name)) "Шаблон" else template.name
          val repairedContent = if (isBrokenEncoding(template.content)) DEFAULT_COVER_LETTER else template.content
          if (repairedName != template.name || repairedContent != template.content) {
            template.copy(name = repairedName, content = repairedContent)
          } else {
            template
          }
        }
        if (repairedTemplates != _coverLetterTemplates.value) {
          _coverLetterTemplates.value = repairedTemplates
        }

        _coverLetter.value = _coverLetterTemplates.value.firstOrNull { it.enabled }?.content ?: _coverLetter.value
        val legacySearchQuery = sharedPrefs.getString(SHARED_PREFS_SEARCH_QUERY, "") ?: ""
        val persistedFilters = VacancyFilters.fromJsonString(sharedPrefs.getString(SHARED_PREFS_FILTERS_JSON, null))
        val migratedFilters = if (persistedFilters.searchText.isBlank() && legacySearchQuery.isNotBlank()) {
          persistedFilters.copy(searchText = legacySearchQuery)
        } else {
          persistedFilters
        }
        _filters.value = migratedFilters
        _searchQuery.value = migratedFilters.searchText
        _automationSettings.value = AutomationSettings.fromJsonString(
          sharedPrefs.getString(SHARED_PREFS_AUTOMATION_JSON, null)
        )
        _appliedVacancies.value = AppliedVacancyHistoryStore.load(sharedPrefs)

        with(sharedPrefs.edit()) {
          putString(SHARED_PREFS_COVER_LETTER, _coverLetter.value)
          putString(SHARED_PREFS_COVER_LETTER_TEMPLATES, coverLetterTemplatesToJson(_coverLetterTemplates.value))
          apply()
        }
      }
      loadResumes()
      observeWorkerStates()
      ensureAutomationScheduled(enqueueImmediate = false)
      refreshVacancyEstimate()
      refreshAllTemplatePreviews()
    }
  }

  private fun ensureAutomationScheduled(enqueueImmediate: Boolean) {
    val settings = automationSettings.value
    if (settings.backgroundApplyEnabled) {
      startVacancyApplyAutomation(enqueueImmediate = enqueueImmediate, showSnackbar = false)
    } else {
      stopVacancyApplyAutomation(showSnackbar = false)
    }
    if (settings.backgroundResumeUpdateEnabled) {
      startResumeUpdateAutomation(enqueueImmediate = enqueueImmediate, showSnackbar = false)
    } else {
      stopResumeUpdateAutomation(showSnackbar = false)
    }
  }

  private fun persistFilters(filters: VacancyFilters) {
    viewModelScope.launch(Dispatchers.IO) {
      with(sharedPrefs.edit()) {
        putString(SHARED_PREFS_FILTERS_JSON, filters.toJsonString())
        putString(SHARED_PREFS_SEARCH_QUERY, filters.searchText)
        apply()
      }
    }
  }

  private fun persistAutomationSettings(settings: AutomationSettings) {
    viewModelScope.launch(Dispatchers.IO) {
      sharedPrefs.edit().putString(SHARED_PREFS_AUTOMATION_JSON, settings.toJsonString()).apply()
    }
  }

  private fun persistTemplates(templates: List<CoverLetterTemplate>) {
    viewModelScope.launch(Dispatchers.IO) {
      sharedPrefs.edit().putString(SHARED_PREFS_COVER_LETTER_TEMPLATES, coverLetterTemplatesToJson(templates)).apply()
    }
  }

  fun refreshAppliedVacanciesHistory() {
    viewModelScope.launch(Dispatchers.IO) {
      _appliedVacancies.value = AppliedVacancyHistoryStore.load(sharedPrefs)
    }
  }

  fun clearAppliedVacanciesHistory() {
    viewModelScope.launch(Dispatchers.IO) {
      AppliedVacancyHistoryStore.clear(sharedPrefs)
      _appliedVacancies.value = emptyList()
      withContext(Dispatchers.Main) {
        snackbarHostState.showSnackbar("История откликов очищена")
      }
    }
  }

  private fun normalizeTemplates(input: List<CoverLetterTemplate>, preferredEnabledId: String? = null): List<CoverLetterTemplate> {
    if (input.isEmpty()) return emptyList()
    if (preferredEnabledId != null) {
      return input.map { it.copy(enabled = it.id == preferredEnabledId) }
    }

    val enabledTemplates = input.filter { it.enabled }
    if (enabledTemplates.isEmpty()) {
      return input.map { it.copy(enabled = false) }
    }

    val keepEnabledId = enabledTemplates.first().id
    return input.map { it.copy(enabled = it.id == keepEnabledId) }
  }

  private fun isBrokenEncoding(value: String): Boolean {
    return value.contains('╨') || value.contains('�')
  }

  private fun observeWorkerStates() {
    WorkManager.getInstance(context)
      .getWorkInfosForUniqueWorkLiveData(VACANCY_APPLY_PERIODIC_WORK_NAME)
      .asFlow()
      .onEach { workInfos ->
        _isVacancyApplyRunning.value = workInfos.any {
          it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
        }
      }
      .launchIn(viewModelScope)

    WorkManager.getInstance(context)
      .getWorkInfosForUniqueWorkLiveData(RESUME_UPDATE_PERIODIC_WORK_NAME)
      .asFlow()
      .onEach { workInfos ->
        _isResumeUpdateRunning.value = workInfos.any {
          it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
        }
      }
      .launchIn(viewModelScope)
  }

  private suspend fun loadResumes() {
    try {
      val data = withContext(Dispatchers.IO) {
        client.api("GET", "/resumes/mine")
      }
      val items = data["items"] as? List<Map<String, Any>> ?: emptyList()
      _resumes.value = items
    } catch (e: ApiException) {
      viewModelScope.launch {
        snackbarHostState.showSnackbar("Ошибка при загрузке резюме: ${e.message ?: "неизвестная"}")
      }
    }
  }

  fun selectResume(id: String) {
    val currentResumeId = _selectedResumeId.value
    if (id != currentResumeId) {
      stopVacancyApplyAutomation(showSnackbar = false)
      stopResumeUpdateAutomation(showSnackbar = false)

      _selectedResumeId.value = id
      viewModelScope.launch(Dispatchers.IO) {
        sharedPrefs.edit().putString(SHARED_PREFS_SELECTED_RESUME_ID, id).apply()
      }

      if (automationSettings.value.backgroundApplyEnabled) {
        startVacancyApplyAutomation(
          enqueueImmediate = automationSettings.value.runApplyImmediatelyOnEnable,
          showSnackbar = false
        )
      }
      if (automationSettings.value.backgroundResumeUpdateEnabled) {
        startResumeUpdateAutomation(enqueueImmediate = true, showSnackbar = false)
      }

      viewModelScope.launch {
        snackbarHostState.showSnackbar("Резюме изменено. Автоматизации перезапущены при необходимости.")
      }
    }
  }

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
    updateFilters(filters.value.copy(searchText = query))
  }

  fun updateFilters(updated: VacancyFilters) {
    _filters.value = updated
    _searchQuery.value = updated.searchText
    persistFilters(updated)
    scheduleEstimateRefresh()
  }

  private fun scheduleEstimateRefresh() {
    estimateRefreshJob?.cancel()
    estimateRefreshJob = viewModelScope.launch {
      delay(800)
      refreshVacancyEstimate()
    }
  }

  fun toggleAlwaysAttachCoverLetter(enabled: Boolean) {
    _alwaysAttachCoverLetter.value = enabled
    viewModelScope.launch(Dispatchers.IO) {
      sharedPrefs.edit().putBoolean(SHARED_PREFS_ALWAYS_ATTACH_COVER_LETTER, enabled).apply()
    }
  }

  fun updateCoverLetter(message: String) {
    _coverLetter.value = message
    val activeTemplate = _coverLetterTemplates.value.firstOrNull { it.enabled }
    if (activeTemplate != null) {
      val updatedTemplates = _coverLetterTemplates.value.map {
        if (it.id == activeTemplate.id) {
          it.copy(
            content = message,
            resumeId = selectedResumeId.value,
            searchText = searchQuery.value,
            filters = filters.value,
            alwaysAttach = alwaysAttachCoverLetter.value,
          )
        } else {
          it
        }
      }
      _coverLetterTemplates.value = updatedTemplates
      persistTemplates(updatedTemplates)
      refreshTemplatePreview(activeTemplate.id)
    }
    viewModelScope.launch(Dispatchers.IO) {
      sharedPrefs.edit().putString(SHARED_PREFS_COVER_LETTER, message).apply()
    }
  }

  fun addCoverLetterTemplate(name: String, content: String) {
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) return
    val newTemplate = CoverLetterTemplate(
      id = UUID.randomUUID().toString(),
      name = trimmedName,
      content = content,
      resumeId = selectedResumeId.value,
      searchText = searchQuery.value,
      filters = filters.value,
      alwaysAttach = alwaysAttachCoverLetter.value,
      enabled = false
    )
    val updated = normalizeTemplates(_coverLetterTemplates.value + newTemplate)
    _coverLetterTemplates.value = updated
    persistTemplates(updated)
    refreshTemplatePreview(newTemplate.id)
  }

  fun createCoverLetterTemplate(
    name: String,
    content: String,
    resumeId: String?,
    searchText: String,
    templateFilters: VacancyFilters,
    alwaysAttach: Boolean,
  ): String? {
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) return null
    val newTemplate = CoverLetterTemplate(
      id = UUID.randomUUID().toString(),
      name = trimmedName,
      content = content,
      resumeId = resumeId,
      searchText = searchText,
      filters = templateFilters.copy(searchText = searchText),
      alwaysAttach = alwaysAttach,
      enabled = false
    )
    val updated = normalizeTemplates(_coverLetterTemplates.value + newTemplate)
    _coverLetterTemplates.value = updated
    persistTemplates(updated)
    refreshTemplatePreview(newTemplate.id)
    return newTemplate.id
  }

  fun updateCoverLetterTemplate(
    id: String,
    name: String,
    content: String,
    resumeId: String?,
    searchText: String,
    templateFilters: VacancyFilters,
    alwaysAttach: Boolean,
  ) {
    val updated = _coverLetterTemplates.value.map {
      if (it.id == id) {
        it.copy(
          name = name.trim().ifBlank { it.name },
          content = content,
          resumeId = resumeId,
          searchText = searchText,
          filters = templateFilters.copy(searchText = searchText),
          alwaysAttach = alwaysAttach,
        )
      } else {
        it
      }
    }
    _coverLetterTemplates.value = updated
    persistTemplates(updated)
    val current = updated.firstOrNull { it.id == id }
    if (current != null) {
      refreshTemplatePreview(current.id)
    }
    if (current?.enabled == true) {
      applyTemplateToCurrentState(current)
    }
  }

  fun activateCoverLetterTemplate(id: String) {
    val updated = normalizeTemplates(_coverLetterTemplates.value, preferredEnabledId = id)
    _coverLetterTemplates.value = updated
    persistTemplates(updated)
    val active = updated.firstOrNull { it.enabled } ?: return
    applyTemplateToCurrentState(active)
    refreshTemplatePreview(active.id)
  }

  fun deleteCoverLetterTemplate(id: String) {
    val remaining = _coverLetterTemplates.value.filterNot { it.id == id }
    if (remaining.isEmpty()) return
    val normalized = normalizeTemplates(remaining)
    _coverLetterTemplates.value = normalized
    persistTemplates(normalized)
    val active = normalized.firstOrNull { it.enabled }
    if (active != null) {
      applyTemplateToCurrentState(active)
    }
    _templatePreviewById.value = _templatePreviewById.value - id
    _templatePreviewLoadingIds.value = _templatePreviewLoadingIds.value - id
  }

  fun setAreaNames(values: List<String>) {
    updateFilters(filters.value.copy(areaNames = values.distinct()))
  }

  fun setCompanyWhitelist(values: List<String>) {
    updateFilters(filters.value.copy(companyWhitelistNames = values.distinct()))
  }

  fun setCompanyBlacklist(values: List<String>) {
    updateFilters(filters.value.copy(companyBlacklistNames = values.distinct()))
  }

  fun refreshVacancyEstimate() {
    val resumeId = selectedResumeId.value ?: return
    val requestSeq = ++vacancyEstimateRequestSeq
    vacancyEstimateJob?.cancel()
    vacancyEstimateJob = viewModelScope.launch {
      _vacancyEstimateLoading.value = true
      try {
        val snapshotFilters = filters.value
        val result = withContext(Dispatchers.IO) {
          fetchVacanciesForPreview(resumeId, snapshotFilters)
        }
        if (requestSeq != vacancyEstimateRequestSeq) return@launch
        _vacancyEstimate.value = result.first
        _availableAreaNames.value = (_availableAreaNames.value + result.second.first).distinct().sorted()
        _availableCompanyNames.value = (_availableCompanyNames.value + result.second.second).distinct().sorted()
      } catch (e: Exception) {
        if (requestSeq != vacancyEstimateRequestSeq) return@launch
        snackbarHostState.showSnackbar("Не удалось обновить оценку вакансий: ${e.message ?: "ошибка"}")
      } finally {
        if (requestSeq == vacancyEstimateRequestSeq) {
          _vacancyEstimateLoading.value = false
        }
      }
    }
  }

  fun refreshTemplatePreview(templateId: String) {
    val template = _coverLetterTemplates.value.firstOrNull { it.id == templateId } ?: return
    val resumeId = template.resumeId
    if (resumeId.isNullOrBlank()) {
      _templatePreviewById.value = _templatePreviewById.value + (templateId to TemplateVacancyPreview())
      return
    }
    _templatePreviewLoadingIds.value = _templatePreviewLoadingIds.value + templateId
    viewModelScope.launch {
      try {
        val preview = withContext(Dispatchers.IO) {
          buildTemplatePreview(template)
        }
        _templatePreviewById.value = _templatePreviewById.value + (templateId to preview)
      } catch (_: Exception) {
        _templatePreviewById.value = _templatePreviewById.value + (templateId to TemplateVacancyPreview())
      } finally {
        _templatePreviewLoadingIds.value = _templatePreviewLoadingIds.value - templateId
      }
    }
  }

  fun refreshAllTemplatePreviews() {
    viewModelScope.launch {
      _coverLetterTemplates.value.forEach { template ->
        val resumeId = template.resumeId
        if (resumeId.isNullOrBlank()) {
          _templatePreviewById.value = _templatePreviewById.value + (template.id to TemplateVacancyPreview())
          return@forEach
        }

        _templatePreviewLoadingIds.value = _templatePreviewLoadingIds.value + template.id
        try {
          val preview = withContext(Dispatchers.IO) {
            buildTemplatePreview(template)
          }
          _templatePreviewById.value = _templatePreviewById.value + (template.id to preview)
        } catch (_: Exception) {
          _templatePreviewById.value = _templatePreviewById.value + (template.id to TemplateVacancyPreview())
        } finally {
          _templatePreviewLoadingIds.value = _templatePreviewLoadingIds.value - template.id
        }
      }
    }
  }

  private suspend fun buildTemplatePreview(template: CoverLetterTemplate): TemplateVacancyPreview {
    val resumeId = template.resumeId
    if (resumeId.isNullOrBlank()) return TemplateVacancyPreview()

    val result = fetchVacanciesForPreview(resumeId, template.filters.copy(searchText = template.searchText))
    return TemplateVacancyPreview(
      estimate = result.first,
      topVacancyTitles = result.third.take(8)
    )
  }

  fun refreshDraftTemplatePreview(
    resumeId: String?,
    searchText: String,
    templateFilters: VacancyFilters,
  ) {
    if (resumeId.isNullOrBlank()) {
      _draftTemplatePreview.value = TemplateVacancyPreview()
      return
    }
    val requestSeq = ++draftTemplatePreviewRequestSeq
    draftTemplatePreviewJob?.cancel()
    _draftTemplatePreviewLoading.value = true
    draftTemplatePreviewJob = viewModelScope.launch {
      try {
        val result = withContext(Dispatchers.IO) {
          fetchVacanciesForPreview(resumeId, templateFilters.copy(searchText = searchText))
        }
        if (requestSeq != draftTemplatePreviewRequestSeq) return@launch
        _draftTemplatePreview.value = TemplateVacancyPreview(
          estimate = result.first,
          topVacancyTitles = result.third.take(8)
        )
      } catch (_: Exception) {
        if (requestSeq != draftTemplatePreviewRequestSeq) return@launch
        _draftTemplatePreview.value = TemplateVacancyPreview()
      } finally {
        if (requestSeq == draftTemplatePreviewRequestSeq) {
          _draftTemplatePreviewLoading.value = false
        }
      }
    }
  }

  private suspend fun fetchVacanciesForPreview(
    resumeId: String,
    currentFilters: VacancyFilters
  ): Triple<VacancyEstimate, Pair<List<String>, List<String>>, List<String>> {
    val processedIds = mutableSetOf<String>()
    val areaSet = linkedSetOf<String>()
    val companySet = linkedSetOf<String>()
    val topMatchedTitles = mutableListOf<String>()
    var matched = 0
    var processedTotal = 0

    val source = if (currentFilters.vacancySource.equals(SOURCE_GENERAL, ignoreCase = true)) {
      SOURCE_GENERAL
    } else {
      SOURCE_RECOMMENDED
    }

    val perPage = 50
    var totalFromApi = 0

    Log.i(
      TAG,
      "Preview fetch start: source=$source resumeId=$resumeId searchText='${currentFilters.searchText.trim()}' includeKeywords=${currentFilters.includeKeywords.size} excludeTitleKeywords=${currentFilters.excludeTitleKeywords.size} salaryFrom=${currentFilters.salaryFrom} salaryTo=${currentFilters.salaryTo} perPage=$perPage"
    )

    suspend fun processResponseItems(items: List<Map<String, Any>>) {
      items.forEach { vacancy ->
        val vacancyId = vacancy["id"]?.toString()?.trim().orEmpty()
        if (vacancyId.isBlank()) return@forEach
        if (!processedIds.add(vacancyId)) return@forEach

        processedTotal += 1
        (vacancy["area"] as? Map<String, Any>)?.get("name")?.toString()?.trim()?.takeIf { it.isNotBlank() }
          ?.let { areaSet += it }
        (vacancy["employer"] as? Map<String, Any>)?.get("name")?.toString()?.trim()?.takeIf { it.isNotBlank() }
          ?.let { companySet += it }

        if (shouldKeepVacancy(vacancy, currentFilters)) {
          matched += 1
          if (topMatchedTitles.size < PREVIEW_TITLES_LIMIT) {
            vacancy["name"]?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { topMatchedTitles += it }
          }
        }
      }
    }

    if (source == SOURCE_GENERAL) {
      val generalSearchQuery = buildGeneralSearchQuery(currentFilters)
      if (generalSearchQuery.isBlank()) {
        Log.w(TAG, "Preview fetch skipped: general source selected but query is blank")
        return Triple(VacancyEstimate(), emptyList<String>() to emptyList(), emptyList())
      }

      Log.i(
        TAG,
        "Preview general query parts: include=${currentFilters.includeKeywords} exclude=${currentFilters.excludeTitleKeywords} areas=${currentFilters.areaNames} companyWhitelist=${currentFilters.companyWhitelistNames} companyBlacklist=${currentFilters.companyBlacklistNames} workFormats=${currentFilters.workFormats} employmentForms=${currentFilters.employmentForms} schedules=${currentFilters.schedules} employmentTypes=${currentFilters.employmentTypes}"
      )
      Log.i(TAG, "Preview general query='$generalSearchQuery'")

      val requestParams = mutableMapOf<String, String>(
        "page" to "0",
        "per_page" to "$perPage",
        "text" to generalSearchQuery
      )
      currentFilters.salaryFrom?.let { requestParams["salary"] = "$it" }
      if (currentFilters.onlyWithSalary) {
        requestParams["only_with_salary"] = "true"
      }

      val remoteCacheKey = "general|$resumeId|$generalSearchQuery|${currentFilters.salaryFrom ?: ""}|onlySalary=${currentFilters.onlyWithSalary}"
      val nowMs = System.currentTimeMillis()
      val cachedEntry = previewApiCache[remoteCacheKey]
      val useCache = cachedEntry != null && (nowMs - cachedEntry.fetchedAtMs) <= PREVIEW_API_CACHE_TTL_MS

      val items: List<Map<String, Any>>
      if (useCache) {
        items = cachedEntry!!.items
        totalFromApi = cachedEntry.found
        Log.d(TAG, "Preview cache hit [/vacancies]: key=$remoteCacheKey ageMs=${nowMs - cachedEntry.fetchedAtMs} items=${items.size} found=$totalFromApi")
      } else {
        Log.d(TAG, "Preview request [/vacancies]: page=0 plannedPages=1 params=$requestParams")

        val response = client.api("GET", "/vacancies", requestParams)
        items = response["items"] as? List<Map<String, Any>> ?: emptyList()

        Log.i(
          TAG,
          "Preview first raw [/vacancies]: keys=${response.keys} found=${response["found"]} pages=${response["pages"]} page=${response["page"]} per_page=${response["per_page"]} items=${items.size}"
        )

        totalFromApi = ((response["found"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
        val apiPages = ((response["pages"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
        val pagesByFound = if (totalFromApi > 0) {
          ((totalFromApi + perPage - 1) / perPage).coerceAtLeast(1)
        } else {
          1
        }
        Log.i(TAG, "Preview first page [/vacancies]: found=$totalFromApi pagesByFound=$pagesByFound apiPages=$apiPages finalPages=1")

        previewApiCache[remoteCacheKey] = PreviewApiCacheEntry(
          found = totalFromApi,
          items = items,
          fetchedAtMs = nowMs,
        )
      }

      Log.d(TAG, "Preview response [/vacancies]: page=0 items=${items.size} processedUnique=$processedTotal matched=$matched")
      processResponseItems(items)
      if (totalFromApi > 0) {
        matched = totalFromApi
        Log.i(TAG, "Preview matched synchronized with found for general query-first mode: matched=$matched")
      }
      Log.i(
        TAG,
        "Preview general metrics: foundApi=$totalFromApi localProcessedUnique=$processedTotal localMatchedPage0=$matched"
      )
    } else {
      val requestParams = mutableMapOf<String, String>("page" to "0", "per_page" to "$perPage")
      currentFilters.searchText.takeIf { it.isNotBlank() }?.let { requestParams["text"] = it.trim() }

      val remoteCacheKey = "recommended|$resumeId|${currentFilters.searchText.trim()}"
      val nowMs = System.currentTimeMillis()
      val cachedEntry = previewApiCache[remoteCacheKey]
      val useCache = cachedEntry != null && (nowMs - cachedEntry.fetchedAtMs) <= PREVIEW_API_CACHE_TTL_MS

      val items: List<Map<String, Any>>
      if (useCache) {
        items = cachedEntry!!.items
        totalFromApi = cachedEntry.found
        Log.d(TAG, "Preview cache hit [/similar_vacancies]: key=$remoteCacheKey ageMs=${nowMs - cachedEntry.fetchedAtMs} items=${items.size} found=$totalFromApi")
      } else {
        Log.d(TAG, "Preview request [/resumes/$resumeId/similar_vacancies]: page=0 plannedPages=1 params=$requestParams")

        val response = client.api("GET", "/resumes/$resumeId/similar_vacancies", requestParams)
        items = response["items"] as? List<Map<String, Any>> ?: emptyList()

        Log.i(
          TAG,
          "Preview first raw [/similar_vacancies]: keys=${response.keys} found=${response["found"]} pages=${response["pages"]} page=${response["page"]} per_page=${response["per_page"]} items=${items.size}"
        )

        totalFromApi = ((response["found"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
        val apiPages = ((response["pages"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
        val pagesByFound = if (totalFromApi > 0) {
          ((totalFromApi + perPage - 1) / perPage).coerceAtLeast(1)
        } else {
          1
        }
        Log.i(TAG, "Preview first page [/similar_vacancies]: found=$totalFromApi pagesByFound=$pagesByFound apiPages=$apiPages finalPages=1")

        previewApiCache[remoteCacheKey] = PreviewApiCacheEntry(
          found = totalFromApi,
          items = items,
          fetchedAtMs = nowMs,
        )
      }

      Log.d(TAG, "Preview response [/similar_vacancies]: page=0 items=${items.size} processedUnique=$processedTotal matched=$matched")
      processResponseItems(items)
    }

    val total = if (totalFromApi > 0) totalFromApi else processedTotal
    Log.i(
      TAG,
      "Preview fetch done: source=$source resumeId=$resumeId total=$total matched=$matched processedUnique=$processedTotal areas=${areaSet.size} companies=${companySet.size} topTitles=${topMatchedTitles.size}"
    )
    return Triple(
      VacancyEstimate(total = total, matched = matched),
      areaSet.toList().sorted() to companySet.toList().sorted(),
      topMatchedTitles
    )
  }

  private fun buildGeneralSearchQuery(currentFilters: VacancyFilters): String {
    fun normalizeQueryToken(value: String): String {
      return value
        .trim()
        .replace("\"", "")
        .replace("(", " ")
        .replace(")", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    }

    fun buildOrGroup(values: List<String>, quoteValues: Boolean = false): String {
      val prepared = values
        .map { normalizeQueryToken(it) }
        .filter { it.isNotBlank() }
        .distinct()
      if (prepared.isEmpty()) return ""
      val body = prepared.joinToString(" OR ") { token ->
        if (quoteValues && token.contains(" ")) {
          "(\"$token\")"
        } else {
          "($token)"
        }
      }
      return "($body)"
    }

    val includeGroup = buildOrGroup(currentFilters.includeKeywords)
    if (includeGroup.isBlank()) return ""

    val mustGroups = listOfNotNull(
      includeGroup,
      buildOrGroup(currentFilters.areaNames, quoteValues = true).takeIf { it.isNotBlank() },
      buildOrGroup(currentFilters.companyWhitelistNames, quoteValues = true).takeIf { it.isNotBlank() },
      buildOrGroup(currentFilters.workFormats, quoteValues = true).takeIf { it.isNotBlank() },
      buildOrGroup(currentFilters.employmentForms, quoteValues = true).takeIf { it.isNotBlank() },
      buildOrGroup(currentFilters.schedules, quoteValues = true).takeIf { it.isNotBlank() },
      buildOrGroup(currentFilters.employmentTypes, quoteValues = true).takeIf { it.isNotBlank() },
    )

    val excludedTokens = (currentFilters.excludeTitleKeywords + currentFilters.companyBlacklistNames)
      .map { normalizeQueryToken(it) }
      .filter { it.isNotBlank() }
      .distinct()

    val baseQuery = mustGroups.joinToString(" AND ")
    if (excludedTokens.isEmpty()) return baseQuery

    val excludedGroup = excludedTokens.joinToString(" OR ") { token ->
      if (token.contains(" ")) {
        "(\"$token\")"
      } else {
        "($token)"
      }
    }
    return "$baseQuery NOT ($excludedGroup)"
  }

  private fun applyTemplateToCurrentState(template: CoverLetterTemplate) {
    val resolvedResumeId = template.resumeId ?: selectedResumeId.value
    if (!resolvedResumeId.isNullOrBlank()) {
      _selectedResumeId.value = resolvedResumeId
      viewModelScope.launch(Dispatchers.IO) {
        sharedPrefs.edit().putString(SHARED_PREFS_SELECTED_RESUME_ID, resolvedResumeId).apply()
      }
    }
    _searchQuery.value = template.searchText
    _filters.value = template.filters.copy(searchText = template.searchText)
    _coverLetter.value = template.content
    _alwaysAttachCoverLetter.value = template.alwaysAttach
    persistFilters(_filters.value)
    viewModelScope.launch(Dispatchers.IO) {
      sharedPrefs.edit()
        .putString(SHARED_PREFS_COVER_LETTER, template.content)
        .putBoolean(SHARED_PREFS_ALWAYS_ATTACH_COVER_LETTER, template.alwaysAttach)
        .apply()
    }
    scheduleEstimateRefresh()
  }

  private fun shouldKeepVacancy(vacancy: Map<String, Any>, currentFilters: VacancyFilters): Boolean {
    if (!currentFilters.vacancySource.equals(SOURCE_GENERAL, ignoreCase = true)) {
      return true
    }

    val title = vacancy["name"]?.toString().orEmpty()
    val snippet = vacancy["snippet"] as? Map<String, Any>
    val summary = listOfNotNull(
      title.takeIf { it.isNotBlank() },
      snippet?.get("requirement")?.toString()?.takeIf { it.isNotBlank() },
      snippet?.get("responsibility")?.toString()?.takeIf { it.isNotBlank() }
    ).joinToString(" ")
    val summaryNorm = normalizeForMatch(summary)
    val titleNorm = normalizeForMatch(title)

    if (currentFilters.excludeTitleKeywords.isNotEmpty()) {
      val excludeTitleKeywords = currentFilters.excludeTitleKeywords.map { normalizeForMatch(it) }
      if (excludeTitleKeywords.any { token -> token.isNotBlank() && (summaryNorm.contains(token) || titleNorm.contains(token)) }) return false
    }

    val employerName = normalizeForMatch((vacancy["employer"] as? Map<String, Any>)?.get("name")?.toString().orEmpty())
    if (currentFilters.companyWhitelistNames.isNotEmpty()) {
      val whitelist = currentFilters.companyWhitelistNames.map { normalizeForMatch(it) }
      if (whitelist.none { token -> token.isNotBlank() && employerName.contains(token) }) return false
    }
    if (currentFilters.companyBlacklistNames.isNotEmpty()) {
      val blacklist = currentFilters.companyBlacklistNames.map { normalizeForMatch(it) }
      if (blacklist.any { token -> token.isNotBlank() && employerName.contains(token) }) return false
    }

    if (currentFilters.onlyWithSalary && vacancy["salary"] == null) return false
    if (!salaryMatches(vacancy["salary"] as? Map<String, Any>, currentFilters)) return false

    val areaName = normalizeForMatch((vacancy["area"] as? Map<String, Any>)?.get("name")?.toString().orEmpty())
    if (currentFilters.areaNames.isNotEmpty()) {
      val areas = currentFilters.areaNames.map { normalizeForMatch(it) }
      if (areas.none { token -> token.isNotBlank() && areaName.contains(token) }) return false
    }

    currentFilters.searchRadiusKm?.let { radius ->
      val distance = (vacancy["sort_point_distance"] as? Number)?.toDouble()
      if (distance != null && distance > radius.toDouble()) return false
    }

    if (!matchAnyFilter(
        currentFilters.workFormats,
        extractNames(vacancy["work_format"]) + extractNames(vacancy["workFormat"]) + extractNames(vacancy["work_format_by_days"])
      )
    ) {
      return false
    }

    if (!matchAnyFilter(currentFilters.schedules, extractNames(vacancy["schedule"]))) return false
    if (!matchAnyFilter(currentFilters.employmentTypes, extractNames(vacancy["employment"]))) return false
    if (!matchAnyFilter(currentFilters.employmentForms, extractNames(vacancy["employment_form"]))) return false

    return true
  }

  private fun salaryMatches(salary: Map<String, Any>?, currentFilters: VacancyFilters): Boolean {
    val from = (salary?.get("from") as? Number)?.toInt()
    val to = (salary?.get("to") as? Number)?.toInt()
    val minWanted = currentFilters.salaryFrom
    val maxWanted = currentFilters.salaryTo

    if (minWanted != null) {
      val top = to ?: from
      if (top != null && top < minWanted) return false
    }
    if (maxWanted != null) {
      val low = from ?: to
      if (low != null && low > maxWanted) return false
    }
    return true
  }

  private fun extractNames(raw: Any?): List<String> {
    return when (raw) {
      is Map<*, *> -> listOfNotNull(raw["name"]?.toString(), raw["id"]?.toString())
      is List<*> -> raw.flatMap { item -> extractNames(item) }
      is String -> listOf(raw)
      else -> emptyList()
    }
  }

  private fun matchAnyFilter(filterValues: List<String>, actualValues: List<String>): Boolean {
    if (filterValues.isEmpty()) return true
    val actualNorm = actualValues.flatMap { canonicalTokens(it) }.toSet()
    val filterNorm = filterValues.flatMap { canonicalTokens(it) }.toSet()
    if (actualNorm.isEmpty() || filterNorm.isEmpty()) return false
    return filterNorm.any { fv -> actualNorm.any { av -> av.contains(fv) || fv.contains(av) } }
  }

  private fun matchesRegex(text: String, pattern: String): Boolean {
    if (pattern.isBlank()) return false
    return try {
      Regex(pattern, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(text)
    } catch (_: Exception) {
      false
    }
  }

  fun toggleAutoUpdateResume(enabled: Boolean) {
    toggleBackgroundResumeUpdateEnabled(enabled)
  }

  fun toggleBackgroundApplyEnabled(enabled: Boolean) {
    val updated = automationSettings.value.copy(backgroundApplyEnabled = enabled)
    _automationSettings.value = updated
    persistAutomationSettings(updated)
    if (enabled) {
      startVacancyApplyAutomation(
        enqueueImmediate = updated.runApplyImmediatelyOnEnable,
        showSnackbar = true
      )
    } else {
      stopVacancyApplyAutomation(showSnackbar = true)
    }
  }

  fun toggleBackgroundResumeUpdateEnabled(enabled: Boolean) {
    val updated = automationSettings.value.copy(backgroundResumeUpdateEnabled = enabled)
    _automationSettings.value = updated
    persistAutomationSettings(updated)
    if (enabled) {
      startResumeUpdateAutomation(enqueueImmediate = true, showSnackbar = true)
    } else {
      stopResumeUpdateAutomation(showSnackbar = true)
    }
  }

  fun startVacancyApplyAutomation(enqueueImmediate: Boolean = true, showSnackbar: Boolean = true) {
    val resumeId = selectedResumeId.value
    if (resumeId.isNullOrEmpty()) {
      if (showSnackbar) {
        viewModelScope.launch {
          snackbarHostState.showSnackbar("Выберите резюме для запуска рассылки откликов.")
        }
      }
      return
    }

    if (enqueueImmediate) {
      enqueueVacancyApplyOneTimeWork(
        resumeId,
        searchQuery.value,
        coverLetter.value,
        alwaysAttachCoverLetter.value,
        filters.value
      )
    }
    enqueueVacancyApplyPeriodicWork(
      resumeId,
      searchQuery.value,
      coverLetter.value,
      alwaysAttachCoverLetter.value,
      filters.value
    )
    if (showSnackbar) {
      viewModelScope.launch {
        snackbarHostState.showSnackbar("Рассылка откликов запущена (фоновый режим активен).")
      }
    }
  }

  fun stopVacancyApplyAutomation(showSnackbar: Boolean = true) {
    WorkManager.getInstance(context).cancelUniqueWork(VACANCY_APPLY_PERIODIC_WORK_NAME)
    WorkManager.getInstance(context).cancelUniqueWork(VACANCY_APPLY_ONE_TIME_WORK_NAME)
    if (showSnackbar) {
      viewModelScope.launch {
        snackbarHostState.showSnackbar("Рассылка откликов остановлена.")
      }
    }
  }

  fun startResumeUpdateAutomation(enqueueImmediate: Boolean = true, showSnackbar: Boolean = true) {
    val resumeId = selectedResumeId.value
    if (resumeId.isNullOrEmpty()) {
      if (showSnackbar) {
        viewModelScope.launch {
          snackbarHostState.showSnackbar("Выберите резюме для запуска автоподнятия.")
        }
      }
      stopResumeUpdateAutomation(showSnackbar = false)
      return
    }

    if (enqueueImmediate) {
      enqueueResumeUpdateOneTimeWork(resumeId)
    }
    enqueueResumeUpdatePeriodicWork(resumeId)
    if (showSnackbar) {
      viewModelScope.launch {
        snackbarHostState.showSnackbar("Обновление резюме активно (фоновый режим).")
      }
    }
  }

  fun stopResumeUpdateAutomation(showSnackbar: Boolean = true) {
    WorkManager.getInstance(context).cancelUniqueWork(RESUME_UPDATE_PERIODIC_WORK_NAME)
    WorkManager.getInstance(context).cancelUniqueWork(RESUME_UPDATE_ONE_TIME_WORK_NAME)
    if (showSnackbar) {
      viewModelScope.launch {
        snackbarHostState.showSnackbar("Обновление резюме остановлено.")
      }
    }
  }

  private fun enqueueVacancyApplyOneTimeWork(
    resumeId: String,
    query: String,
    coverLetter: String,
    alwaysAttach: Boolean,
    filters: VacancyFilters
  ) {
    val inputData = workDataOf(
      "resume_id" to resumeId,
      "query" to query,
      "cover_letter" to coverLetter,
      "always_attach" to alwaysAttach,
      "filters_json" to filters.toJsonString()
    )

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .setRequiresBatteryNotLow(true)
      .build()

    val oneTimeRequest = OneTimeWorkRequestBuilder<VacancyApplyWorker>()
      .setInputData(inputData)
      .setConstraints(constraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
      .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
      VACANCY_APPLY_ONE_TIME_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      oneTimeRequest
    )
  }

  private fun enqueueVacancyApplyPeriodicWork(
    resumeId: String,
    query: String,
    coverLetter: String,
    alwaysAttach: Boolean,
    filters: VacancyFilters
  ) {
    val inputData = workDataOf(
      "resume_id" to resumeId,
      "query" to query,
      "cover_letter" to coverLetter,
      "always_attach" to alwaysAttach,
      "filters_json" to filters.toJsonString()
    )

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .setRequiresBatteryNotLow(true)
      .build()

    val intervalHours = automationSettings.value.applyIntervalHours.coerceAtLeast(1).toLong()
    val vacancyApplyRequest = PeriodicWorkRequestBuilder<VacancyApplyWorker>(
      intervalHours, TimeUnit.HOURS,
      15, TimeUnit.MINUTES
    )
      .setInputData(inputData)
      .setConstraints(constraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
      .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      VACANCY_APPLY_PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      vacancyApplyRequest
    )
  }

  private fun enqueueResumeUpdateOneTimeWork(resumeId: String) {
    val inputData = workDataOf(
      "resume_id" to resumeId
    )

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .setRequiresBatteryNotLow(true)
      .build()

    val oneTimeRequest = OneTimeWorkRequestBuilder<ResumeUpdateWorker>()
      .setInputData(inputData)
      .setConstraints(constraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
      RESUME_UPDATE_ONE_TIME_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      oneTimeRequest
    )
  }

  private fun enqueueResumeUpdatePeriodicWork(resumeId: String) {
    val inputData = workDataOf(
      "resume_id" to resumeId
    )

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .setRequiresBatteryNotLow(true)
      .build()

    val intervalHours = automationSettings.value.resumeUpdateIntervalHours.coerceAtLeast(1).toLong()
    val resumeUpdateRequest = PeriodicWorkRequestBuilder<ResumeUpdateWorker>(
      intervalHours, TimeUnit.HOURS,
      15, TimeUnit.MINUTES
    )
      .setInputData(inputData)
      .setConstraints(constraints)
      .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      RESUME_UPDATE_PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      resumeUpdateRequest
    )
  }

  override fun onCleared() {
    super.onCleared()
    //stopVacancyApplyAutomation()
    //stopResumeUpdateAutomation()
  }
}
