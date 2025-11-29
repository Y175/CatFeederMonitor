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
    
    val startOfWeek = remember(today) {
        val cal = today.clone() as Calendar
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.timeInMillis
    }

    val todaySunny = records.count { it.catName == "sunny" && it.timestamp >= startOfDay }
    val todayPutong = records.count { it.catName == "putong" && it.timestamp >= startOfDay }
    
    val weekSunny = records.count { it.catName == "sunny" && it.timestamp >= startOfWeek }
    val weekPutong = records.count { it.catName == "putong" && it.timestamp >= startOfWeek }

    var selectedRecord by remember { mutableStateOf<FeedingRecord?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Dashboard
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("今日数据", style = MaterialTheme.typography.titleMedium)
                Text("Sunny: $todaySunny 次 | Putong: $todayPutong 次", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("本周统计", style = MaterialTheme.typography.titleMedium)
                Text("Sunny: $weekSunny 次 | Putong: $weekPutong 次", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Text("历史记录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))

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
                Text(record.catName, style = MaterialTheme.typography.titleMedium)
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
