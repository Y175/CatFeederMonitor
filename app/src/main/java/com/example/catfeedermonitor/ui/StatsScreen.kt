package com.example.catfeedermonitor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.catfeedermonitor.data.FeedingDao
import com.example.catfeedermonitor.data.FeedingRecord
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Custom Colors for a Premium Look
val CatOrange = Color(0xFFFF8F00)
val CatCream = Color(0xFFFFF3E0)
val DarkSlate = Color(0xFF263238)
val SoftSurface = Color(0xFFF5F5F5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(dao: FeedingDao) {
    val records by dao.getAllRecords().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var recordToDelete by remember { mutableStateOf<FeedingRecord?>(null) }

    // Calculate stats
    val today = remember { Calendar.getInstance() }
    val startOfDay = remember(today) {
        val cal = today.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.timeInMillis
    }

    val todayRecords = records.filter { it.timestamp >= startOfDay }
    val todaySunnyCount = todayRecords.count { it.catName.equals("sunny", ignoreCase = true) }
    val todayPutongCount = todayRecords.count {
        it.catName == "噗通" || it.catName.equals("putong", ignoreCase = true)
    }
    val todaySunnyDuration = todayRecords.filter { it.catName.equals("sunny", ignoreCase = true) }.sumOf { it.duration } / 1000
    val todayPutongDuration = todayRecords
        .filter { it.catName == "噗通" || it.catName.equals("putong", ignoreCase = true) }
        .sumOf { it.duration } / 1000
    var selectedRecord by remember { mutableStateOf<FeedingRecord?>(null) }

    Scaffold(
        containerColor = SoftSurface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "进食统计",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkSlate
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Dashboard Section
            Text(
                "今日概览",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.Gray),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    name = "Sunny",
                    count = todaySunnyCount,
                    duration = todaySunnyDuration,
                    color = CatOrange,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    name = "噗通",
                    count = todayPutongCount,
                    duration = todayPutongDuration,
                    color = Color(0xFF5C6BC0), // Indigo for contrast
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // History Section
            Text(
                "历史记录",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.Gray),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    RecordItem(
                        record = record,
                        onClick = { selectedRecord = record },
                        onDelete = { recordToDelete = record }
                    )
                }
            }
        }
    }

    // Full Image Dialog
    if (selectedRecord != null) {
        FullImageDialog(record = selectedRecord!!) {
            selectedRecord = null
        }
    }

    // Delete Confirmation Dialog
    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("删除记录？") },
            text = { Text("确定要删除这条进食记录吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            recordToDelete?.let { dao.delete(it) }
                            recordToDelete = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun StatCard(
    name: String,
    count: Int,
    duration: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Pets,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = DarkSlate)
                )
                Text(
                    " 次",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    formatDuration(duration),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                )
            }
        }
    }
}

@Composable
fun RecordItem(
    record: FeedingRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                androidx.compose.animation.fadeIn()
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(record.imagePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = "缩略图",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            record.catName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (record.catName.equals("sunny", ignoreCase = true)) CatOrange.copy(alpha = 0.1f) else Color(0xFF5C6BC0).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = formatDuration(record.duration / 1000),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (record.catName.equals("sunny", ignoreCase = true)) CatOrange else Color(0xFF5C6BC0)
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    Text(
                        sdf.format(Date(record.timestamp)),
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                }

                // Delete Button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
fun FullImageDialog(record: FeedingRecord, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Box(modifier = Modifier.clickable(onClick = onDismiss)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(record.imagePath))
                        .build(),
                    contentDescription = "查看大图",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

fun formatDuration(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m > 0) "${m}分 ${s}秒" else "${s}秒"
}