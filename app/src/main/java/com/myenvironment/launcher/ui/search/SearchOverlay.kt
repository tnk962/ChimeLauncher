package com.myenvironment.launcher.ui.search

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherAction
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.core.search.SearchEngine
import com.myenvironment.launcher.ui.components.LauncherItemGraphic
import kotlinx.coroutines.delay

/**
 * Swipe Up Search オーバーレイ画面 (仕様 8, 9)
 *
 * - 表示直後に TextField へ Focus し、ソフトキーボードを表示する。
 * - 検索優先順位:
 *   1. インストール済みアプリ
 *   2. Launcherショートカット
 *   3. Launcher独自Action
 *   4. Web検索 (Googleで「検索文字列」を検索)
 */
@Composable
fun SearchOverlay(
    installedApps: List<AppInfo>,
    configuredShortcuts: List<LayoutItem>,
    searchEngine: SearchEngine,
    appDiscoveryRepository: AppDiscoveryRepository,
    onLaunchApp: (AppInfo) -> Unit,
    onLaunchShortcut: (LayoutItem) -> Unit,
    onTriggerAction: (LauncherAction) -> Unit,
    onGoogleSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchResults = remember(query, installedApps, configuredShortcuts) {
        searchEngine.search(
            query = query,
            installedApps = installedApps,
            configuredShortcuts = configuredShortcuts
        )
    }

    // 検索開始直後に TextField へ Focus & ソフトキーボード表示 (仕様 8.1)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        delay(60)
        keyboardController?.show()
    }

    Surface(
        color = Color(0xEE101217),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 検索入力バー
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("アプリ、ショートカット、Action、Webを検索...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "クリア")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val topApp = searchResults.matchingApps.firstOrNull()
                            if (topApp != null) {
                                onLaunchApp(topApp)
                                onDismiss()
                            } else if (query.isNotBlank()) {
                                onGoogleSearch(query)
                                onDismiss()
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "検索を閉じる",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 検索結果リスト (優先順位: 1. Apps -> 2. Shortcuts -> 3. Actions -> 4. Web)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. インストール済みアプリ
                if (searchResults.matchingApps.isNotEmpty()) {
                    item {
                        SearchSectionHeader("Apps (${searchResults.matchingApps.size})")
                    }
                    items(
                        items = searchResults.matchingApps,
                        key = { "app_${it.packageName}_${it.activityName}" }
                    ) { app ->
                        Surface(
                            color = Color(0xFF1C2028),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLaunchApp(app)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                LauncherItemGraphic(
                                    type = ItemType.APP,
                                    packageName = app.packageName,
                                    activityName = app.activityName,
                                    targetUri = "",
                                    label = app.label,
                                    isInstalled = true,
                                    appDiscoveryRepository = appDiscoveryRepository,
                                    iconSize = 36.dp,
                                    showLabel = false
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = app.label,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = Color(0xFF9AA0A6),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Launcher ショートカット
                if (searchResults.matchingShortcuts.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        SearchSectionHeader("Shortcuts")
                    }
                    items(
                        items = searchResults.matchingShortcuts,
                        key = { "shortcut_${it.id}" }
                    ) { shortcut ->
                        Surface(
                            color = Color(0xFF1C2028),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLaunchShortcut(shortcut)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(text = "🔗", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = shortcut.label,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = shortcut.targetUri,
                                        color = Color(0xFF9AA0A6),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Launcher 独自 Action
                if (searchResults.matchingActions.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        SearchSectionHeader("Actions")
                    }
                    items(
                        items = searchResults.matchingActions,
                        key = { "action_${it.name}" }
                    ) { action ->
                        Surface(
                            color = Color(0xFF1C2028),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onTriggerAction(action)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(text = action.emojiIcon, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = action.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = action.subtitle,
                                        color = Color(0xFF9AA0A6),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Web検索（検索結果最下部に「Googleで『検索文字列』を検索」を表示: 仕様 9）
                if (searchResults.webSearchQuery.isNotBlank()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        SearchSectionHeader("Web")
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onGoogleSearch(searchResults.webSearchQuery)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Googleで「${searchResults.webSearchQuery}」を検索",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
    }
}
