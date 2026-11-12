package id.danzku.clearsound.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import id.danzku.clearsound.audio.AudioEngineService
import id.danzku.clearsound.audio.AudioRuntime
import id.danzku.clearsound.audio.EqCurve
import id.danzku.clearsound.audio.SoundPreset
import id.danzku.clearsound.audio.SoundStore
import id.danzku.clearsound.shizuku.ShizukuBridge
import java.util.Locale

private val Mint = Color(0xFF45DCC4)
private val Dark = Color(0xFF0D141C)
private val DarkCard = Color(0xFF18232C)
private val Light = Color(0xFFF6F8FA)

@Composable
fun ClearSoundApp(
    store: SoundStore,
    shizuku: ShizukuBridge,
    onRequestShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onBatterySetup: () -> Unit,
    onBatteryWhitelist: () -> Unit,
    onAddTile: () -> Unit,
    onMessage: (String) -> Unit
) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Mint,
        onPrimary = Dark,
        background = Dark,
        onBackground = Color(0xFFEFF7F5),
        surface = DarkCard,
        onSurface = Color(0xFFEFF7F5),
        surfaceVariant = Color(0xFF24323C),
        onSurfaceVariant = Color(0xFFB2C1C8),
        error = Color(0xFFFFB4A9)
    ) else lightColorScheme(
        primary = Color(0xFF007B6C),
        onPrimary = Color.White,
        background = Light,
        onBackground = Color(0xFF15212A),
        surface = Color.White,
        onSurface = Color(0xFF15212A),
        surfaceVariant = Color(0xFFE9F0F0),
        onSurfaceVariant = Color(0xFF56666E)
    )
    var setupComplete by remember { mutableStateOf(store.onboardingDone) }
    var tab by remember { mutableIntStateOf(0) }
    var selectedPreset by remember { mutableStateOf(store.preset) }
    val gains = remember { mutableStateListOf<Float>().apply { addAll(store.readBands()) } }
    val context = LocalContext.current

    var notificationsAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = granted
        onMessage(if (granted) "Notifikasi layanan diizinkan" else "Notifikasi tidak diizinkan; kamu tetap dapat mencoba fitur audio")
    }

    fun updateCurve() {
        store.saveBands(gains.toList())
        AudioEngineService.refresh(context)
    }
    fun selectPreset(preset: SoundPreset) {
        if (preset == SoundPreset.CUSTOM) return
        store.selectPreset(preset)
        selectedPreset = preset
        gains.clear()
        gains.addAll(preset.values)
        AudioEngineService.refresh(context)
    }

    MaterialTheme(colorScheme = scheme, typography = ClearTypography) {
        if (!setupComplete) {
            SetupWizard(
                shizuku = shizuku,
                notificationsGranted = notificationsAllowed,
                requestNotifications = {
                    if (Build.VERSION.SDK_INT >= 33) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onRequestShizuku = onRequestShizuku,
                onOpenShizuku = onOpenShizuku,
                onAddTile = onAddTile,
                onFinish = {
                    store.onboardingDone = true
                    setupComplete = true
                }
            )
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        listOf(
                            Triple("Beranda", Icons.Rounded.Home, 0),
                            Triple("Equalizer", Icons.Rounded.Equalizer, 1),
                            Triple("Pengaturan", Icons.Rounded.Settings, 2)
                        ).forEach { (title, symbol, index) ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(symbol, contentDescription = null) },
                                label = { Text(title, fontSize = 11.sp) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            ) { padding ->
                Column(
                    Modifier.fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    AppHeader(onSetup = { setupComplete = false })
                    when (tab) {
                        0 -> HomeScreen(
                            gains = gains.toList(),
                            preset = selectedPreset,
                            onPreset = ::selectPreset,
                            onPower = { turnOn ->
                                if (turnOn) AudioEngineService.start(context).onFailure {
                                    AudioRuntime.failed("Gagal memulai layanan: ${it.message ?: "akses ditolak"}")
                                }
                                else AudioEngineService.stop(context)
                            },
                            onEqualizer = { tab = 1 },
                            onSetup = { setupComplete = false }
                        )
                        1 -> EqualizerScreen(
                            gains = gains,
                            preset = selectedPreset,
                            onPreset = ::selectPreset,
                            onBandChange = { index, level ->
                                gains[index] = level
                                selectedPreset = SoundPreset.CUSTOM
                            },
                            onBandChangeFinished = {
                                store.preset = SoundPreset.CUSTOM
                                updateCurve()
                            },
                            onReset = { selectPreset(SoundPreset.NATURAL) }
                        )
                        else -> SettingsScreen(
                            shizuku = shizuku,
                            notificationGranted = notificationsAllowed,
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= 33) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            onOpenShizuku = onOpenShizuku,
                            onRequestShizuku = onRequestShizuku,
                            onBatterySetup = onBatterySetup,
                            onBatteryWhitelist = onBatteryWhitelist,
                            onAddTile = onAddTile,
                            onSetup = { setupComplete = false }
                        )
                    }
                }
            }
        }
    }
}

