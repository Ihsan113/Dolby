package id.danzku.ytlocalsub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

private val Navy = Color(0xFF10151F)
private val NavyCard = Color(0xFF1B2431)
private val Blue = Color(0xFF739FFF)
private val Sky = Color(0xFF80D5E4)

@Composable
fun LocalSubApp(
    settings: LocalSettings,
    start: (AudioSourceMode, WhisperModel, SpeechLang) -> Unit,
    stop: () -> Unit,
    notifyPermission: () -> Unit,
    feedback: (String) -> Unit
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Blue, onPrimary = Navy, background = Navy,
        surface = NavyCard, onSurface = Color(0xFFF6F8FF),
        onBackground = Color(0xFFF6F8FF), surfaceVariant = Color(0xFF2B3747),
        onSurfaceVariant = Color(0xFFB1BFD1), secondary = Sky
    ) else lightColorScheme(
        primary = Color(0xFF2858D3), background = Color(0xFFF5F7FB),
        surface = Color.White, onSurface = Color(0xFF151E2D),
        onBackground = Color(0xFF151E2D), surfaceVariant = Color(0xFFE7EDF8),
        onSurfaceVariant = Color(0xFF596779), secondary = Color(0xFF007C90)
    )
    var onboarded by remember { mutableStateOf(settings.setupDone) }
    var tab by remember { mutableIntStateOf(0) }
    var rawUrl by remember { mutableStateOf(settings.lastUrl) }
    var videoId by remember { mutableStateOf(VideoUrl.extract(settings.lastUrl)) }
    var model by remember { mutableStateOf(settings.model) }
    var language by remember { mutableStateOf(settings.language) }
    var source by remember { mutableStateOf(settings.source) }
    var inputError by remember { mutableStateOf(false) }
    val caption by CaptionBus.state.collectAsState()
    val downloadStatus = ModelDownloader.status
    val modelReady = downloadStatus is ModelStatus.Ready && downloadStatus.model == model

    LaunchedEffect(model) { ModelDownloader.refresh(context, model) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                    out.write(toSrt(caption.cues))
                } ?: error("Tidak bisa membuka lokasi file")
            }.onSuccess { feedback("Subtitle berhasil disimpan") }
                .onFailure { feedback("Gagal menyimpan: ${it.message}") }
        }
    }
    fun applyUrl() {
        val id = VideoUrl.extract(rawUrl)
        inputError = id == null
        if (id != null) {
            if (caption.running) { feedback("Hentikan transkripsi sebelum mengganti video"); return }
            videoId = id
            settings.lastUrl = rawUrl.trim()
        }
    }
    fun changeModel(next: WhisperModel) {
        if (caption.running) { feedback("Hentikan transkripsi dahulu"); return }
        model = next
        settings.model = next
        ModelDownloader.refresh(context, next)
    }
    fun changeSource(next: AudioSourceMode) {
        if (caption.running) { feedback("Hentikan transkripsi dahulu"); return }
        source = next
        settings.source = next
    }

    MaterialTheme(colorScheme = colors) {
        if (!onboarded) {
            Onboarding(onFinish = { settings.setupDone = true; onboarded = true })
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Rounded.PlayCircle, null) },
                            label = { Text("Video") }
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Rounded.Tune, null) },
                            label = { Text("Pengaturan") }
                        )
                    }
                }
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                        .padding(horizontal = 17.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Header()
                    if (tab == 0) {
                        SurfaceCard {
                            Text("LINK VIDEO YOUTUBE", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = rawUrl, onValueChange = { rawUrl = it; inputError = false },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp), singleLine = true,
                                isError = inputError,
                                placeholder = { Text("Tempel https://youtu.be/...") },
                                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                supportingText = if (inputError) ({ Text("Gunakan URL YouTube yang valid (https)") }) else null
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = ::applyUrl, modifier = Modifier.fillMaxWidth(),
                                enabled = !caption.running, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(19.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Muat video")
                            }
                        }
                        VideoSurface(videoId)
                        SubtitlePanel(caption)
                        if (caption.running) {
                            Button(onClick = stop, modifier = Modifier.fillMaxWidth().height(54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Rounded.Stop, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Hentikan subtitle")
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (videoId == null) feedback("Muat URL video terlebih dahulu")
                                    else if (!modelReady) feedback("Unduh model Whisper terlebih dahulu")
                                    else { CaptionBus.reset(); start(source, model, language) }
                                },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                enabled = videoId != null && modelReady,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Rounded.GraphicEq, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Mulai subtitle lokal")
                            }
                        }
                        ModelCard(model, ::changeModel, downloadStatus,
                            { ModelDownloader.download(context, model) })
                        SurfaceCard {
                            SectionHeading("Sumber suara", "Gunakan audio perangkat jika tersedia")
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = source == AudioSourceMode.DEVICE,
                                    onClick = { changeSource(AudioSourceMode.DEVICE) },
                                    label = { Text("Audio perangkat") },
                                    leadingIcon = { Icon(Icons.Rounded.Headphones, null, Modifier.size(17.dp)) }
                                )
                                FilterChip(
                                    selected = source == AudioSourceMode.MICROPHONE,
                                    onClick = { changeSource(AudioSourceMode.MICROPHONE) },
                                    label = { Text("Mikrofon") },
                                    leadingIcon = { Icon(Icons.Rounded.Mic, null, Modifier.size(17.dp)) }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (source == AudioSourceMode.DEVICE)
                                    "Android akan meminta izin sekali tiap sesi. Pemutar yang memblokir capture bisa menghasilkan audio kosong."
                                else "Gunakan speaker HP; headphone tidak cocok untuk mode mikrofon. Lingkungan bising bisa mengurangi akurasi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (caption.cues.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val clip = ClipData.newPlainText("Transkrip", caption.cues.joinToString("\n") { it.text })
                                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                        feedback("Teks disalin")
                                    }, modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Salin teks")
                                }
                                OutlinedButton(
                                    onClick = { exportLauncher.launch("localsub-subtitle.srt") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.SaveAlt, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Simpan SRT")
                                }
                            }
                        }
                        Text("Transkripsi berjalan di HP. Subtitle ditampilkan secara bertahap, tidak langsung, dan cap waktu SRT dihitung dari awal sesi (bukan waktu video saat seek).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        SettingsPanel(model, ::changeModel, language, {
                            if (caption.running) feedback("Hentikan transkripsi sebelum mengganti bahasa")
                            else { language = it; settings.language = it }
                        }, downloadStatus,
                            { ModelDownloader.download(context, model) },
                            notifyPermission,
                            { onboarded = false },
                            caption)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(45.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), Alignment.Center) {
            Icon(Icons.Rounded.Subtitles, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("LocalSub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("YouTube • Subtitle AI di perangkat", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
            Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text("LOKAL", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(23.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), content = content)
    }
}

@Composable
private fun SectionHeading(heading: String, subtitle: String) {
    Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(subtitle, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun VideoSurface(videoId: String?) {
    Surface(shape = RoundedCornerShape(23.dp), color = Color(0xFF070A12)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
            if (videoId == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.PlayCircleOutline, null, Modifier.size(52.dp), tint = Color(0xFF8799B3))
                    Text("Video akan muncul di sini", color = Color(0xFFBCC9DA))
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = true
                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()
                            settings.setSupportMultipleWindows(false)
                        }
                    },
                    update = { view ->
                        if (view.tag != videoId) {
                            view.tag = videoId
                            val safeId = checkNotNull(VideoUrl.extract("https://youtu.be/$videoId"))
                            val url = "https://www.youtube.com/embed/$safeId?playsinline=1&rel=0"
                            view.loadUrl(url, mapOf("Referer" to "https://www.youtube.com/"))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SubtitlePanel(state: CaptionViewState) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ClosedCaption, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text("Subtitle langsung", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(shape = CircleShape,
                    color = if (state.running) MaterialTheme.colorScheme.primary.copy(alpha = 0.17f)
                    else MaterialTheme.colorScheme.surfaceVariant) {
                    Text(if (state.running) "ON" else "OFF", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = if (state.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 110.dp)
                    .clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.background)
                    .padding(16.dp), contentAlignment = Alignment.Center
            ) {
                Text(
                    state.cues.lastOrNull()?.text ?: if (state.running) "Mendengarkan dan memproses ucapan..." else "Subtitle muncul di sini setelah kamu menekan Mulai.",
                    textAlign = TextAlign.Center, fontSize = 17.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (state.audioDetected) Icons.Rounded.GraphicEq else Icons.Rounded.Hearing,
                    null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.audioDetected) "Audio terdeteksi • ${state.lastInferenceMs} ms/proses"
                    else state.phase,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.warning?.let { warning ->
                Text(warning, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (state.cues.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Text("Riwayat terkini", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.cues.takeLast(4).dropLast(1).asReversed().forEach { cue ->
                    Text("${minuteSeconds(cue.elapsedMs)}  ${cue.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    chosen: WhisperModel,
    onChoose: (WhisperModel) -> Unit,
    status: ModelStatus,
    download: () -> Unit
) {
    SurfaceCard {
        SectionHeading("Model AI offline", "Unduh sekali, proses suara selanjutnya lokal")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhisperModel.entries.forEach { option ->
                FilterChip(
                    selected = chosen == option, onClick = { onChoose(option) },
                    label = { Text(if (option == WhisperModel.TINY) "Cepat · 32 MB" else "Akurat · 60 MB") },
                    leadingIcon = if (chosen == option) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null
                )
            }
        }
        when (status) {
            is ModelStatus.Ready -> if (status.model == chosen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(7.dp))
                    Text("Model sudah siap offline", style = MaterialTheme.typography.bodyMedium)
                }
            } else DownloadButton(download)
            is ModelStatus.Downloading -> {
                Text(
                    if (status.percent < 0) "Mengunduh ${status.model.title}..."
                    else "Mengunduh ${status.model.title} • ${status.percent}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(7.dp))
                if (status.percent < 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { status.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            is ModelStatus.Error -> {
                Text(status.message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                DownloadButton(download)
            }
            ModelStatus.Idle -> DownloadButton(download)
        }
    }
}

@Composable
private fun DownloadButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Unduh model AI")
    }
}

@Composable
private fun SettingsPanel(
    model: WhisperModel,
    choose: (WhisperModel) -> Unit,
    language: SpeechLang,
    chooseLanguage: (SpeechLang) -> Unit,
    status: ModelStatus,
    download: () -> Unit,
    notifyPermission: () -> Unit,
    reopenGuide: () -> Unit,
    caption: CaptionViewState
) {
    Text("Pengaturan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    ModelCard(model, choose, status, download)
    SurfaceCard {
        SectionHeading("Bahasa ucapan", "Transkripsi sesuai bahasa asli, bukan terjemahan")
        Spacer(Modifier.height(10.dp))
        SpeechLang.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    FilterChip(
                        selected = language == item,
                        onClick = { chooseLanguage(item) },
                        label = { Text(item.label, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    SurfaceCard {
        SectionHeading("Privasi dan kompatibilitas", "Tidak ada server STT, akun, atau token API")
        Spacer(Modifier.height(12.dp))
        Text("Transkripsi menggunakan whisper.cpp di CPU ARM64. Video tetap dimuat dari YouTube. Model diunduh dari Hugging Face sekali saja.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Text("AudioPlaybackCapture hanya bekerja jika Android dan pemutar mengizinkan. Video dengan DRM, pembatasan embed, atau pengaturan capture dapat gagal.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        Text("Status: ${caption.phase} | Versi 1.0.0-beta1", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = notifyPermission, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Notifications, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Izin notifikasi")
        }
        TextButton(onClick = reopenGuide, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.HelpOutline, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Buka panduan lagi")
        }
    }
}

@Composable
private fun Onboarding(onFinish: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val titles = listOf("Video apa pun, satu tempat.", "Subtitle AI tanpa cloud.", "Izinkan audio, lalu mulai.")
    val descriptions = listOf(
        "Tempel URL video YouTube, lalu putar melalui pemutar yang tersedia di aplikasi.",
        "Whisper.cpp memproses audio di HP secara lokal. Unduh model saat pertama kali menggunakan; setelah itu transkripsi tak perlu internet.",
        "Pilih Audio perangkat dan setujui dialog Android. Bila pemutar menolak perekaman, pilih Mikrofon dan nyalakan speaker HP."
    )
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Subtitles, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(9.dp))
            Text("LocalSub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${page + 1}/3", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { (page + 1) / 3f }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(0.18f))
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp).clip(RoundedCornerShape(42.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), Alignment.Center) {
                Icon(
                    when (page) { 0 -> Icons.Rounded.OndemandVideo; 1 -> Icons.Rounded.Memory; else -> Icons.Rounded.Mic },
                    null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(titles[page], style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(descriptions[page], style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (page == 2) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Persiapan pertama", fontWeight = FontWeight.SemiBold)
                    Text("1  Unduh model Tiny / Base.")
                    Text("2  Tempel dan muat URL YouTube.")
                    Text("3  Tekan Mulai dan izinkan perekaman.")
                    Text("4  Tunggu 5–15 detik pertama (bergantung CPU).")
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (page == 2) onFinish() else page++ },
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (page == 2) "Mulai menggunakan" else "Lanjut")
            Spacer(Modifier.width(7.dp))
            Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp))
        }
        if (page > 0) TextButton(onClick = { page-- }, modifier = Modifier.fillMaxWidth()) { Text("Kembali") }
    }
}

private fun minuteSeconds(ms: Long): String = String.format(Locale.US, "%02d:%02d", ms / 60_000, (ms / 1000) % 60)
private fun srtTime(ms: Long): String = String.format(
    Locale.US, "%02d:%02d:%02d,%03d",
    ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000
)
fun toSrt(cues: List<CaptionCue>): String = buildString {
    cues.forEachIndexed { index, cue ->
        val start = if (index == 0) 0L else cues[index - 1].elapsedMs
        val end = cue.elapsedMs.coerceAtLeast(start + 700)
        appendLine(index + 1)
        appendLine("${srtTime(start)} --> ${srtTime(end)}")
        appendLine(cue.text.replace("\n", " "))
        appendLine()
    }
}
