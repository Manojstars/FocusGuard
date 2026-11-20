package com.focusguard.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.FocusSessionService
import com.focusguard.app.core.Permissions
import com.focusguard.app.core.SessionState
import com.focusguard.app.core.UsageStatsHelper
import com.focusguard.app.ui.appLabel
import com.focusguard.app.ui.installedLaunchableApps
import java.time.LocalDate

private val Bg = Color(0xFF07130D)
private val Card = Color(0xFF0E241A)
private val Accent = Color(0xFF7CFC9A)
private val Muted = Color(0xFFB7E4C7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionStore.init(this)
        setContent { FocusGuardRoot() }
    }
}

@Composable
fun FocusGuardRoot() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card)) {
        var tab by remember { mutableStateOf(0) }
        var showSetup by remember { mutableStateOf(false) }
        val session by SessionState.flow.collectAsState()
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Card) {
                    listOf("Home", "Stats", "Block", "Setup").forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i; showSetup = false },
                            label = { Text(label) },
                            icon = {
                                Icon(
                                    when (i) {
                                        0 -> Icons.Filled.Home
                                        1 -> Icons.Filled.BarChart
                                        2 -> Icons.Filled.Block
                                        else -> Icons.Filled.Settings
                                    }, contentDescription = label
                                )
                            }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().background(Bg)) {
                when {
                    session.active -> RunningScreen()
                    showSetup -> FocusSetupScreen(onStarted = { showSetup = false })
                    tab == 0 -> HomeScreen(onStartFocus = { showSetup = true })
                    tab == 1 -> StatsScreen()
                    tab == 2 -> BlockListScreen()
                    else -> PermissionsScreen()
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onStartFocus: () -> Unit) {
    val todayMin = SessionStore.focusMinutesOn(LocalDate.now())
    val goal = SessionStore.dailyGoalMinutes
    val streak = SessionStore.currentStreak()
    LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Text("FocusGuard", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                if (SessionStore.exam.isNotBlank()) "Target: ${SessionStore.exam}" else "Block distractions. Study deep.",
                color = Muted, fontSize = 15.sp
            )
            Spacer(Modifier.height(20.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Today's focus", color = Muted)
                    Text("$todayMin / $goal min", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { (todayMin.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        color = Accent, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    )
                    Text("🔥 $streak day streak", color = Accent, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onStartFocus, modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)
            ) { Text("Start Focus", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(14.dp))
            Text(
                "${SessionStore.blockedPackages.size} apps will be blocked during your session.",
                color = Muted, fontSize = 13.sp
            )
        }
    }
}

@Composable
fun FocusSetupScreen(onStarted: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("Timer") }
    var minutes by remember { mutableIntStateOf(25) }
    var subject by remember { mutableStateOf("Study") }
    val subjects = listOf("Study", "Physics", "Chemistry", "Maths", "Biology", "English", "Revision")
    LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Text("New session", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Timer", "Pomodoro", "Stopwatch").forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) })
                }
            }
            Spacer(Modifier.height(18.dp))
            if (mode != "Stopwatch") {
                Text("Duration: $minutes min", color = Color.White, fontSize = 17.sp)
                Slider(
                    value = minutes.toFloat(), onValueChange = { minutes = it.toInt() },
                    valueRange = 5f..240f, steps = 46
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60, 90).forEach { preset ->
                        AssistChip(onClick = { minutes = preset }, label = { Text("$preset") })
                    }
                }
                if (mode == "Pomodoro") {
                    Text("Pomodoro: focus, then a 5-min break at the end.", color = Muted, fontSize = 13.sp)
                }
            } else {
                Text("Stopwatch counts up until you stop it.", color = Muted)
            }
            Spacer(Modifier.height(16.dp))
            Text("Subject", color = Color.White, fontSize = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                subjects.take(4).forEach { s -> FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(s) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                subjects.drop(4).forEach { s -> FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(s) }) }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    FocusSessionService.start(context, mode, subject, minutes)
                    onStarted()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)
            ) { Text("Start Focus Now", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Text(
                "Blocked apps: ${SessionStore.blockedPackages.size}. Change them in the Block tab before starting.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
fun RunningScreen() {
    val context = LocalContext.current
    val session by SessionState.flow.collectAsState()
    val isStopwatch = session.mode == "Stopwatch"
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(session.mode, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(session.subject, color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            FocusSessionService.format(if (isStopwatch) session.elapsedSeconds else session.remainingSeconds),
            color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Bold
        )
        Text(
            if (isStopwatch) "elapsed" else "${session.blocked.size} apps blocked",
            color = Muted
        )
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (session.paused) {
                Button(onClick = { FocusSessionService.send(context, FocusSessionService.ACTION_RESUME) }) { Text("Resume") }
            } else {
                OutlinedButton(onClick = { FocusSessionService.send(context, FocusSessionService.ACTION_PAUSE) }) {
                    Text("Pause", color = Color.White)
                }
            }
            Button(
                onClick = { FocusSessionService.send(context, FocusSessionService.ACTION_STOP) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
            ) { Text("End Session") }
        }
        if (!isStopwatch) {
            Text("Ending early still saves sessions of 1+ minute to your stats.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val records = remember { SessionStore.records().reversed() }
    val usage = remember { UsageStatsHelper.todayUsage(context) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Text("Stats", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("This week: ${records.filter { it.date >= LocalDate.now().minusDays(6).toString() }.sumOf { it.minutes }} focus min", color = Accent, fontSize = 17.sp)
            Text("Streak: ${SessionStore.currentStreak()} days", color = Muted)
            Spacer(Modifier.height(18.dp))
            Text("Recent sessions", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (records.isEmpty()) {
            item { Text("No sessions yet. Start your first focus session.", color = Muted) }
        }
        items(records.take(20)) { r ->
            Text("${r.date} · ${r.subject} · ${r.mode} · ${r.minutes} min", color = Color.White, modifier = Modifier.padding(vertical = 4.dp))
        }
        item {
            Spacer(Modifier.height(18.dp))
            Text("Top apps today (screen time)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (usage.isEmpty()) Text("Grant Usage Access in Setup to see screen time.", color = Muted)
        }
        items(usage) { u ->
            Text("${appLabel(context, u.pkg)} · ${u.minutes} min", color = Muted, modifier = Modifier.padding(vertical = 3.dp))
        }
    }
}

@Composable
fun BlockListScreen() {
    val context = LocalContext.current
    val apps = remember { installedLaunchableApps(context) }
    var blocked by remember { mutableStateOf(SessionStore.blockedPackages) }
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Block list", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("These apps are blocked during every focus session.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(apps.filter { it.label.contains(query, ignoreCase = true) }) { app ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = Color.White, fontSize = 16.sp)
                        Text(app.pkg, color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = blocked.contains(app.pkg),
                        onCheckedChange = { on ->
                            blocked = if (on) blocked + app.pkg else blocked - app.pkg
                            SessionStore.blockedPackages = blocked
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionsScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val unused = refresh
    LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Text("Setup & permissions", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Blocking needs these. Each one is asked with a reason, and blocking data never leaves your device.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            PermissionRow("Usage Access", "Detect the app in front of you + screen-time stats", Permissions.hasUsageAccess(context)) {
                context.startActivity(Permissions.usageAccessIntent())
            }
            PermissionRow("Accessibility", "Instantly block apps you picked when you open them mid-session", Permissions.isAccessibilityEnabled(context)) {
                context.startActivity(Permissions.accessibilityIntent())
            }
            PermissionRow("Display over other apps", "Show the block screen over a blocked app", Permissions.canOverlay(context)) {
                context.startActivity(Permissions.overlayIntent(context))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                try { context.startActivity(Permissions.batteryIntent(context)) } catch (e: Exception) { }
            }) { Text("Allow background running (battery)", color = Color.White) }
            Spacer(Modifier.height(18.dp))
            Text("Your goal", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            var exam by remember { mutableStateOf(SessionStore.exam) }
            OutlinedTextField(
                value = exam, onValueChange = { exam = it; SessionStore.exam = it },
                label = { Text("Exam (NEET, JEE, UPSC, SSC...)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(120, 240, 360, 480).forEach { g ->
                    FilterChip(
                        selected = SessionStore.dailyGoalMinutes == g,
                        onClick = { SessionStore.dailyGoalMinutes = g; refresh++ },
                        label = { Text("${g / 60}h") }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { SessionStore.onboarded = true; refresh++ }, modifier = Modifier.fillMaxWidth()) {
                Text(if (SessionStore.onboarded) "Setup saved ✓" else "Save setup")
            }
            Text("Tip: after changing a permission, come back here — the ticks update when you revisit this tab.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
fun PermissionRow(title: String, desc: String, granted: Boolean, onOpen: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(desc, color = Muted, fontSize = 12.sp)
            }
            if (granted) Text("✓ On", color = Accent, fontWeight = FontWeight.Bold)
            else Button(onClick = onOpen) { Text("Allow") }
        }
    }
}
