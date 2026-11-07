package ir.math7.learning

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.util.Locale

private val ChalkYellow = Color(0xFFFFE066)
private val BoardGreen = Color(0xFF1F5A3C)
private val Skin = Color(0xFFFFD9B3)
private val Cream = Color(0xFFFFF8E1)

fun Int.fa(): String = toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

// ---------------------------------------------------------------- Voice
class Voice(val app: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    var ready by mutableStateOf(false)
    var faOk by mutableStateOf(true)
    var speaking by mutableStateOf(false)
    private var cbId = ""
    private var cb: (() -> Unit)? = null

    init {
        tts = TextToSpeech(app.applicationContext) { st ->
            main.post {
                val t = tts
                if (st == TextToSpeech.SUCCESS && t != null) {
                    var r = t.setLanguage(Locale("fa", "IR"))
                    if (r < 0) r = t.setLanguage(Locale("fa"))
                    faOk = r >= 0
                    t.setSpeechRate(0.85f)
                    t.setPitch(1.2f)
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) { main.post { speaking = true } }
                        override fun onDone(id: String?) { finish(id) }
                        override fun onError(id: String?) { finish(id) }
                        override fun onStop(id: String?, interrupted: Boolean) { finish(id) }
                    })
                } else faOk = false
                ready = true
            }
        }
    }

    private fun finish(id: String?) {
        main.post {
            speaking = false
            if (id == cbId) { val c = cb; cb = null; c?.invoke() }
        }
    }

    fun speak(text: String, done: () -> Unit): String {
        val t = tts
        if (t == null || !faOk) { done(); return "" }
        cbId = System.nanoTime().toString()
        cb = done
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, cbId)
        return cbId
    }

    fun stopIf(id: String) { if (id == cbId) stop() }
    fun stop() { cb = null; cbId = ""; tts?.stop(); speaking = false }
    fun shutdown() { tts?.stop(); tts?.shutdown() }
}

/** Plays res/raw/<clip>.mp3 if it exists, otherwise speaks with Android TTS, otherwise just waits (subtitles only). */
suspend fun Voice.say(text: String, clip: String? = null) {
    val rid = if (clip == null) 0 else app.resources.getIdentifier(clip, "raw", app.packageName)
    if (rid != 0) {
        val mp = MediaPlayer.create(app, rid)
        if (mp != null) {
            val d = CompletableDeferred<Unit>()
            mp.setOnCompletionListener { d.complete(Unit) }
            speaking = true
            try { mp.start(); d.await() } finally { speaking = false; mp.release() }
            return
        }
    }
    withTimeoutOrNull(3000) { while (!ready) delay(100) }
    if (!ready || !faOk) {
        speaking = true
        try { delay(text.length * 70L + 600L) } finally { speaking = false }
        return
    }
    val d = CompletableDeferred<Unit>()
    val id = speak(text) { d.complete(Unit) }
    try {
        withTimeoutOrNull(text.length * 220L + 8000L) { d.await() }
    } catch (e: CancellationException) {
        stopIf(id); throw e
    }
}

