package com.myenvironment.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myenvironment.launcher.core.model.LayoutItem

/**
 * 未インストールアプリのPlaceholderタップ時に表示するダイアログ (仕様 24, 25)
 *
 * 「このアプリはインストールされていません」
 * - [Playストアで開く]
 * - [ホームから削除]
 * - [キャンセル]
 */
@Composable
fun MissingAppDialog(
    item: LayoutItem,
    onOpenPlayStore: (String) -> Unit,
    onRemoveFromHome: (LayoutItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "このアプリはインストールされていません",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "アプリ名: ${item.label}")
                if (item.packageName.isNotBlank()) {
                    Text(
                        text = "Package: ${item.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Playストアから再インストールすると、この位置のまま自動的にアイコンが復元されます。",
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        onOpenPlayStore(item.packageName)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Playストアで開く")
                }

                OutlinedButton(
                    onClick = {
                        onRemoveFromHome(item)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ホームから削除", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
