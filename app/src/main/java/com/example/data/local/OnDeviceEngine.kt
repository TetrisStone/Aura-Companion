package com.example.data.local

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.example.data.model.AccelerationMode
import com.example.data.model.LlmSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OnDeviceEngine (Singleton): Lädt das Modell einmal und hält es im Speicher.
 * Alle nativen Aufrufe laufen auf einem eigenen Single-Thread-Dispatcher.
 * Reagiert auf TRIM_MEMORY_COMPLETE, um Speicher freizugeben.
 */
class OnDeviceEngine private constructor(private val context: Context) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "OnDeviceEngine"

        @Volatile
        private var instance: OnDeviceEngine? = null

        fun getInstance(context: Context): OnDeviceEngine =
            instance ?: synchronized(this) {
                instance ?: OnDeviceEngine(context.applicationContext).also {
                    instance = it
                    it.registerMemoryCallback()
                }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val singleThreadDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(singleThreadDispatcher + SupervisorJob())

    private val inferenceEngine: InferenceEngine by lazy {
        AiChat.getInferenceEngine(context)
    }

    // Cached model parameters to detect when a reload is necessary
    private var loadedModelPath: String? = null
    private var loadedAccelMode: AccelerationMode? = null
    private var loadedContextLength: Int = 0
    private var loadedCpuThreads: Int = 0

    private var currentSystemPrompt: String? = null
    private var lastRecordedDiagnostics: String = ""

    private fun registerMemoryCallback() {
        try {
            context.registerComponentCallbacks(this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register ComponentCallbacks", e)
        }
    }

    fun getLastDiagnostics(): String {
        val diag = inferenceEngine.getDiagnostics()
        return if (diag.isNotBlank()) diag else lastRecordedDiagnostics
    }

    /**
     * Ensures the model is loaded with current settings.
     * Reloads if model path, acceleration or context length changed.
     */
    suspend fun ensureModelLoaded(settings: LlmSettings): Result<Unit> = withContext(singleThreadDispatcher) {
        val modelPath = settings.activeLocalModelPath
        if (modelPath.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Kein lokales GGUF-Modell ausgewählt. Bitte importiere und aktiviere zuerst ein Modell im Tab 'Lokale Modelle'.")
            )
        }

        val file = File(modelPath)
        if (!file.exists() || !file.canRead()) {
            return@withContext Result.failure(
                IllegalStateException("Die Modelldatei '${file.name}' wurde nicht gefunden oder ist nicht lesbar.")
            )
        }

        val needsReload = (loadedModelPath != modelPath) ||
                (loadedAccelMode != settings.accelerationMode) ||
                (loadedContextLength != settings.contextLength) ||
                (loadedCpuThreads != settings.cpuThreads)

        if (needsReload) {
            Log.i(TAG, "Loading model: $modelPath with accel: ${settings.accelerationMode}, ctx: ${settings.contextLength}, threads: ${settings.cpuThreads}")
            try {
                inferenceEngine.loadModel(
                    pathToModel = modelPath,
                    nGpuLayers = settings.accelerationMode.nGpuLayers,
                    contextSize = settings.contextLength,
                    threads = settings.cpuThreads,
                    temperature = settings.temperature,
                    topP = settings.topP
                )

                loadedModelPath = modelPath
                loadedAccelMode = settings.accelerationMode
                loadedContextLength = settings.contextLength
                loadedCpuThreads = settings.cpuThreads
                currentSystemPrompt = null // reset prompt after load

                lastRecordedDiagnostics = inferenceEngine.getDiagnostics()
                Log.i(TAG, "On-device model successfully loaded! Diag: $lastRecordedDiagnostics")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                loadedModelPath = null
                return@withContext Result.failure(
                    IllegalStateException("Laden des Modells fehlgeschlagen: ${e.localizedMessage ?: e.message}")
                )
            }
        }

        Result.success(Unit)
    }

    /**
     * Sends the system prompt to the engine if changed or after reload.
     */
    suspend fun setSystemPromptIfNeeded(prompt: String): Result<Unit> = withContext(singleThreadDispatcher) {
        if (currentSystemPrompt == prompt) {
            return@withContext Result.success(Unit)
        }
        try {
            inferenceEngine.setSystemPrompt(prompt)
            currentSystemPrompt = prompt
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set system prompt", e)
            Result.failure(e)
        }
    }

    /**
     * Streams generated tokens from on-device llama.cpp
     */
    fun generate(userMessage: String, predictLength: Int): Flow<String> {
        return inferenceEngine.sendUserPrompt(userMessage, predictLength)
    }

    fun unloadModel() {
        scope.launch {
            try {
                Log.i(TAG, "Unloading on-device model...")
                inferenceEngine.cleanUp()
                loadedModelPath = null
                loadedAccelMode = null
                loadedContextLength = 0
                loadedCpuThreads = 0
                currentSystemPrompt = null
                Log.i(TAG, "On-device model unloaded.")
            } catch (e: Exception) {
                Log.e(TAG, "Error unloading model", e)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            Log.w(TAG, "TRIM_MEMORY_COMPLETE received: Unloading on-device model from RAM")
            unloadModel()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    override fun onLowMemory() {
        Log.w(TAG, "onLowMemory received: Unloading on-device model from RAM")
        unloadModel()
    }
}