// ---------------------------------------------------------------- Teacher (2D cartoon, drawn in code)
@Composable
fun Teacher(talking: Boolean, happy: Boolean, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "teacher")
    val mouth by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(230), RepeatMode.Reverse), label = "m")
    val wave by inf.animateFloat(-12f, 12f, infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "w")
    val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "b")
    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(2600); blink = true; delay(130); blink = false } }
    Canvas(modifier) {
        val u = size.width / 100f
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        val dy = bob * 1.5f
        val navy = Color(0xFF3F3D8F)
        val teal = Color(0xFF26A69A)
        val tealDark = Color(0xFF00897B)
        val dark = Color(0xFF3E2723)
        // body
        val body = Path().apply {
            moveTo(28f * u, 92f * u); lineTo(72f * u, 92f * u); lineTo(92f * u, 150f * u); lineTo(8f * u, 150f * u); close()
        }
        drawPath(body, teal)
        // left arm + hand
        drawLine(tealDark, o(27f, 98f), o(18f, 126f), strokeWidth = 8f * u, cap = StrokeCap.Round)
        drawCircle(Skin, 4.5f * u, o(18f, 127f))
        // scarf drape + head scarf
        drawOval(navy, o(22f, 68f + dy), Size(56f * u, 34f * u))
        drawCircle(navy, 30f * u, o(50f, 46f + dy))
        // face
        drawOval(Skin, o(32f, 26f + dy), Size(36f * u, 42f * u))
        // eyes
        if (happy) {
            drawArc(color = dark, startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = o(39f, 41f + dy), size = Size(8f * u, 7f * u), style = Stroke(1.7f * u, cap = StrokeCap.Round))
            drawArc(color = dark, startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = o(53f, 41f + dy), size = Size(8f * u, 7f * u), style = Stroke(1.7f * u, cap = StrokeCap.Round))
        } else if (blink) {
            drawLine(dark, o(40f, 45f + dy), o(46f, 45f + dy), strokeWidth = 1.7f * u, cap = StrokeCap.Round)
            drawLine(dark, o(54f, 45f + dy), o(60f, 45f + dy), strokeWidth = 1.7f * u, cap = StrokeCap.Round)
        } else {
            drawOval(dark, o(40.5f, 41.5f + dy), Size(5f * u, 7f * u))
            drawOval(dark, o(54.5f, 41.5f + dy), Size(5f * u, 7f * u))
            drawCircle(Color.White, 0.9f * u, o(43f, 43f + dy))
            drawCircle(Color.White, 0.9f * u, o(57f, 43f + dy))
        }
        // cheeks
        drawCircle(Color(0xFFFF8A80).copy(alpha = 0.45f), 4f * u, o(37f, 53f + dy))
        drawCircle(Color(0xFFFF8A80).copy(alpha = 0.45f), 4f * u, o(63f, 53f + dy))
        // mouth
        if (talking) {
            drawOval(Color(0xFF7B1F1F), o(45.5f, 56f + dy), Size(9f * u, (1.5f + 5f * mouth) * u))
        } else if (happy) {
            drawArc(color = Color(0xFF7B1F1F), startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = o(43f, 51f + dy), size = Size(14f * u, 10f * u), style = Stroke(1.6f * u, cap = StrokeCap.Round))
        } else {
            drawArc(color = Color(0xFF7B1F1F), startAngle = 25f, sweepAngle = 130f, useCenter = false, topLeft = o(45f, 53f + dy), size = Size(10f * u, 6f * u), style = Stroke(1.5f * u, cap = StrokeCap.Round))
        }
        // right arm with pointer (waves)
        val ang = if (talking || happy) wave else wave * 0.3f
        rotate(degrees = -20f + ang, pivot = o(74f, 98f)) {
            drawLine(tealDark, o(74f, 98f), o(92f, 70f), strokeWidth = 8f * u, cap = StrokeCap.Round)
            drawLine(Color(0xFF8D6E63), o(92f, 70f), o(97f, 38f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
            drawCircle(Skin, 4.5f * u, o(92f, 70f))
        }
    }
}

// ---------------------------------------------------------------- Board visuals
@Composable
fun Lines(p: Float, lines: List<String>, big: Boolean = false, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        lines.forEachIndexed { i, s ->
            val a = (p * lines.size - i).coerceIn(0f, 1f)
            val c = when {
                s.startsWith("✓") -> ChalkYellow
                s.contains("✗") -> Color(0xFFFFA4A4)
                else -> Color.White
            }
            Text(
                s, color = c, fontSize = if (big && i == 0) 30.sp else 20.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.alpha(a).padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun Pencils(p: Float, n: Int, lines: List<String>) {
    Column(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val cols = listOf(Color(0xFFFF5252), Color(0xFF42A5F5), Color(0xFFFFCA28), Color(0xFF66BB6A), Color(0xFFAB47BC), Color(0xFFFF7043))
            val gap = size.width / (n + 1)
            val w = minOf(gap * 0.45f, 30.dp.toPx())
            val bodyH = size.height * 0.6f
            for (i in 0 until n) {
                val a = (p * n * 1.5f - i).coerceIn(0f, 1f)
                if (a <= 0f) continue
                val x = gap * (i + 1)
                val y0 = size.height * 0.1f - (1f - a) * 30f
                drawRect(Color(0xFFF48FB1).copy(alpha = a), Offset(x - w / 2, y0), Size(w, bodyH * 0.12f))
                drawRect(cols[i % cols.size].copy(alpha = a), Offset(x - w / 2, y0 + bodyH * 0.12f), Size(w, bodyH * 0.78f))
                val tip = Path().apply {
                    moveTo(x - w / 2, y0 + bodyH * 0.9f); lineTo(x + w / 2, y0 + bodyH * 0.9f); lineTo(x, y0 + bodyH * 0.9f + w * 0.9f); close()
                }
                drawPath(tip, Color(0xFFFFE0B2).copy(alpha = a))
            }
        }
        Lines(((p - 0.4f) / 0.6f).coerceIn(0f, 1f), lines, modifier = Modifier.fillMaxWidth().weight(0.9f))
    }
}

@Composable
fun Matches(p: Float, n: Int) {
    val v = Math.ceil((p * n).toDouble()).toInt().coerceIn(1, n)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
            val s = minOf(size.width / (n + 0.3f), size.height * 0.7f)
            val x0 = (size.width - s * n) / 2f
            val y0 = (size.height - s) / 2f
            fun stick(a: Offset, b: Offset) {
                drawLine(Color(0xFFFFCC80), a, b, strokeWidth = 7f, cap = StrokeCap.Round)
                drawCircle(Color(0xFFE53935), 7f, a)
            }
            for (i in 0 until v) {
                val l = x0 + s * i
                stick(Offset(l, y0), Offset(l + s, y0))
                stick(Offset(l, y0 + s), Offset(l + s, y0 + s))
                stick(Offset(l + s, y0), Offset(l + s, y0 + s))
                if (i == 0) stick(Offset(l, y0), Offset(l, y0 + s))
            }
        }
        Text(
            "${v.fa()} مربع ← ${(3 * v + 1).fa()} چوب کبریت", color = ChalkYellow, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp)
        )
    }
}

