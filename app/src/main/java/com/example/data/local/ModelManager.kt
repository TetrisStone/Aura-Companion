package com.example.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

data class LocalGgufModel(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val isHeaderValid: Boolean
)

sealed class ImportProgress {
    object Idle : ImportProgress()
    data class Copying(val progressPercent: Int, val copiedBytes: Long, val totalBytes: Long) : ImportProgress()
    data class Success(val model: LocalGgufModel) : ImportProgress()
    data class Error(val message: String) : ImportProgress()
}

class ModelManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val GGUF_MAGIC = "GGUF" // 0x46554747 in little-endian / ASCII "GGUF"

        @Volatile
        private var instance: ModelManager? = null

        fun getInstance(context: Context): ModelManager =
            instance ?: synchronized(this) {
                instance ?: ModelManager(context.applicationContext).also { instance = it }
            }
    }

    private val modelsDir: File
        get() {
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private val _modelsListFlow = MutableStateFlow<List<LocalGgufModel>>(emptyList())
    val modelsListFlow: StateFlow<List<LocalGgufModel>> = _modelsListFlow.asStateFlow()

    private val _importProgress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    init {
        refreshModels()
    }

    fun refreshModels(): List<LocalGgufModel> {
        val files = modelsDir.listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }
            ?: emptyArray()

        val list = files.map { file ->
            val valid = checkGgufHeader(file)
            LocalGgufModel(
                file = file,
                name = file.name,
                sizeBytes = file.length(),
                sizeFormatted = formatFileSize(file.length()),
                isHeaderValid = valid
            )
        }.sortedByDescending { it.file.lastModified() }

        _modelsListFlow.value = list
        return list
    }

    fun resetImportProgress() {
        _importProgress.value = ImportProgress.Idle
    }

    /**
     * Checks if the first 4 bytes of the file are "GGUF"
     */
    fun checkGgufHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(4)
                val read = input.read(buffer)
                if (read == 4) {
                    val magic = String(buffer, Charsets.US_ASCII)
                    magic == GGUF_MAGIC
                } else false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking GGUF header for ${file.name}", e)
            false
        }
    }

    private fun checkGgufHeader(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(4)
                val read = input.read(buffer)
                if (read == 4) {
                    String(buffer, Charsets.US_ASCII) == GGUF_MAGIC
                } else false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importModelFromUri(uri: Uri): Result<LocalGgufModel> = withContext(Dispatchers.IO) {
        try {
            val fileName = queryDisplayName(uri) ?: "model_${System.currentTimeMillis()}.gguf"
            val totalBytes = queryFileSize(uri)

            // 1. Check GGUF header before copying
            if (!checkGgufHeader(uri)) {
                val err = "Ungültige Datei: GGUF-Header Magic ('GGUF') wurde nicht gefunden!"
                _importProgress.value = ImportProgress.Error(err)
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            // 2. Check available free disk space (require file size + 100MB buffer)
            val freeSpace = modelsDir.usableSpace
            val requiredSpace = if (totalBytes > 0) totalBytes + (100L * 1024 * 1024) else 500L * 1024 * 1024
            if (freeSpace < requiredSpace) {
                val err = "Nicht genügend freier interner Speicherplatz! Verfügbar: ${formatFileSize(freeSpace)}, Benötigt: ${formatFileSize(requiredSpace)}"
                _importProgress.value = ImportProgress.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val targetFile = File(modelsDir, fileName)
            _importProgress.value = ImportProgress.Copying(0, 0, totalBytes)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(256 * 1024) // 256 KB buffer for fast copying
                    var bytesRead: Int
                    var totalCopied: Long = 0
                    var lastReportTime = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalCopied += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 250 || totalCopied == totalBytes) {
                            lastReportTime = now
                            val pct = if (totalBytes > 0) ((totalCopied * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                            _importProgress.value = ImportProgress.Copying(pct, totalCopied, totalBytes)
                        }
                    }
                }
            } ?: run {
                val err = "Konnte Datei über Storage Access Framework nicht öffnen"
                _importProgress.value = ImportProgress.Error(err)
                return@withContext Result.failure(IOException(err))
            }

            // Verify copied file
            if (!checkGgufHeader(targetFile)) {
                targetFile.delete()
                val err = "Gleich nach dem Kopieren: GGUF Header-Prüfung fehlgeschlagen."
                _importProgress.value = ImportProgress.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val model = LocalGgufModel(
                file = targetFile,
                name = targetFile.name,
                sizeBytes = targetFile.length(),
                sizeFormatted = formatFileSize(targetFile.length()),
                isHeaderValid = true
            )

            refreshModels()
            _importProgress.value = ImportProgress.Success(model)
            Result.success(model)
        } catch (e: Exception) {
            Log.e(TAG, "Import error", e)
            val err = "Import fehlgeschlagen: ${e.localizedMessage ?: e.message}"
            _importProgress.value = ImportProgress.Error(err)
            Result.failure(e)
        }
    }

    fun deleteModel(file: File): Boolean {
        val deleted = file.delete()
        if (deleted) {
            refreshModels()
        }
        return deleted
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else null
        }
    }

    private fun queryFileSize(uri: Uri): Long {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex != -1 && cursor.moveToFirst()) {
                cursor.getLong(sizeIndex)
            } else 0L
        } ?: 0L
    }

    private fun formatFileSize(bytes: Long): String {
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        return if (gb >= 1.0) {
            String.format(java.util.Locale.US, "%.2f GB", gb)
        } else {
            val mb = bytes.toDouble() / (1024 * 1024)
            String.format(java.util.Locale.US, "%.1f MB", mb)
        }
    }
}
