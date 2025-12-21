package com.github.jobsflow.appjobsflow.api

import com.github.jobsflow.appjobsflow.BuildConfig

object ApiConstants {
    val CLIENT_ID: String = BuildConfig.HH_CLIENT_ID
    val CLIENT_SECRET: String = BuildConfig.HH_CLIENT_SECRET
    val REDIRECT_URI: String = BuildConfig.HH_REDIRECT_URI
    const val API_URL = "https://api.hh.ru/"
    const val OAUTH_URL = "https://hh.ru/oauth"
}
