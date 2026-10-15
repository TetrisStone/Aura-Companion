package com.arm.aichat.internal

import android.content.Context
import android.util.Log
import com.arm.aichat.InferenceEngine
import com.arm.aichat.UnsupportedArchitectureException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal class InferenceEngineImpl private constructor(
    private val nativeLibDir: String
) : InferenceEngine {

    companion object {
        private val TAG = InferenceEngineImpl::class.java.simpleName

        @Volatile
        private var instance: InferenceEngine? = null

        internal fun getInstance(context: Context): InferenceEngine =
            instance ?: synchronized(this) {
                instance ?: run {
                    val nativeLibDir = context.applicationInfo.nativeLibraryDir
                    require(nativeLibDir.isNotBlank()) { "Expected a valid native library path!" }
                    Log.i(TAG, "Instantiating InferenceEngineImpl from $nativeLibDir")
                    InferenceEngineImpl(nativeLibDir).also { instance = it }
                }
            }
    }

    private external fun init(nativeLibDir: String)

    private external fun load(modelPath: String, nGpuLayers: Int): Int

    private external fun prepare(nCtx: Int, nThreads: Int, temperature: Float, topP: Float): Int

    private external fun systemInfo(): String

    private external fun nativeGetDiagnostics(): String

    private external fun benchModel(pp: Int, tg: Int, pl: Int, nr: Int): String

    private external fun processSystemPrompt(systemPrompt: String): Int

    private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int

    private external fun generateNextToken(): String?

    private external fun unload()

    private external fun shutdown()

    private val _state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Uninitialized)
    override val state: StateFlow<InferenceEngine.State> = _state.asStateFlow()

    private var _readyForSystemPrompt = false
    @Volatile
    private var _cancelGeneration = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val llamaScope = CoroutineScope(llamaDispatcher + SupervisorJob())

    init {
        llamaScope.launch {
            try {
                check(_state.value is InferenceEngine.State.Uninitialized) {
                    "Cannot load native library in ${_state.value.javaClass.simpleName}!"
                }
                _state.value = InferenceEngine.State.Initializing
                Log.i(TAG, "Loading native library ai-chat...")
                System.loadLibrary("ai-chat")
                init(nativeLibDir)
                _state.value = InferenceEngine.State.Initialized
                Log.i(TAG, "Native library loaded! System info: \n${systemInfo()}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load native library", e)
                _state.value = InferenceEngine.State.Error(e)
            }
        }
    }

    override suspend fun loadModel(
        pathToModel: String,
        nGpuLayers: Int,
        contextSize: Int,
        threads: Int,
        temperature: Float,
        topP: Float
    ) = withContext(llamaDispatcher) {
        // Wait if still initializing
        while (_state.value is InferenceEngine.State.Initializing) {
            kotlinx.coroutines.delay(50)
        }

        if (_state.value is InferenceEngine.State.ModelReady) {
            Log.i(TAG, "Unloading previously loaded model...")
            unload()
            _state.value = InferenceEngine.State.Initialized
        }

        check(_state.value is InferenceEngine.State.Initialized) {
            "Cannot load model in ${_state.value.javaClass.simpleName}!"
        }

        try {
            Log.i(TAG, "Checking model file: $pathToModel")
            val file = File(pathToModel)
            require(file.exists() && file.isFile && file.canRead()) {
                "Model file not accessible: $pathToModel"
            }

            Log.i(TAG, "Loading model: $pathToModel (nGpuLayers=$nGpuLayers, nCtx=$contextSize, threads=$threads)")
            _readyForSystemPrompt = false
            _state.value = InferenceEngine.State.LoadingModel

            val loadRes = load(pathToModel, nGpuLayers)
            if (loadRes != 0) {
                throw UnsupportedArchitectureException()
            }

            val prepRes = prepare(contextSize, threads, temperature, topP)
            if (prepRes != 0) {
                throw IOException("Failed to prepare model resources (code=$prepRes)")
            }

            Log.i(TAG, "Model successfully loaded and prepared!")
            _readyForSystemPrompt = true
            _cancelGeneration = false
            _state.value = InferenceEngine.State.ModelReady
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model: $pathToModel", e)
            _state.value = InferenceEngine.State.Error(e)
            throw e
        }
    }

    override suspend fun setSystemPrompt(systemPrompt: String) =
        withContext(llamaDispatcher) {
            require(systemPrompt.isNotBlank()) { "Cannot process empty system prompt!" }
            check(_state.value is InferenceEngine.State.ModelReady) {
                "Cannot process system prompt in ${_state.value.javaClass.simpleName}!"
            }
            Log.i(TAG, "Sending system prompt...")
            _readyForSystemPrompt = false
            _state.value = InferenceEngine.State.ProcessingSystemPrompt
            val result = processSystemPrompt(systemPrompt)
            if (result != 0) {
                val err = RuntimeException("Failed to process system prompt: $result")
                _state.value = InferenceEngine.State.Error(err)
                throw err
            }
            Log.i(TAG, "System prompt processed.")
            _state.value = InferenceEngine.State.ModelReady
        }

    override fun sendUserPrompt(
        message: String,
        predictLength: Int
    ): Flow<String> = flow {
        require(message.isNotEmpty()) { "User prompt cannot be empty" }
        check(_state.value is InferenceEngine.State.ModelReady) {
            "Engine not ready: ${_state.value.javaClass.simpleName}"
        }

        try {
            Log.i(TAG, "Processing user prompt...")
            _readyForSystemPrompt = false
            _cancelGeneration = false
            _state.value = InferenceEngine.State.ProcessingUserPrompt

            val res = processUserPrompt(message, predictLength)
            if (res != 0) {
                Log.e(TAG, "Failed to process user prompt: $res")
                _state.value = InferenceEngine.State.ModelReady
                return@flow
            }

            _state.value = InferenceEngine.State.Generating
            while (!_cancelGeneration) {
                val token = generateNextToken()
                if (token != null) {
                    if (token.isNotEmpty()) emit(token)
                } else {
                    break
                }
            }
            _state.value = InferenceEngine.State.ModelReady
        } catch (e: CancellationException) {
            Log.i(TAG, "Generation cancelled by caller.")
            _cancelGeneration = true
            _state.value = InferenceEngine.State.ModelReady
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during generation", e)
            _state.value = InferenceEngine.State.Error(e)
            throw e
        }
    }.flowOn(llamaDispatcher)

    override fun getDiagnostics(): String = try {
        nativeGetDiagnostics()
    } catch (e: Exception) {
        "Keine Diagnosedaten verfügbar"
    }

    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String =
        withContext(llamaDispatcher) {
            check(_state.value is InferenceEngine.State.ModelReady) {
                "Benchmark requires ModelReady"
            }
            _readyForSystemPrompt = false
            _state.value = InferenceEngine.State.Benchmarking
            val res = benchModel(pp, tg, pl, nr)
            _state.value = InferenceEngine.State.ModelReady
            res
        }

    override fun cleanUp() {
        _cancelGeneration = true
        runBlocking(llamaDispatcher) {
            when (_state.value) {
                is InferenceEngine.State.ModelReady,
                is InferenceEngine.State.Generating -> {
                    Log.i(TAG, "Unloading model...")
                    _readyForSystemPrompt = false
                    _state.value = InferenceEngine.State.UnloadingModel
                    unload()
                    _state.value = InferenceEngine.State.Initialized
                    Log.i(TAG, "Model unloaded.")
                }
                is InferenceEngine.State.Error -> {
                    _state.value = InferenceEngine.State.Initialized
                }
                else -> Unit
            }
        }
    }

    override fun destroy() {
        _cancelGeneration = true
        runBlocking(llamaDispatcher) {
            _readyForSystemPrompt = false
            when (_state.value) {
                is InferenceEngine.State.Uninitialized -> Unit
                is InferenceEngine.State.Initialized -> shutdown()
                else -> {
                    unload()
                    shutdown()
                }
            }
        }
        llamaScope.cancel()
    }
}
