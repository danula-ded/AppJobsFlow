package com.github.jobsflow.appjobsflow.worker

import android.content.Context
import android.util.Log
import androidx.work.WorkerParameters
import com.github.jobsflow.appjobsflow.api.ApiClient
import com.github.jobsflow.appjobsflow.api.ApiException
import com.github.jobsflow.appjobsflow.api.LimitExceededException
import com.github.jobsflow.appjobsflow.ui.home.VacancyFilters
import kotlinx.coroutines.delay
import kotlin.random.Random

class VacancyApplyWorker(
    context: Context,
    private val params: WorkerParameters
) : NotificationWorker(context, params) {

    private val TAG = "VacancyApplyWorker"
    private val SOURCE_RECOMMENDED = "recommended"
    private val SOURCE_GENERAL = "general"

    private val resumeId = inputData.getString("resume_id")
    private val searchQuery = inputData.getString("query")
    private val filtersJson = inputData.getString("filters_json")
    private val coverLetter = inputData.getString("cover_letter")
    private val alwaysAttach = inputData.getBoolean("always_attach", false)
    private val filters: VacancyFilters by lazy {
        val parsed = VacancyFilters.fromJsonString(filtersJson)
        if (parsed.searchText.isBlank() && !searchQuery.isNullOrBlank()) {
            parsed.copy(searchText = searchQuery.trim())
        } else {
            parsed
        }
    }
    private val sourceMode: String
        get() = if (filters.vacancySource.equals(SOURCE_GENERAL, ignoreCase = true)) SOURCE_GENERAL else SOURCE_RECOMMENDED

    private val sharedPrefs = context.getSharedPreferences("appjobsflow", Context.MODE_PRIVATE)
    private val client = ApiClient(sharedPrefs = sharedPrefs)

    private var firstName: String = ""
    private var lastName: String = ""

    override suspend fun doWork(): Result {
        if (resumeId.isNullOrEmpty()) {
            val errorMessage = "Ошибка: Не указан ID резюме."
            Log.e(TAG, errorMessage)
            showNotification("❗ $errorMessage")
            return Result.failure()
        }

        showNotification("📣 Рассылка откликов запущена...")

        try {
            val meResponse = client.api("GET", "/me")
            this.firstName = meResponse["first_name"] as? String ?: ""
            this.lastName = meResponse["last_name"] as? String ?: ""
            Log.d(TAG, "Получены данные пользователя: $firstName $lastName")
        } catch (e: Exception) {
            val errorMessage = "Ошибка при получении данных пользователя: ${e.message ?: "Неизвестная ошибка"}"
            Log.e(TAG, errorMessage, e)
            showNotification("❗ $errorMessage")
            return Result.retry()
        }

        return try {
            applySimilarVacancies()
            showNotification("✅ Все отклики отправлены.")
            Result.success()
        } catch (e: LimitExceededException) {
            Log.w(TAG, "Достигнут лимит откликов. Планируем повторную попытку позже.", e)
            showNotification("⚠️ Достигнут лимит откликов. Повторим попытку позже.")
            Result.retry()
        } catch (e: Exception) {
            val errorMessage = "Ошибка при рассылке: ${e.message ?: "Неизвестная ошибка"}"
            Log.e(TAG, errorMessage, e)
            showNotification("❗ $errorMessage")
            Result.retry()
        }
    }

    private suspend fun applySimilarVacancies() {
        val processedVacancyIds = mutableSetOf<String>()
        val skipReasonStats = mutableMapOf<String, Int>()
        var fetchedItemsTotal = 0
        var skippedTotal = 0
        var duplicateTotal = 0
        var appliedTotal = 0

        Log.i(
            TAG,
            "Apply run start: source=$sourceMode resumeId=$resumeId searchText='${filters.searchText.trim()}' includeKeywords=${filters.includeKeywords.size} excludeTitleKeywords=${filters.excludeTitleKeywords.size} salaryFrom=${filters.salaryFrom} salaryTo=${filters.salaryTo} alwaysAttach=$alwaysAttach"
        )

        suspend fun processVacancy(vacancy: Map<String, Any>) {
            if (isStopped) {
                Log.i(TAG, "Worker is stopped. Breaking vacancy loop.")
                return
            }
            val skipReason = shouldSkipVacancy(vacancy)
            if (skipReason != null) {
                skippedTotal += 1
                skipReasonStats[skipReason] = (skipReasonStats[skipReason] ?: 0) + 1
                Log.d(TAG, "Skipping vacancy with ID ${vacancy["id"]}: $skipReason")
                return
            }

            val vacancyId = vacancy["id"]?.toString()
            if (vacancyId.isNullOrEmpty()) {
                skippedTotal += 1
                skipReasonStats["empty_id"] = (skipReasonStats["empty_id"] ?: 0) + 1
                Log.w(TAG, "Vacancy ID is null or empty, skipping vacancy: $vacancy")
                return
            }
            if (!processedVacancyIds.add(vacancyId)) {
                duplicateTotal += 1
                return
            }

            val vacancyName = vacancy["name"]?.toString() ?: "Вакансия (ID: $vacancyId)"
            if (Random.nextInt(100) < 50) {
                try {
                    val viewDelayMillis = Random.nextLong(1000, 3000)
                    Log.d(TAG, "Задержка на $viewDelayMillis мс перед просмотром вакансии.")
                    delay(viewDelayMillis)
                    Log.d(TAG, "Просмотр вакансии '$vacancyName'.")
                    client.api("GET", "/vacancies/$vacancyId")

                    if (Random.nextInt(100) < 25) {
                        val employerId = (vacancy["employer"] as? Map<String, Any>)?.get("id")?.toString()
                        if (!employerId.isNullOrEmpty()) {
                            val employerViewDelayMillis = Random.nextLong(1000, 3000)
                            Log.d(TAG, "Задержка на $employerViewDelayMillis мс перед просмотром работодателя.")
                            delay(employerViewDelayMillis)
                            Log.d(TAG, "Просмотр аккаунта работодателя #$employerId для вакансии '$vacancyName'.")
                            client.api("GET", "/employers/$employerId")
                        }
                    }
                } catch (e: ApiException) {
                    val errorMessage = "Ошибка API при просмотре '$vacancyName': ${e.message}"
                    Log.w(TAG, errorMessage, e)
                    showNotification("❗ $errorMessage")
                    return
                }
            }

            val payload = mutableMapOf<String, Any>(
                "resume_id" to resumeId!!,
                "vacancy_id" to vacancyId
            )

            if (vacancy["response_letter_required"] == true || alwaysAttach) {
                val templateVars = mutableMapOf(
                    "vacancyName" to vacancyName,
                    "firstName" to this.firstName,
                    "lastName" to this.lastName
                )
                val message = expandTemplate(coverLetter ?: "", templateVars)
                payload["message"] = message
                Log.d(TAG, "Attaching cover letter for '$vacancyName': '$message'")
            }

            val vacancyUrl = vacancy["alternate_url"]?.toString() ?: "n/a"

            try {
                val delayMillis = Random.nextLong(3000, 5000)
                Log.d(TAG, "Ожидаем $delayMillis перед откликом.")
                delay(delayMillis)
                client.api("POST", "/negotiations", payload)
                appliedTotal += 1
                showNotification("✅ Отклик на $vacancyUrl ($vacancyName)")
                Log.i(TAG, "Successfully applied to vacancy: $vacancyUrl")
            } catch (e: LimitExceededException) {
                throw e
            } catch (e: ApiException) {
                val errorMessage = "Ошибка API при отклике на $vacancyUrl ($vacancyName): ${e.message}"
                Log.w(TAG, "${e.message}: ${e.json} (vacancy: $vacancy)")
                showNotification("❗ $errorMessage")
            }
        }

        if (sourceMode == SOURCE_GENERAL) {
            val generalSearchQuery = buildGeneralSearchQuery()
            if (generalSearchQuery.isBlank()) {
                showNotification("⚠️ Для общего списка не заданы ключевые слова для поиска.")
                Log.w(TAG, "Apply run skipped: general source selected but query is blank")
                return
            }

            Log.i(TAG, "General source query='$generalSearchQuery'")

            val perPage = 100
            var pages = 1
            var page = 0
            while (page < pages) {
                if (isStopped) return
                if (page > 1) {
                    val pageDelayMillis = Random.nextLong(1000, 3000)
                    Log.d(TAG, "Delaying for $pageDelayMillis ms before fetching next page.")
                    delay(pageDelayMillis)
                }

                val requestParams = mutableMapOf(
                    "page" to "$page",
                    "per_page" to "$perPage",
                    "text" to generalSearchQuery
                )
                filters.salaryFrom?.let { requestParams["salary"] = "$it" }

                Log.d(TAG, "Apply request [/vacancies]: page=$page plannedPages=$pages params=$requestParams")

                val response = client.api("GET", "/vacancies", requestParams)
                val items = response["items"] as? List<Map<String, Any>>
                val itemsCount = items?.size ?: 0
                fetchedItemsTotal += itemsCount
                if (items.isNullOrEmpty()) {
                    Log.w(TAG, "Apply response [/vacancies]: empty items on page=$page, stopping pagination")
                    break
                }
                if (page == 0) {
                    val totalFound = ((response["found"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
                    val apiPages = ((response["pages"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
                    val pagesByFound = if (totalFound > 0) {
                        ((totalFound + perPage - 1) / perPage).coerceAtLeast(1)
                    } else {
                        1
                    }
                    pages = when {
                        apiPages > 0 -> minOf(apiPages, pagesByFound)
                        else -> pagesByFound
                    }.coerceAtLeast(1)
                    Log.i(TAG, "Apply first page [/vacancies]: found=$totalFound pagesByFound=$pagesByFound apiPages=$apiPages finalPages=$pages")
                }

                Log.d(TAG, "Apply response [/vacancies]: page=$page items=$itemsCount fetchedTotal=$fetchedItemsTotal applied=$appliedTotal skipped=$skippedTotal duplicates=$duplicateTotal")

                for (vacancy in items) {
                    processVacancy(vacancy)
                }
                page += 1
            }
        } else {
            val perPage = 100
            var pages = 1
            var page = 0
            while (page < pages) {
                if (isStopped) {
                    Log.i(TAG, "Worker is stopped. Breaking page loop.")
                    return
                }
                if (page > 1) {
                    val pageDelayMillis = Random.nextLong(1000, 3000)
                    Log.d(TAG, "Delaying for $pageDelayMillis ms before fetching next page.")
                    delay(pageDelayMillis)
                }

                val requestParams = mutableMapOf<String, String>("page" to "$page", "per_page" to "$perPage")
                filters.searchText.takeIf { it.isNotBlank() }?.let {
                    requestParams["text"] = it.trim()
                }

                Log.d(TAG, "Apply request [/resumes/$resumeId/similar_vacancies]: page=$page plannedPages=$pages params=$requestParams")

                val response = client.api("GET", "/resumes/$resumeId/similar_vacancies", requestParams)
                val items = response["items"] as? List<Map<String, Any>>
                val itemsCount = items?.size ?: 0
                fetchedItemsTotal += itemsCount
                if (items.isNullOrEmpty()) {
                    Log.w(TAG, "Apply response [/similar_vacancies]: empty items on page=$page, stopping pagination")
                    break
                }
                if (page == 0) {
                    val totalFound = ((response["found"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
                    val apiPages = ((response["pages"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
                    val pagesByFound = if (totalFound > 0) {
                        ((totalFound + perPage - 1) / perPage).coerceAtLeast(1)
                    } else {
                        1
                    }
                    pages = when {
                        apiPages > 0 -> minOf(apiPages, pagesByFound)
                        else -> pagesByFound
                    }.coerceAtLeast(1)
                    Log.i(TAG, "Apply first page [/similar_vacancies]: found=$totalFound pagesByFound=$pagesByFound apiPages=$apiPages finalPages=$pages")
                }

                Log.d(TAG, "Apply response [/similar_vacancies]: page=$page items=$itemsCount fetchedTotal=$fetchedItemsTotal applied=$appliedTotal skipped=$skippedTotal duplicates=$duplicateTotal")

                for (vacancy in items) {
                    processVacancy(vacancy)
                }
                page += 1
            }
        }

        Log.i(
            TAG,
            "Apply run done: source=$sourceMode resumeId=$resumeId fetchedTotal=$fetchedItemsTotal uniqueProcessed=${processedVacancyIds.size} applied=$appliedTotal skipped=$skippedTotal duplicates=$duplicateTotal skipReasons=$skipReasonStats"
        )
    }

    private fun expandTemplate(input: String, vars: Map<String, String>): String {
        val pattern = Regex("\\{([^{}]+)\\}")
        var result = input

        while (true) {
            val match = pattern.find(result) ?: break
            val options = match.groupValues[1].split("|").map { it.trim() }
            result = result.replaceRange(match.range, options.randomOrNull().orEmpty())
        }

        for ((k, v) in vars) {
            result = result.replace("%$k%", v)
        }

        return result
    }

    private fun shouldSkipVacancy(vacancy: Map<String, Any>): String? {
        if (vacancy["has_test"] == true) return "has_test"
        if (vacancy["archived"] == true) return "archived"
        if ((vacancy["relations"] as? List<*>)?.isNotEmpty() == true) return "already_applied"

        if (sourceMode != SOURCE_GENERAL) {
            return null
        }

        val title = vacancy["name"]?.toString().orEmpty()
        val titleNorm = normalize(title)
        val snippet = vacancy["snippet"] as? Map<String, Any>
        val summary = listOfNotNull(
            title.takeIf { it.isNotBlank() },
            snippet?.get("requirement")?.toString()?.takeIf { it.isNotBlank() },
            snippet?.get("responsibility")?.toString()?.takeIf { it.isNotBlank() }
        ).joinToString(" ")
        val summaryNorm = normalize(summary)

        if (filters.excludeTitleKeywords.isNotEmpty()) {
            val excludeKeywords = filters.excludeTitleKeywords.map(::normalize)
            if (excludeKeywords.any { token -> titleNorm.contains(token) || summaryNorm.contains(token) }) return "exclude_title_keywords_hit"
        }

        val employerName = normalize((vacancy["employer"] as? Map<String, Any>)?.get("name")?.toString().orEmpty())
        if (filters.companyWhitelistNames.isNotEmpty()) {
            val whitelist = filters.companyWhitelistNames.map(::normalize)
            if (whitelist.none { token -> employerName.contains(token) }) return "company_not_in_whitelist"
        }
        if (filters.companyBlacklistNames.isNotEmpty()) {
            val blacklist = filters.companyBlacklistNames.map(::normalize)
            if (blacklist.any { token -> employerName.contains(token) }) return "company_blacklisted"
        }

        if (filters.onlyWithSalary && vacancy["salary"] == null) return "salary_required"
        if (!salaryMatches(vacancy["salary"] as? Map<String, Any>)) return "salary_out_of_range"

        val areaName = normalize((vacancy["area"] as? Map<String, Any>)?.get("name")?.toString().orEmpty())
        if (filters.areaNames.isNotEmpty()) {
            val areas = filters.areaNames.map(::normalize)
            if (areas.none { token -> areaName.contains(token) }) return "area_mismatch"
        }

        filters.searchRadiusKm?.let { radius ->
            val distance = (vacancy["sort_point_distance"] as? Number)?.toDouble()
            if (distance != null && distance > radius.toDouble()) return "out_of_radius"
        }

        if (!matchAnyFilter(
                filters.workFormats,
                extractNames(vacancy["work_format"]) + extractNames(vacancy["workFormat"]) + extractNames(vacancy["work_format_by_days"])
            )
        ) {
            return "work_format_mismatch"
        }

        if (!matchAnyFilter(filters.schedules, extractNames(vacancy["schedule"]))) {
            return "schedule_mismatch"
        }

        if (!matchAnyFilter(filters.employmentTypes, extractNames(vacancy["employment"]))) {
            return "employment_type_mismatch"
        }

        if (!matchAnyFilter(filters.employmentForms, extractNames(vacancy["employment_form"]))) {
            return "employment_form_mismatch"
        }

        return null
    }

    private fun salaryMatches(salary: Map<String, Any>?): Boolean {
        val from = (salary?.get("from") as? Number)?.toInt()
        val to = (salary?.get("to") as? Number)?.toInt()
        val minWanted = filters.salaryFrom
        val maxWanted = filters.salaryTo

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
        val actualNorm = actualValues.map(::normalize)
        val filterNorm = filterValues.map(::normalize)
        return filterNorm.any { fv -> actualNorm.any { av -> av.contains(fv) || fv.contains(av) } }
    }

    private fun matchesRegex(text: String, pattern: String): Boolean {
        if (pattern.isBlank()) return false
        return try {
            Regex(pattern, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(text)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid regex '$pattern': ${e.message}")
            false
        }
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase()
    }

    private fun buildGeneralSearchQuery(): String {
        val includeParts = filters.includeKeywords
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val includeQuery = includeParts
            .joinToString(" OR ") { "(${it})" }
            .trim()

        val manualQuery = filters.searchText.trim()
        return when {
            includeQuery.isNotBlank() && manualQuery.isNotBlank() -> "($includeQuery) OR ($manualQuery)"
            includeQuery.isNotBlank() -> includeQuery
            else -> manualQuery
        }
    }
}
