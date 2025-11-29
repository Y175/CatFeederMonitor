package com.example.catfeedermonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.catfeedermonitor.data.FeedingDao
import com.example.catfeedermonitor.data.FeedingRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatsScreen(dao: FeedingDao) {
    val records by dao.getAllRecords().collectAsState(initial = emptyList())

    // Calculate stats
    val today = remember { Calendar.getInstance() }
    val startOfDay = remember(today) {
        val cal = today.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.timeInMillis
    }

    // 过滤今日数据
    val todayRecords = records.filter { it.timestamp >= startOfDay }

    val todaySunnyCount = todayRecords.count { it.catName == "sunny" }
    val todayPutongCount = todayRecords.count { it.catName == "putong" }

    // NEW: 计算今日总时长 (秒)
    val todaySunnyDuration = todayRecords.filter { it.catName == "sunny" }.sumOf { it.duration } / 1000
    val todayPutongDuration = todayRecords.filter { it.catName == "putong" }.sumOf { it.duration } / 1000

    var selectedRecord by remember { mutableStateOf<FeedingRecord?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Dashboard
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("今日概览", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                // Sunny Stats
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sunny", style = MaterialTheme.typography.bodyLarge)
                    Text("$todaySunnyCount 次 | 共 ${formatDuration(todaySunnyDuration)}", style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Putong Stats
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Putong", style = MaterialTheme.typography.bodyLarge)
                    Text("$todayPutongCount 次 | 共 ${formatDuration(todayPutongDuration)}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Text("详细记录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(records) { record ->
                RecordItem(record) {
                    selectedRecord = record
                }
            }
        }
    }

    if (selectedRecord != null) {
        FullImageDialog(record = selectedRecord!!) {
            selectedRecord = null
        }
    }
}

// 辅助函数：格式化秒数为分:秒
fun formatDuration(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m > 0) "${m}分${s}秒" else "${s}秒"
}

@Composable
fun RecordItem(record: FeedingRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(record.imagePath))
                    .crossfade(true)
                    .build(),
                contentDescription = "Thumbnail",
                modifier = Modifier.size(64.dp).background(Color.Gray),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.catName, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    // NEW: 显示单次进食时长标签
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = formatDuration(record.duration / 1000),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                Text(sdf.format(Date(record.timestamp)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun FullImageDialog(record: FeedingRecord, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(record.imagePath))
                    .build(),
                contentDescription = "Full Image",
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                contentScale = ContentScale.Fit
            )
        }
    }
}