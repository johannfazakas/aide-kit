package ro.jf.ai.assistant.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import ro.jf.ai.assistant.client.apiHttpClient
import ro.jf.ai.assistant.presentation.DEFAULT_SERVICE_PORT
import ro.jf.ai.assistant.presentation.ScreenModels
import ro.jf.ai.assistant.presentation.createScreenModels

class AppViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("aide-kit", Context.MODE_PRIVATE)
    private val httpClient: HttpClient = apiHttpClient()

    private var generationScope = newGenerationScope()
    private val mutableModels = MutableStateFlow(buildModels(storedBaseUrl()))
    val models: StateFlow<ScreenModels> = mutableModels.asStateFlow()

    fun updateBaseUrl(baseUrl: String) {
        val normalized = normalizeBaseUrl(baseUrl) ?: return
        if (normalized == mutableModels.value.baseUrl) return
        preferences.edit().putString(KEY_SERVER_BASE_URL, normalized).apply()
        generationScope.cancel()
        generationScope = newGenerationScope()
        mutableModels.value = buildModels(normalized)
    }

    private fun newGenerationScope(): CoroutineScope =
        CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext.job))

    private fun storedBaseUrl(): String =
        preferences.getString(KEY_SERVER_BASE_URL, DEFAULT_SERVER_BASE_URL) ?: DEFAULT_SERVER_BASE_URL

    private fun buildModels(baseUrl: String): ScreenModels = createScreenModels(httpClient, baseUrl, generationScope)

    override fun onCleared() {
        httpClient.close()
    }

    companion object {
        const val DEFAULT_SERVER_BASE_URL = "http://10.0.2.2:$DEFAULT_SERVICE_PORT"
        private const val KEY_SERVER_BASE_URL = "serverBaseUrl"

        fun normalizeBaseUrl(input: String): String? {
            val trimmed = input.trim().removeSuffix("/")
            if (trimmed.isEmpty()) return null
            return if ("://" in trimmed) trimmed else "http://$trimmed"
        }
    }
}
