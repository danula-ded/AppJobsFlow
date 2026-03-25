package com.github.jobsflow.appjobsflow.ui.home

import org.json.JSONArray
import org.json.JSONObject

data class VacancyFilters(
  val vacancySource: String = "recommended",
  val searchText: String = "",
  val includeKeywords: List<String> = emptyList(),
  val excludeTitleKeywords: List<String> = emptyList(),
  val salaryFrom: Int? = null,
  val salaryTo: Int? = null,
  val onlyWithSalary: Boolean = false,
  val areaNames: List<String> = emptyList(),
  val metroNames: List<String> = emptyList(),
  val searchRadiusKm: Int? = null,
  val workFormats: List<String> = emptyList(),
  val employmentForms: List<String> = emptyList(),
  val schedules: List<String> = emptyList(),
  val employmentTypes: List<String> = emptyList(),
  val companyWhitelistNames: List<String> = emptyList(),
  val companyBlacklistNames: List<String> = emptyList(),
  val excludeTitleRegex: String = "",
  val excludeTextRegex: String = "",
  val skillsIncludeAny: List<String> = emptyList(),
  val skillsIncludeAll: List<String> = emptyList(),
  val skillsExcludeAny: List<String> = emptyList(),
) {
  fun toJsonString(): String {
    val json = JSONObject().apply {
      put("vacancy_source", vacancySource)
      put("search_text", searchText)
      put("include_keywords", JSONArray(includeKeywords))
      put("exclude_title_keywords", JSONArray(excludeTitleKeywords))
      put("salary_from", salaryFrom)
      put("salary_to", salaryTo)
      put("only_with_salary", onlyWithSalary)
      put("area_names", JSONArray(areaNames))
      put("metro_names", JSONArray(metroNames))
      put("search_radius_km", searchRadiusKm)
      put("work_formats", JSONArray(workFormats))
      put("employment_forms", JSONArray(employmentForms))
      put("schedules", JSONArray(schedules))
      put("employment_types", JSONArray(employmentTypes))
      put("company_whitelist_names", JSONArray(companyWhitelistNames))
      put("company_blacklist_names", JSONArray(companyBlacklistNames))
      put("exclude_title_regex", excludeTitleRegex)
      put("exclude_text_regex", excludeTextRegex)
      put("skills_include_any", JSONArray(skillsIncludeAny))
      put("skills_include_all", JSONArray(skillsIncludeAll))
      put("skills_exclude_any", JSONArray(skillsExcludeAny))
    }
    return json.toString()
  }

  companion object {
    fun fromJsonString(raw: String?): VacancyFilters {
      if (raw.isNullOrBlank()) return VacancyFilters()
      return try {
        val json = JSONObject(raw)
        VacancyFilters(
          vacancySource = json.optString("vacancy_source", "recommended"),
          searchText = json.optString("search_text", ""),
          includeKeywords = json.optStringList("include_keywords"),
          excludeTitleKeywords = json.optStringList("exclude_title_keywords"),
          salaryFrom = json.optIntOrNull("salary_from"),
          salaryTo = json.optIntOrNull("salary_to"),
          onlyWithSalary = json.optBoolean("only_with_salary", false),
          areaNames = json.optStringList("area_names"),
          metroNames = json.optStringList("metro_names"),
          searchRadiusKm = json.optIntOrNull("search_radius_km"),
          workFormats = json.optStringList("work_formats"),
          employmentForms = json.optStringList("employment_forms"),
          schedules = json.optStringList("schedules"),
          employmentTypes = json.optStringList("employment_types"),
          companyWhitelistNames = json.optStringList("company_whitelist_names"),
          companyBlacklistNames = json.optStringList("company_blacklist_names"),
          excludeTitleRegex = json.optString("exclude_title_regex", ""),
          excludeTextRegex = json.optString("exclude_text_regex", ""),
          skillsIncludeAny = json.optStringList("skills_include_any"),
          skillsIncludeAll = json.optStringList("skills_include_all"),
          skillsExcludeAny = json.optStringList("skills_exclude_any")
        )
      } catch (_: Exception) {
        VacancyFilters()
      }
    }
  }
}