@Composable
fun Garden(p: Float, w: String, h: String) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
            drawRect(Color(0xFF66BB6A).copy(alpha = 0.55f), size = size)
            val pts = listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height), Offset(0f, 0f))
            for (k in 0..3) {
                val f = (p * 4f - k).coerceIn(0f, 1f)
                if (f > 0f) drawLine(Color(0xFFFFCC80), pts[k], pts[k] + (pts[k + 1] - pts[k]) * f, strokeWidth = 9f, cap = StrokeCap.Round)
            }
        }
        val lab = Modifier.alpha(((p - 0.1f) * 3f).coerceIn(0f, 1f))
        Text(w, lab.align(Alignment.TopCenter).padding(top = 6.dp), color = ChalkYellow, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(w, lab.align(Alignment.BottomCenter).padding(bottom = 6.dp), color = ChalkYellow, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(h, lab.align(Alignment.CenterEnd).padding(end = 4.dp), color = ChalkYellow, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(h, lab.align(Alignment.CenterStart).padding(start = 4.dp), color = ChalkYellow, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

// ---------------------------------------------------------------- Lesson data (Chapter 1)
class Scene(val say: String, val board: @Composable (Float) -> Unit)
class Quiz(val q: String, val opts: List<String>, val ans: Int, val why: String)
class Lesson(val title: String, val icon: String, val scenes: List<Scene>, val quiz: Quiz)

val LESSONS: List<Lesson> = listOf(
    Lesson(
        "مراحل حل مسئله", "🧭",
        listOf(
            Scene("سلام دوست من! من معلم ریاضی تو هستم. توی این فصل یاد می‌گیریم هر مسئله‌ای رو قدم‌به‌قدم و آسون حل کنیم. آماده‌ای؟") { p ->
                Lines(p, listOf("فصل ۱", "راهبردهای حل مسئله", "🙂 بزن بریم!"), big = true)
            },
            Scene("مسئله یعنی سؤالی که جوابش رو همین الان نمی‌دونیم. مثلاً: هر مداد پانصد تومنه. چهار تا مداد چند تومان می‌شه؟") { p ->
                Pencils(p, 4, listOf("هر مداد = ۵۰۰ تومان", "تعداد مداد = ۴", "✓ ۴ × ۵۰۰ = ۲۰۰۰ تومان"))
            },
            Scene("قدم اول: مسئله رو خوب بخون و بفهم. ببین چی داده شده و چی خواسته شده. قدم دوم: نقشه بکش، یعنی فکر کن با کدوم راهبرد می‌شه حلش کرد.") { p ->
                Lines(p, listOf("قدم ۱: مسئله را بفهم 👀", "قدم ۲: نقشه بکش ✏️"))
            },
            Scene("قدم سوم: نقشه‌ات رو اجرا کن و محاسبه‌ها رو انجام بده. قدم چهارم، که خیلی مهمه: جواب رو بررسی کن ببین منطقی هست یا نه.") { p ->
                Lines(p, listOf("قدم ۳: نقشه را اجرا کن ⚙️", "قدم ۴: جواب را بررسی کن ✅"))
            },
            Scene("توی درس‌های بعدی، پنج راهبرد جادویی یاد می‌گیریم: رسم شکل، الگوسازی، حدس و آزمایش، لیست منظم و مسئله ساده‌تر.") { p ->
                Lines(p, listOf("۱) رسم شکل", "۲) الگوسازی", "۳) حدس و آزمایش", "۴) لیست منظم", "۵) مسئله ساده‌تر"))
            }
        ),
        Quiz(
            "بعد از پیدا کردن جواب، آخرین قدم چیه؟",
            listOf("بررسی و بازبینی جواب", "فقط جواب رو بنویسم", "دوباره نقشه بکشم"), 0,
            "بازبینی جواب باعث می‌شه اشتباهی نداشته باشیم."
        )
    ),
    Lesson(
        "رسم شکل", "📐",
        listOf(
            Scene("اولین راهبرد: رسم شکل. وقتی مسئله پیچیده است، یه نقاشی ساده کمک می‌کنه. مسئله این است: باغی مستطیل‌شکل شش متر طول و چهار متر عرض داره. دور تا دور باغ چند متر حصار لازمه؟") { p ->
                Lines(p, listOf("باغ مستطیلی", "طول = ۶ متر ، عرض = ۴ متر", "حصار دور تا دور؟"))
            },
            Scene("بیا شکلش رو بکشیم. اول ضلع بالا، شش متر. بعد ضلع سمت راست، چهار متر. پایین دوباره شش متر و سمت چپ چهار متر.") { p ->
                Garden(p, "۶ متر", "۴ متر")
            },
            Scene("حالا همه ضلع‌ها رو جمع می‌کنیم: شش به‌علاوه چهار به‌علاوه شش به‌علاوه چهار، می‌شه بیست متر. دیدی؟ شکل کمک کرد هیچ ضلعی رو فراموش نکنیم.") { p ->
                Lines(p, listOf("۶ + ۴ + ۶ + ۴", "✓ = ۲۰ متر حصار"))
            }
        ),
        Quiz(
            "یک باغ مربع‌شکل با ضلع پنج متر داریم. دور تا دورش چند متر حصار لازمه؟",
            listOf("۱۰ متر", "۲۰ متر", "۲۵ متر"), 1,
            "مربع چهار ضلع پنج‌متری داره. پنج ضرب در چهار می‌شه بیست."
        )
    ),
    Lesson(
        "الگوسازی", "🔢",
        listOf(
            Scene("راهبرد دوم: الگوسازی. با چوب کبریت مربع می‌سازیم. یک مربع، چهار چوب کبریت لازم داره.") { p -> Matches(p, 1) },
            Scene("دو مربع کنار هم، هفت چوب کبریت لازم دارن، چون یک ضلع بین‌شون مشترکه.") { p -> Matches(p, 2) },
            Scene("سه مربع کنار هم، ده چوب کبریت می‌خوان. می‌بینی؟ هر بار سه تا چوب اضافه می‌شه.") { p -> Matches(p, 3) },
            Scene("پس الگو اینه: چهار، هفت، ده، سیزده و همین‌طور ادامه. هر بار سه تا اضافه می‌شه. برای ده مربع: سه ضرب در ده، به‌علاوه یک، می‌شه سی و یک چوب کبریت.") { p ->
                Lines(p, listOf("۴ ، ۷ ، ۱۰ ، ۱۳ ، ...", "هر بار ۳ تا اضافه می‌شود", "✓ ۱۰ مربع = ۳×۱۰ + ۱ = ۳۱ چوب"))
            }
        ),
        Quiz(
            "پنج مربع کنار هم چند چوب کبریت لازم داره؟",
            listOf("۱۵", "۱۶", "۱۷"), 1,
            "سه ضرب در پنج می‌شه پانزده، به‌علاوه یک می‌شه شانزده."
        )
    ),
    Lesson(
        "حدس و آزمایش", "🎯",
        listOf(
            Scene("راهبرد سوم: حدس و آزمایش. مسئله: مجموع دو عدد دوازده و حاصل‌ضربشون بیست و هفت است. اون دو عدد کدامند؟") { p ->
                Lines(p, listOf("مجموع = ۱۲", "حاصل‌ضرب = ۲۷", "دو عدد؟"))
            },
            Scene("اول یه حدس می‌زنیم: پنج و هفت. مجموعشون دوازده است، ولی حاصل‌ضربشون سی و پنجه. زیاده! پس باید عددها رو از هم دورتر کنیم.") { p ->
                Lines(p, listOf("۵ و ۷ ← ضرب = ۳۵", "✗ زیاد شد"))
            },
            Scene("حدس بعدی: چهار و هشت. ضربشون سی و دو. هنوز زیاده! دوباره دورتر می‌کنیم: سه و نه. ضربشون بیست و هفت. درسته!") { p ->
                Lines(p, listOf("۴ و ۸ ← ۳۲ ✗", "۳ و ۹ ← ۲۷", "✓ جواب: ۳ و ۹"))
            }
        ),
        Quiz(
            "مجموع دو عدد ده و حاصل‌ضربشون بیست و یکه. اون دو عدد کدومن؟",
            listOf("۴ و ۶", "۳ و ۷", "۵ و ۵"), 1,
            "سه و هفت: مجموعشون ده و ضربشون بیست و یکه."
        )
    ),
    Lesson(
        "لیست منظم", "📋",
        listOf(
            Scene("راهبرد چهارم: لیست منظم. مسئله: با رقم‌های یک و دو و سه، چند عدد دو‌رقمی با رقم‌های متفاوت می‌شه نوشت؟") { p ->
                Lines(p, listOf("رقم‌ها: ۱ ، ۲ ، ۳", "عدد دورقمی با رقم‌های متفاوت؟"))
            },
            Scene("برای اینکه چیزی از قلم نیفته، منظم می‌نویسیم. اول عددهایی که رقم دهگانشون یکه: دوازده و سیزده.") { p ->
                Lines(p, listOf("دهگان ۱ ← ۱۲ ، ۱۳"))
            },
            Scene("بعد دهگان دو: بیست و یک و بیست و سه. و آخر، دهگان سه: سی و یک و سی و دو.") { p ->
                Lines(p, listOf("دهگان ۱ ← ۱۲ ، ۱۳", "دهگان ۲ ← ۲۱ ، ۲۳", "دهگان ۳ ← ۳۱ ، ۳۲"))
            },
            Scene("همه رو شمردیم: شش عدد. چون منظم نوشتیم، هیچ عددی جا نیفتاد.") { p ->
                Lines(p, listOf("۱۲ ، ۱۳ ، ۲۱ ، ۲۳ ، ۳۱ ، ۳۲", "✓ جمعاً ۶ عدد"))
            }
        ),
        Quiz(
            "با رقم‌های یک و دو و سه و چهار، چند عدد دورقمی با رقم‌های متفاوت می‌شه نوشت؟",
            listOf("۸", "۱۲", "۱۶"), 1,
            "برای هر رقم دهگان، سه انتخاب برای یکان داریم. چهار ضرب در سه می‌شه دوازده."
        )
    ),
    Lesson(
        "مسئله ساده‌تر", "🪄",
        listOf(
            Scene("راهبرد پنجم: حل یک مسئله ساده‌تر. مسئله: حاصل جمع عددهای یک تا صد چنده؟ یکی‌یکی جمع کردنش خیلی طول می‌کشه!") { p ->
                Lines(p, listOf("۱ + ۲ + ۳ + ... + ۱۰۰ = ؟"), big = true)
            },
            Scene("بیا اول با مسئله ساده‌تر شروع کنیم: جمع یک تا ده. اول و آخر رو جفت می‌کنیم: یک و ده، دو و نه، سه و هشت. هر جفت می‌شه یازده.") { p ->
                Lines(p, listOf("۱ + ۱۰ = ۱۱", "۲ + ۹ = ۱۱", "۳ + ۸ = ۱۱", "... پنج جفت"))
            },
            Scene("پنج جفت داریم که هر کدوم یازده‌ان. پس پنج ضرب در یازده می‌شه پنجاه و پنج.") { p ->
                Lines(p, listOf("۵ × ۱۱ = ۵۵", "✓ جمع ۱ تا ۱۰ = ۵۵"))
            },
            Scene("حالا همین ایده رو برای یک تا صد پیاده می‌کنیم: پنجاه جفت داریم و هر جفت صد و یکه. پنجاه ضرب در صد و یک می‌شه پنج هزار و پنجاه.") { p ->
                Lines(p, listOf("۵۰ جفت ، هر جفت ۱۰۱", "۵۰ × ۱۰۱ = ۵۰۵۰", "✓ جواب: ۵۰۵۰"))
            }
        ),
        Quiz(
            "با همین روش، جمع عددهای یک تا بیست چند می‌شه؟",
            listOf("۲۰۰", "۲۱۰", "۲۲۰"), 1,
            "ده جفت داریم که هر کدوم بیست و یکه. ده ضرب در بیست و یک می‌شه دویست و ده."
        )
    )
)

// ---------------------------------------------------------------- Screens
@Composable
fun VoiceWarning() {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Text("صدای فارسی روی گوشی نصب نیست؛ فعلاً انیمیشن و زیرنویس داری.", fontSize = 13.sp)
            TextButton(onClick = {
                try {
                    ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    try {
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }
            }) { Text("باز کردن تنظیمات صدا") }
        }
    }
}

@Composable
fun QuizBox(q: Quiz, picked: Int, solved: Boolean, onPick: (Int) -> Unit, onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("❓ سؤال", color = ChalkYellow, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(q.q, color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        q.opts.forEachIndexed { i, o ->
            val good = solved && i == q.ans
            val bad = !solved && picked == i
            val bg = when { good -> Color(0xFF43A047); bad -> Color(0xFFE53935); else -> Color(0xFFFFF59D) }
            val fg = if (good || bad) Color.White else Color.Black
            Button(
                onClick = { if (!solved) onPick(i) },
                colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) { Text(o, fontSize = 18.sp) }
        }
        if (solved) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onNext) { Text("درس بعدی ◀", fontSize = 18.sp) }
        }
    }
}

@Composable
fun LessonScreen(voice: Voice, li: Int, onExit: () -> Unit, onDone: () -> Unit) {
    val lesson = LESSONS[li]
    val n = lesson.scenes.size
    var idx by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var happy by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(-1) }
    var solved by remember { mutableStateOf(false) }
    var fb by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val prog = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) { onDispose { voice.stop() } }

    LaunchedEffect(idx, playing) {
        if (idx >= n) {
            if (playing) { fb = null; voice.say(lesson.quiz.q, "l${li}q") }
            return@LaunchedEffect
        }
        if (!playing) { voice.stop(); return@LaunchedEffect }
        val s = lesson.scenes[idx]
        prog.snapTo(0f)
        launch { prog.animateTo(1f, tween((s.say.length * 55).coerceIn(2500, 11000), easing = LinearEasing)) }
        voice.say(s.say, "l${li}s${idx}")
        delay(600)
        idx++
    }

    fun pick(i: Int) {
        picked = i
        val ok = i == lesson.quiz.ans
        solved = ok
        happy = ok
        val t = if (ok) "آفرین! درسته. ${lesson.quiz.why}" else "نزدیک بود! دوباره فکر کن و یه گزینه دیگه رو امتحان کن."
        fb = t
        job?.cancel()
        job = scope.launch { voice.say(t) }
    }

    val bubble = fb ?: if (idx < n) lesson.scenes[idx].say else lesson.quiz.q

    Column(Modifier.fillMaxSize().background(Cream).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("درس ${(li + 1).fa()}: ${lesson.title}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4E342E))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onExit) { Text("✕ خروج") }
        }
        if (voice.ready && !voice.faOk) VoiceWarning()
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp)).background(BoardGreen)
                .border(6.dp, Color(0xFF8D6E63), RoundedCornerShape(18.dp))
        ) {
            if (idx < n) lesson.scenes[idx].board(prog.value)
            else QuizBox(lesson.quiz, picked, solved, { pick(it) }, onDone)
        }
        Row(Modifier.fillMaxWidth().height(170.dp), verticalAlignment = Alignment.Bottom) {
            Teacher(voice.speaking, happy, Modifier.width(110.dp).height(165.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.weight(1f).heightIn(max = 160.dp).padding(start = 6.dp, bottom = 12.dp)
            ) {
                Text(bubble, fontSize = 16.sp, modifier = Modifier.verticalScroll(rememberScrollState()).padding(10.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = idx > 0, onClick = {
                idx--; picked = -1; solved = false; happy = false; fb = null; job?.cancel()
            }) { Text("قبلی") }
            if (idx < n) {
                OutlinedButton(onClick = { playing = !playing }) { Text(if (playing) "⏸ توقف" else "▶ پخش") }
                Text("${(idx + 1).fa()} از ${n.fa()}", fontSize = 14.sp)
            } else {
                Text("سؤال پایانی", fontSize = 14.sp)
            }
            Button(enabled = idx < n, onClick = { idx++ }) { Text("بعدی") }
        }
    }
}

@Composable
fun Home(done: Set<String>, onOpen: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Teacher(false, true, Modifier.width(80.dp).height(120.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("ریاضی هفتم", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4E342E))
                Text("فصل ۱: راهبردهای حل مسئله", fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (done.size >= LESSONS.size) {
            Text("🎉 آفرین! فصل ۱ را تمام کردی", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(LESSONS) { i, l ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().clickable { onOpen(i) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(l.icon, fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Text("درس ${(i + 1).fa()}: ${l.title}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (done.contains(i.toString())) Text("✅", fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun App(voice: Voice, prefs: SharedPreferences) {
    var cur by remember { mutableStateOf(-1) }
    var done by remember { mutableStateOf(prefs.getStringSet("done", emptySet())!!.toSet()) }
    if (cur < 0) {
        Home(done) { cur = it }
    } else {
        BackHandler { voice.stop(); cur = -1 }
        key(cur) {
            LessonScreen(voice, cur, onExit = { voice.stop(); cur = -1 }, onDone = {
                val n = done + cur.toString()
                done = n
                prefs.edit().putStringSet("done", n).apply()
                voice.stop()
                if (cur + 1 < LESSONS.size) cur++ else cur = -1
            })
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var voice: Voice
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = Voice(this)
        val prefs = getSharedPreferences("m7", MODE_PRIVATE)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = Cream) { App(voice, prefs) }
                }
            }
        }
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }
}
