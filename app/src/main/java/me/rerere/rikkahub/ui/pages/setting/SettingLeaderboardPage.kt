package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trophy
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.plus

@Composable
fun SettingLeaderboardPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings = LocalSettings.current
    val scores = settings.modelArenaScores.entries
        .sortedWith(compareByDescending<Map.Entry<kotlin.uuid.Uuid, Int>> { it.value }.thenBy { it.key.toString() })

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(text = "模型排行")
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    Icon(imageVector = Lucide.Trophy, contentDescription = null)
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (scores.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "暂无匿名竞技排行数据",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                return@LazyColumn
            }

            itemsIndexed(scores, key = { _, entry -> entry.key }) { index, entry ->
                val modelId = entry.key
                val score = entry.value
                val model = settings.findModelById(modelId)

                ListItem(
                    leadingContent = {
                        if (model != null) {
                            AutoAIIcon(
                                name = model.modelId,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(text = "#${index + 1}")
                        }
                    },
                    headlineContent = {
                        Text(
                            text = model?.displayName ?: modelId.toString(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(text = "得分: $score")
                    },
                    trailingContent = {
                        Text(
                            text = "#${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                )
            }
        }
    }
}

