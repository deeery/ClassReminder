package com.example.classreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class LockOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setShowWhenLocked / setTurnScreenOn were added in API 27; guard calls for older devices
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val name = intent.getStringExtra("name") ?: "（无）"
        val start = intent.getLongExtra("start", 0L)
        val end = intent.getLongExtra("end", 0L)
        val room = intent.getStringExtra("room") ?: ""

        setContent {
            MaterialTheme {
                OverlayContent(name, start, end, room) { finish() }
            }
        }
    }
}

@Composable
fun OverlayContent(name: String, start: Long, end: Long, room: String, onDismiss: () -> Unit) {
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xCC000000))) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .fillMaxWidth(0.95f)
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text(text = name, fontSize = 22.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "上课：${formatTime(start)}  下课：${formatTime(end)}", color = Color.DarkGray)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "教室：$room", color = Color.DarkGray)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val m = cal.get(java.util.Calendar.MINUTE)
    return String.format("%02d:%02d", h, m)
}


