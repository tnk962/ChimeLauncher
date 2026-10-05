package com.myenvironment.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.myenvironment.launcher.core.feed.FeedCategory
import com.myenvironment.launcher.core.model.LauncherSettings

@Composable
internal fun FeedSettingsScreen(
    settings: LauncherSettings,
    isEmbeddedPage: Boolean,
    onSetFeedCategoryEnabled: (FeedCategory, Boolean) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(if (isEmbeddedPage) Color(0xCC101218) else Color(0xF5111318))
            .then(if (isEmbeddedPage) Modifier else Modifier.statusBarsPadding())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "設定に戻る", tint = Color.White)
            }
            Text("フィード設定", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Text("独自フィードに表示するジャンルを選んでください。",
            style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBDC1C6))
        Text("表示するフィード: ${settings.enabledFeedCategories.size} / ${FeedCategory.entries.size}",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xDD1A1D24), contentColor = Color.White)) {
            Column {
                FeedCategory.entries.forEachIndexed { index, category ->
                    val enabled = category.id !in settings.disabledFeedCategoryIds
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .toggleable(value = enabled, role = Role.Switch,
                                onValueChange = { onSetFeedCategoryEnabled(category, it) })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(category.label, modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = null)
                    }
                    if (index < FeedCategory.entries.lastIndex) HorizontalDivider(color = Color(0x33FFFFFF))
                }
            }
        }
        Text("すべてOFFの場合、独自フィードには設定への案内を表示します。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
