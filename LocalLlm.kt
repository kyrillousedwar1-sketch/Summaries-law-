package com.family.lawofflineai

import android.content.Context
import android.net.Uri
import java.io.File

class LocalLlm(private val context: Context) {
    private var modelFile: File? = null

    fun importModel(uri: Uri): File {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(dir, "study-model.gguf")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "تعذر قراءة الموديل" }
            target.outputStream().use { out -> input.copyTo(out, 1024 * 1024) }
        }
        modelFile = target
        return target
    }

    fun hasModel(): Boolean = modelFile?.exists() == true
    fun path(): String? = modelFile?.absolutePath

    fun generate(prompt: String, maxTokens: Int = 700): String {
        val p = modelFile?.absolutePath ?: return ""
        return NativeLlama.generate(p, prompt, maxTokens)
    }
}

object NativeLlama {
    init { System.loadLibrary("lawllama") }
    external fun generate(modelPath: String, prompt: String, maxTokens: Int): String
}