data class CoverLetterTemplate(
  val id: String,
  val name: String,
  val content: String,
  val resumeId: String? = null,
  val searchText: String = "",
  val filters: VacancyFilters = VacancyFilters(),
  val alwaysAttach: Boolean = false,
  val enabled: Boolean = false,
) {
  fun toJson(): JSONObject {
    return JSONObject().apply {
      put("id", id)
      put("name", name)
      put("content", content)
      put("resume_id", resumeId)
      put("search_text", searchText)
      put("filters_json", filters.toJsonString())
      put("always_attach", alwaysAttach)
      put("enabled", enabled)
    }
  }

  companion object {
    fun fromJson(json: JSONObject): CoverLetterTemplate {
      return CoverLetterTemplate(
        id = json.optString("id", ""),
        name = json.optString("name", ""),
        content = json.optString("content", ""),
        resumeId = json.optString("resume_id", "").ifBlank { null },
        searchText = json.optString("search_text", ""),
        filters = VacancyFilters.fromJsonString(json.optString("filters_json", null)),
        alwaysAttach = json.optBoolean("always_attach", false),
        enabled = json.optBoolean("enabled", false)
      )
    }
  }
}

fun coverLetterTemplatesToJson(templates: List<CoverLetterTemplate>): String {
  val jsonArray = JSONArray()
  templates.forEach { jsonArray.put(it.toJson()) }
  return jsonArray.toString()
}

fun coverLetterTemplatesFromJson(raw: String?): List<CoverLetterTemplate> {
  if (raw.isNullOrBlank()) return emptyList()
  return try {
    val arr = JSONArray(raw)
    buildList {
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val template = CoverLetterTemplate.fromJson(obj)
        if (template.id.isNotBlank() && template.name.isNotBlank()) {
          add(template)
        }
      }
    }
  } catch (_: Exception) {
    emptyList()
  }
}

data class AutomationSettings(
  val backgroundApplyEnabled: Boolean = true,
  val backgroundResumeUpdateEnabled: Boolean = true,
  val runApplyImmediatelyOnEnable: Boolean = true,
  val applyIntervalHours: Int = 24,
  val resumeUpdateIntervalHours: Int = 4,
) {
  fun toJsonString(): String {
    val json = JSONObject().apply {
      put("background_apply_enabled", backgroundApplyEnabled)
      put("background_resume_update_enabled", backgroundResumeUpdateEnabled)
      put("run_apply_immediately_on_enable", runApplyImmediatelyOnEnable)
      put("apply_interval_hours", applyIntervalHours)
      put("resume_update_interval_hours", resumeUpdateIntervalHours)
    }
    return json.toString()
  }

  companion object {
    fun fromJsonString(raw: String?): AutomationSettings {
      if (raw.isNullOrBlank()) return AutomationSettings()
      return try {
        val json = JSONObject(raw)
        AutomationSettings(
          backgroundApplyEnabled = json.optBoolean("background_apply_enabled", true),
          backgroundResumeUpdateEnabled = json.optBoolean("background_resume_update_enabled", true),
          runApplyImmediatelyOnEnable = json.optBoolean("run_apply_immediately_on_enable", true),
          applyIntervalHours = json.optInt("apply_interval_hours", 24),
          resumeUpdateIntervalHours = json.optInt("resume_update_interval_hours", 4)
        )
      } catch (_: Exception) {
        AutomationSettings()
      }
    }
  }
}

private fun JSONObject.optStringList(key: String): List<String> {
  val arr = optJSONArray(key) ?: return emptyList()
  val values = mutableListOf<String>()
  for (i in 0 until arr.length()) {
    val v = arr.optString(i, "").trim()
    if (v.isNotEmpty()) values.add(v)
  }
  return values
}

private fun JSONObject.optIntOrNull(key: String): Int? {
  if (!has(key) || isNull(key)) return null
  val raw = opt(key)
  return when (raw) {
    is Number -> raw.toInt()
    is String -> raw.toIntOrNull()
    else -> null
  }
}

fun parseCsvList(raw: String): List<String> {
  return raw
    .split(',', ';', '\n')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinct()
}

fun toCsvString(items: List<String>): String = items.joinToString(", ")
