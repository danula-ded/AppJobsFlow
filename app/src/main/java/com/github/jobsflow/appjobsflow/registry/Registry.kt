// Можно использовать для хранения общих сервисов по имени
package com.github.jobsflow.appjobsflow.registry

object Registry {
    private val registryMap = mutableMapOf<String, Any>()

    fun register(name: String, value: Any) {
        registryMap[name] = value
    }

    fun <T> get(name: String): T? {
        return registryMap[name] as? T
    }
}
