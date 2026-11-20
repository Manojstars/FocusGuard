package com.focusguard.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.FocusSessionService
import com.focusguard.app.core.SessionState

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        val label = try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) { pkg }
        setContent {
            val session by SessionState.flow.collectAsState()
            if (!session.active) { finish() }
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF07130D)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("🛡️", fontSize = 56.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("$label is blocked", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("You are in a focus session.", color = Color(0xFFB7E4C7), fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(FocusSessionService.format(session.remainingSeconds) + " left",
                        color = Color(0xFF7CFC9A), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = {
                        val home = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME); flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(home); finish()
                    }) { Text("Go Home") }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {
                        startActivity(Intent(this@BlockActivity, MainActivity::class.java)); finish()
                    }) { Text("Back to FocusGuard", color = Color.White) }
                }
            }
        }
    }
    companion object { const val EXTRA_PKG = "blocked_pkg" }
}
