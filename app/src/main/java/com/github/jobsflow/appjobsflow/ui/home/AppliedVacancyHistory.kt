package com.github.jobsflow.appjobsflow.ui.home

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

const val SHARED_PREFS_APPLIED_VACANCIES_JSON = "applied_vacancies_history_json"
private const val MAX_APPLIED_VACANCIES_HISTORY = 1000

data class AppliedVacancyRecord(
  val vacancyId: String,
  val vacancyName: String,
  val companyName: String,
  val areaName: String,
  val vacancyUrl: String,
  val resumeId: String,
  val source: String,
  val appliedAtMs: Long,
) {
  fun toJson(): JSONObject = JSONObject().apply {
    put("vacancy_id", vacancyId)
    put("vacancy_name", vacancyName)
    put("company_name", companyName)
    put("area_name", areaName)
    put("vacancy_url", vacancyUrl)
    put("resume_id", resumeId)
    put("source", source)
    put("applied_at_ms", appliedAtMs)
  }

  companion object {
    fun fromJson(obj: JSONObject): AppliedVacancyRecord? {
      val vacancyId = obj.optString("vacancy_id", "").trim()
      if (vacancyId.isBlank()) return null
      return AppliedVacancyRecord(
        vacancyId = vacancyId,
        vacancyName = obj.optString("vacancy_name", "").trim(),
        companyName = obj.optString("company_name", "").trim(),
        areaName = obj.optString("area_name", "").trim(),
        vacancyUrl = obj.optString("vacancy_url", "").trim(),
        resumeId = obj.optString("resume_id", "").trim(),
        source = obj.optString("source", "").trim(),
        appliedAtMs = obj.optLong("applied_at_ms", 0L)
      )
    }
  }
}

fun appliedVacanciesFromJson(raw: String?): List<AppliedVacancyRecord> {
  if (raw.isNullOrBlank()) return emptyList()
  return try {
    val arr = JSONArray(raw)
    buildList {
      for (i in 0 until arr.length()) {
        val record = arr.optJSONObject(i)?.let { AppliedVacancyRecord.fromJson(it) } ?: continue
        add(record)
      }
    }
  } catch (_: Exception) {
    emptyList()
  }
}

fun appliedVacanciesToJson(items: List<AppliedVacancyRecord>): String {
  val arr = JSONArray()
  items.forEach { arr.put(it.toJson()) }
  return arr.toString()
}

object AppliedVacancyHistoryStore {
  private val lock = Any()

  fun load(sharedPrefs: SharedPreferences): List<AppliedVacancyRecord> {
    return appliedVacanciesFromJson(sharedPrefs.getString(SHARED_PREFS_APPLIED_VACANCIES_JSON, null))
      .sortedByDescending { it.appliedAtMs }
  }

  fun append(sharedPrefs: SharedPreferences, record: AppliedVacancyRecord) {
    synchronized(lock) {
      val current = appliedVacanciesFromJson(sharedPrefs.getString(SHARED_PREFS_APPLIED_VACANCIES_JSON, null)).toMutableList()
      current.add(record)
      val trimmed = current
        .asReversed()
        .distinctBy { it.vacancyId }
        .take(MAX_APPLIED_VACANCIES_HISTORY)
        .toList()
        .asReversed()
      sharedPrefs.edit().putString(SHARED_PREFS_APPLIED_VACANCIES_JSON, appliedVacanciesToJson(trimmed)).apply()
    }
  }

  fun clear(sharedPrefs: SharedPreferences) {
    synchronized(lock) {
      sharedPrefs.edit().remove(SHARED_PREFS_APPLIED_VACANCIES_JSON).apply()
    }
  }
}
