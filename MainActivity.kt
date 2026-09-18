package com.family.lawofflineai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.family.lawofflineai.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var asr: OfflineTranscriber
    private lateinit var llm: LocalLlm
    private var audioUri: Uri? = null
    private var transcript = ""
    private var lastResult = ""

    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u ?: return@registerForActivityResult
        audioUri = u
        try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        b.fileInfo.text = "المحاضرة: ${u.lastPathSegment}"
    }
    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val f = llm.importModel(u)
                withContext(Dispatchers.Main) { b.modelInfo.text = "LLM: ${f.length()/1024/1024} MB — جاهز Offline" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { b.status.text = "فشل استيراد الموديل: ${e.message}" }
            }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        asr = OfflineTranscriber(this)
        llm = LocalLlm(this)

        b.pickAudio.setOnClickListener { audioPicker.launch(arrayOf("audio/*", "video/*")) }
        b.pickModel.setOnClickListener { modelPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) }
        b.start.setOnClickListener { runSession() }

        b.summary.setOnClickListener { ask("اعمل ملخصًا منظمًا للمحاضرة بالعربية. استخرج أهم الأفكار والقواعد والاستثناءات، ولا تضف معلومات غير موجودة في النص.") }
        b.explain.setOnClickListener { ask("اشرح محتوى المحاضرة بالعربية لطالب يبدأ المادة، خطوة خطوة، مع أمثلة من النص فقط.") }
        b.definitions.setOnClickListener { ask("استخرج كل التعريفات والقواعد والشروط والاستثناءات والفروق المهمة، مع تنظيمها في عناوين.") }
        b.essay.setOnClickListener { ask("أنشئ 15 سؤالًا مقاليًا صعبًا بمستوى امتحان كلية حقوق، مع إجابة نموذجية مختصرة مستندة فقط إلى المحاضرة.") }
        b.mcq.setOnClickListener { ask("أنشئ 20 سؤال اختيار من متعدد صعبًا جدًا من المحاضرة. أربعة اختيارات، حدد الإجابة واشرح لماذا، ولا تخترع خارج النص.") }
        b.cases.setOnClickListener { ask("أنشئ 8 حالات عملية قانونية مبنية على أفكار المحاضرة، ثم اطلب تحديد القاعدة والتطبيق والاستثناء والإجابة النموذجية.") }
        b.mindmap.setOnClickListener { ask("حوّل المحاضرة إلى خريطة ذهنية نصية هرمية: الموضوع ثم الفروع ثم القواعد والشروط والاستثناءات.") }
        b.flash.setOnClickListener { ask("أنشئ 30 بطاقة مراجعة سؤال/جواب من المحاضرة، وركز على الأشياء التي تصلح للامتحان.") }
        b.quiz.setOnClickListener { ask("اختبرني في المحاضرة: أنشئ 10 أسئلة متدرجة الصعوبة، لا تظهر الإجابة أولًا، ثم ضع مفتاح الإجابة والتفسير في النهاية.") }
        b.export.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, lastResult.ifBlank { transcript })
            }
            startActivity(Intent.createChooser(send, "مشاركة"))
        }
    }

    private fun runSession() {
        val u = audioUri ?: run { b.status.text = "اختار المحاضرة الأول"; return }
        b.progress.visibility = View.VISIBLE; b.start.isEnabled = false
        lifecycleScope.launch {
            try {
                b.status.text = "1/2 تفريغ المحاضرة محليًا..."
                transcript = withContext(Dispatchers.IO) { asr.transcribe(u) }
                b.output.setText(transcript)
                b.status.text = "2/2 تم التفريغ. اختار أداة المذاكرة."
            } catch (e: Exception) {
                b.status.text = "خطأ: ${e.message}"
            } finally {
                b.progress.visibility = View.GONE; b.start.isEnabled = true
            }
        }
    }

    private fun ask(instruction: String) {
        if (transcript.isBlank()) { b.status.text = "فرّغ المحاضرة الأول"; return }
        if (!llm.hasModel()) { b.status.text = "اختار موديل GGUF محلي الأول"; return }
        b.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val prompt = """
أنت مساعد دراسة متخصص في القانون.
ممنوع اختراع أي معلومة خارج النص.
اكتب بالعربية الواضحة، ونظم الإجابة بعناوين ونقاط.
المطلوب:
$instruction

نص المحاضرة:
$transcript
""".trimIndent()
                val result = withContext(Dispatchers.IO) { llm.generate(prompt, 900) }
                lastResult = result
                b.output.setText(result)
                b.status.text = "تم التحليل محليًا"
            } catch (e: Exception) { b.status.text = "خطأ LLM: ${e.message}" }
            finally { b.progress.visibility = View.GONE }
        }
    }
}