private val ClearTypography = Typography(
    headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
private fun AppHeader(onSetup: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BrandMark(46.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("ClearSound", style = MaterialTheme.typography.titleLarge)
            Text("Audio enhancer", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onSetup) {
            Icon(Icons.Rounded.HelpOutline, contentDescription = "Buka panduan setup")
        }
    }
}

@Composable
private fun BrandMark(diameter: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(diameter).clip(RoundedCornerShape(14.dp)).background(Mint),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.GraphicEq, null, tint = Dark, modifier = Modifier.size(diameter * 0.62f))
    }
}

@Composable
private fun HomeScreen(
    gains: List<Float>,
    preset: SoundPreset,
    onPreset: (SoundPreset) -> Unit,
    onPower: (Boolean) -> Unit,
    onEqualizer: () -> Unit,
    onSetup: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("PENINGKAT AUDIO", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(if (AudioRuntime.active) "Suara ditingkatkan" else "Siap mendengarkan",
                            style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            if (AudioRuntime.active) "Mesin efek aktif • ${AudioRuntime.physicalBands} band perangkat"
                            else "Nyalakan untuk mencoba efek suara sistem",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(checked = AudioRuntime.active, onCheckedChange = onPower)
                }
                SpectrumGraph(gains = gains, modifier = Modifier.fillMaxWidth().height(150.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (AudioRuntime.active) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            if (AudioRuntime.active) "Efek berhasil dibuat. Pengaruh global bergantung pada ROM."
                            else "Output global memerlukan dukungan AudioEffect perangkat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (AudioRuntime.lastError != null) {
            StatusWarning(AudioRuntime.lastError ?: "Efek gagal diaktifkan", onSetup)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Pilih karakter suara", "Pilih preset sesuai kebutuhan")
            PresetRow(preset, onPreset)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { onEqualizer() },
            shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Equalizer, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Equalizer 10-band", style = MaterialTheme.typography.titleMedium)
                    Text("Atur frekuensi secara presisi", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, null)
            }
        }
        Text("ClearSound bukan Dolby Atmos dan tidak mengubah firmware. Kompatibilitas efek global berbeda pada tiap perangkat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusWarning(message: String, onSetup: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Efek belum berjalan", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text(message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onSetup) { Text("Setup") }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PresetRow(selected: SoundPreset, onPreset: (SoundPreset) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        SoundPreset.entries.filter { it != SoundPreset.CUSTOM }.forEach { preset ->
            val active = selected == preset
            FilterChip(
                selected = active,
                onClick = { onPreset(preset) },
                label = { Text(preset.label, maxLines = 1) },
                leadingIcon = if (active) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(15.dp)
            )
        }
    }
}

@Composable
private fun SpectrumGraph(gains: List<Float>, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(modifier) {
        val pad = 13.dp.toPx()
        val xRange = size.width - pad * 2
        val yBase = size.height / 2f
        val yScale = (size.height - 24.dp.toPx()) / 16f
        for (row in -2..2) {
            val y = yBase + row * yScale * 2
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val points = gains.mapIndexed { index, gain ->
            Offset(pad + xRange * index / (gains.size - 1), yBase - gain * yScale)
        }
        val curve = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val a = points[i]
                val b = points[i + 1]
                val mid = (a.x + b.x) / 2f
                cubicTo(mid, a.y, mid, b.y, b.x, b.y)
            }
        }
        drawPath(curve, tint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { drawCircle(tint, radius = 4.dp.toPx(), center = it) }
    }
}

@Composable
private fun EqualizerScreen(
    gains: List<Float>,
    preset: SoundPreset,
    onPreset: (SoundPreset) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onBandChangeFinished: () -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionTitle("Equalizer", "10 titik kendali • responsif terhadap perangkat") }
            TextButton(onClick = onReset) {
                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset")
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                SpectrumGraph(gains, Modifier.fillMaxWidth().height(150.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("31 Hz", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("0 dB", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("16 kHz", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PresetRow(preset, onPreset)
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EqCurve.frequencies.forEachIndexed { index, frequency ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(frequencyLabel(frequency), modifier = Modifier.width(55.dp),
                            style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = gains[index],
                            onValueChange = { onBandChange(index, it) },
                            onValueChangeFinished = onBandChangeFinished,
                            valueRange = -8f..6f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(String.format(Locale.US, "%+.1f", gains[index]),
                            modifier = Modifier.width(48.dp), textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text("Kurva 10-band diterjemahkan ke jumlah band fisik yang tersedia pada ROM. Penguatan besar dapat menyebabkan distorsi; mulailah dari volume rendah.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun frequencyLabel(hz: Float): String = if (hz >= 1000f) "${(hz / 1000f).toInt()}k" else "${hz.toInt()}"

@Composable
private fun SettingsScreen(
    shizuku: ShizukuBridge,
    notificationGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRequestShizuku: () -> Unit,
    onBatterySetup: () -> Unit,
    onBatteryWhitelist: () -> Unit,
    onAddTile: () -> Unit,
    onSetup: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        SectionTitle("Pengaturan", "Kontrol sistem & kompatibilitas")
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsAction(Icons.Rounded.Security, "Layanan Shizuku",
                    when {
                        shizuku.authorized -> "Terhubung • Izin diberikan"
                        shizuku.available -> "Berjalan • Belum diberi izin"
                        else -> "Tidak terdeteksi / tidak berjalan"
                    }, if (shizuku.available) onRequestShizuku else onOpenShizuku)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                SettingsAction(Icons.Rounded.BatterySaver, "Manajemen baterai",
                    "Izinkan ClearSound berjalan di latar belakang", onBatterySetup)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                SettingsAction(Icons.Rounded.Shield, "Optimasi via Shizuku",
                    "Ajukan whitelist baterai (opsional)", onBatteryWhitelist)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                SettingsAction(Icons.Rounded.Tune, "Tombol Quick Settings",
                    "Tambahkan pengalih audio ke panel atas", onAddTile)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                SettingsAction(Icons.Rounded.Notifications, "Notifikasi pemrosesan",
                    if (notificationGranted) "Izin notifikasi sudah tersedia" else "Ketuk untuk meminta izin",
                    onRequestNotifications)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Diagnostik", style = MaterialTheme.typography.titleMedium)
                KeyValue("Audio Engine", if (AudioRuntime.active) "Aktif" else "Nonaktif")
                KeyValue("Efek fisik", if (AudioRuntime.active) "${AudioRuntime.physicalBands} band" else "Belum diuji")
                KeyValue("Audio Session", "0 • output mix")
                KeyValue("Versi", "0.1.0")
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Text("Efek global hanya berfungsi jika firmware mengizinkan. Shizuku tidak dapat menambahkan Dolby Atmos atau mengubah Audio HAL tanpa hak akses sistem yang sesuai.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.MenuBook, null)
            Spacer(Modifier.width(8.dp))
            Text("Lihat panduan awal lagi")
        }
        Text("ClearSound • DanzKu • Offline & bebas iklan",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SettingsAction(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        .padding(vertical = 9.dp, horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SetupWizard(
    shizuku: ShizukuBridge,
    notificationsGranted: Boolean,
    requestNotifications: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRequestShizuku: () -> Unit,
    onAddTile: () -> Unit,
    onFinish: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 23.dp, vertical = 22.dp)
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(48.dp)
                Spacer(Modifier.weight(1f))
                Text("${page + 1} / 3", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { (page + 1) / 3f },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(10.dp))
            when (page) {
                0 -> {
                    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(164.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.GraphicEq, null,
                                modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text("Suara lebih sesuai seleramu.", style = MaterialTheme.typography.headlineLarge)
                    Text("ClearSound memberikan kontrol sederhana untuk karakter suara, dari vokal jernih hingga bass yang hangat.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WizardBullet(Icons.Rounded.Equalizer, "Equalizer 10-band dengan preset siap pakai")
                    WizardBullet(Icons.Rounded.TouchApp, "Akses cepat dengan satu sentuhan")
                    WizardBullet(Icons.Rounded.LockOutline, "Offline, tidak mengumpulkan audio")
                }
                1 -> {
                    Box(Modifier.fillMaxWidth().height(155.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SpatialAudio, null, Modifier.size(115.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text("Efek nyata, tanpa janji palsu.", style = MaterialTheme.typography.headlineLarge)
                    Text("ClearSound mencoba memasang efek audio di output global Android (Session 0). Tidak semua ROM mengizinkannya.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WizardBullet(Icons.Rounded.CheckCircleOutline, "Mesin efek Android asli, bukan sekadar animasi")
                    WizardBullet(Icons.Rounded.Info, "Dukungan global perlu diuji pada perangkatmu")
                    WizardBullet(Icons.Rounded.Shield, "Shizuku membantu setup, bukan menginstal Dolby")
                }
                else -> {
                    Text("Persiapan terakhir", style = MaterialTheme.typography.headlineLarge)
                    Text("Tiga langkah opsional ini membuat penggunaan lebih praktis. Fitur EQ dapat dicoba tanpa Shizuku.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            WizardStep(
                                number = "01", title = "Shizuku",
                                detail = when {
                                    shizuku.authorized -> "Sudah terhubung dan diizinkan"
                                    shizuku.available -> "Sudah berjalan, perlu izin"
                                    else -> "Buka Shizuku dan jalankan melalui Wireless Debugging"
                                },
                                actionLabel = if (shizuku.available) "Beri izin" else "Buka",
                                onClick = if (shizuku.available) onRequestShizuku else onOpenShizuku
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            WizardStep("02", "Notifikasi",
                                if (notificationsGranted) "Izin sudah tersedia" else "Aktifkan notifikasi layanan saat berjalan",
                                if (notificationsGranted) "Selesai" else "Izinkan", requestNotifications)
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            WizardStep("03", "Panel cepat", "Tambahkan tombol audio ke Quick Settings",
                                "Tambah", onAddTile)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { if (page < 2) page++ else onFinish() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(17.dp)
            ) {
                Text(if (page == 2) "Masuk ke ClearSound" else "Lanjutkan", fontSize = 15.sp)
                Spacer(Modifier.width(9.dp))
                Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp))
            }
            if (page > 0) {
                TextButton(onClick = { page-- }, modifier = Modifier.fillMaxWidth()) { Text("Kembali") }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun WizardBullet(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(11.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WizardStep(number: String, title: String, detail: String, actionLabel: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(35.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(number, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text(actionLabel) }
    }
}
