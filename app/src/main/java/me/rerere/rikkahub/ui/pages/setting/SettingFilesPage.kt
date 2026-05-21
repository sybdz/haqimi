package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@Composable
fun SettingFilesPage(
    filesManager: FilesManager = koinInject(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val selectedFolder = FileFolders.UPLOAD
    val files by filesManager.observe(selectedFolder).collectAsState(initial = emptyList())

    val deletedToast = stringResource(R.string.setting_files_page_deleted_toast)
    val deleteFailedToast = stringResource(R.string.setting_files_page_delete_failed_toast)
    val openFailedToast = stringResource(R.string.setting_files_page_open_failed_toast)

    var selectedCategory by remember { mutableStateOf(StorageCategory.ALL) }
    var selectedSort by remember { mutableStateOf(StorageSort.NEWEST) }
    var pendingDelete by remember { mutableStateOf<ManagedFileEntity?>(null) }
    var previewImage by remember { mutableStateOf<String?>(null) }
    var previewVideo by remember { mutableStateOf<StorageFileItem?>(null) }

    LaunchedEffect(selectedFolder) {
        filesManager.syncFolder(selectedFolder)
    }

    val items = remember(files) {
        files.map { entity ->
            val fileOnDisk = filesManager.getFile(entity)
            StorageFileItem(
                entity = entity,
                fileOnDisk = fileOnDisk,
                category = entity.storageCategory(),
                exists = fileOnDisk.exists(),
            )
        }
    }
    val stats = remember(items) { items.storageStats() }
    val visibleItems = remember(items, selectedCategory, selectedSort) {
        items
            .asSequence()
            .filter { selectedCategory == StorageCategory.ALL || it.category == selectedCategory }
            .let { sequence ->
                when (selectedSort) {
                    StorageSort.NEWEST -> sequence.sortedByDescending { it.entity.createdAt }
                    StorageSort.LARGEST -> sequence.sortedByDescending { it.effectiveSize }
                    StorageSort.NAME -> sequence.sortedBy { it.entity.displayName.lowercase() }
                }
            }
            .toList()
    }

    previewImage?.let { image ->
        ImagePreviewDialog(images = listOf(image), onDismissRequest = { previewImage = null })
    }

    previewVideo?.let { video ->
        VideoPreviewDialog(item = video, onDismissRequest = { previewVideo = null })
    }

    if (pendingDelete != null) {
        val target = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.setting_files_page_delete_file_title)) },
            text = { Text(target.displayName) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val ok = filesManager.delete(target.id, deleteFromDisk = true)
                            toaster.show(if (ok) deletedToast else deleteFailedToast)
                            pendingDelete = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.setting_files_page_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.setting_files_page_cancel_action))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_files_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StorageOverview(
                    stats = stats,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                CategoryRow(
                    stats = stats,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
            }

            item {
                SortRow(
                    selectedSort = selectedSort,
                    onSortSelected = { selectedSort = it }
                )
            }

            if (visibleItems.isEmpty()) {
                item {
                    EmptyStorageState(
                        text = if (items.isEmpty()) {
                            stringResource(R.string.setting_files_page_no_files)
                        } else {
                            stringResource(R.string.setting_files_page_no_matching_files)
                        }
                    )
                }
            } else {
                item {
                    FileTableHeader(modifier = Modifier.padding(horizontal = 16.dp))
                }
                items(visibleItems, key = { it.entity.id }) { item ->
                    FileTableRow(
                        item = item,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onOpen = {
                            when {
                                item.category == StorageCategory.IMAGE && item.exists -> {
                                    previewImage = item.fileOnDisk.toUri().toString()
                                }

                                item.category == StorageCategory.VIDEO && item.exists -> {
                                    previewVideo = item
                                }

                                openManagedFile(it, item).not() -> {
                                    toaster.show(openFailedToast)
                                }
                            }
                        },
                        onDelete = { pendingDelete = item.entity }
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageOverview(
    stats: StorageStats,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.setting_files_page_storage_overview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatTile(
                    label = stringResource(R.string.setting_files_page_total_size),
                    value = formatBytes(stats.totalSize)
                )
                StatTile(
                    label = stringResource(R.string.setting_files_page_total_files),
                    value = stats.totalCount.toString()
                )
                StatTile(
                    label = stringResource(R.string.setting_files_page_largest_file),
                    value = stats.largestFile?.let { formatBytes(it.effectiveSize) } ?: "-"
                )
                StatTile(
                    label = stringResource(R.string.setting_files_page_missing_files),
                    value = stats.missingCount.toString()
                )
            }

            if (stats.totalCount > 0) {
                CategoryDistribution(stats)
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .width(144.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryDistribution(stats: StorageStats) {
    val categoryStats = StorageCategory.contentCategories().mapNotNull { category ->
        stats.byCategory[category]?.takeIf { it.count > 0 }?.let { category to it }
    }
    if (categoryStats.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.setting_files_page_distribution),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            categoryStats.forEach { (category, stat) ->
                Box(
                    modifier = Modifier
                        .weight(stat.sizeBytes.coerceAtLeast(1L).toFloat())
                        .fillMaxSize()
                        .background(category.color())
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categoryStats.forEach { (category, stat) ->
                CategoryLegend(category = category, stat = stat)
            }
        }
    }
}

@Composable
private fun CategoryLegend(
    category: StorageCategory,
    stat: CategoryStat,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(category.color())
        )
        Text(
            text = "${category.label()} ${stat.count} · ${formatBytes(stat.sizeBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryRow(
    stats: StorageStats,
    selectedCategory: StorageCategory,
    onCategorySelected: (StorageCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StorageCategory.entries.forEach { category ->
            val count = if (category == StorageCategory.ALL) {
                stats.totalCount
            } else {
                stats.byCategory[category]?.count ?: 0
            }
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text("${category.label()} ($count)") },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun SortRow(
    selectedSort: StorageSort,
    onSortSelected: (StorageSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StorageSort.entries.forEach { sort ->
            FilterChip(
                selected = selectedSort == sort,
                onClick = { onSortSelected(sort) },
                label = { Text(sort.label()) }
            )
        }
    }
}

@Composable
private fun FileTableHeader(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.setting_files_page_table_file),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.setting_files_page_table_size),
                modifier = Modifier.width(82.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(48.dp))
        }
        HorizontalDivider()
    }
}

@Composable
private fun FileTableRow(
    item: StorageFileItem,
    modifier: Modifier = Modifier,
    onOpen: (Context) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val categoryLabel = item.category.label()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpen(context) },
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilePreviewThumb(item)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.entity.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.exists) {
                        "$categoryLabel · ${formatDate(item.entity.createdAt)}"
                    } else {
                        "$categoryLabel · ${stringResource(R.string.setting_files_page_missing)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.exists) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatBytes(item.effectiveSize),
                modifier = Modifier.width(82.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onDelete) {
                Icon(
                    HugeIcons.Delete01,
                    contentDescription = stringResource(R.string.setting_files_page_delete_content_description),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun FilePreviewThumb(item: StorageFileItem) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (item.category == StorageCategory.IMAGE && item.exists) {
                AsyncImage(
                    model = item.fileOnDisk,
                    contentDescription = item.entity.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = item.category.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyStorageState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VideoPreviewDialog(
    item: StorageFileItem,
    onDismissRequest: () -> Unit,
) {
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            videoView?.stopPlayback()
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.entity.displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.setting_files_page_cancel_action))
                    }
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    factory = { context ->
                        VideoView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val controller = MediaController(context)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoURI(item.fileOnDisk.toUri())
                            setOnPreparedListener { player ->
                                player.isLooping = false
                                start()
                            }
                            videoView = this
                        }
                    }
                )
            }
        }
    }
}

private fun openManagedFile(context: Context, item: StorageFileItem): Boolean {
    if (!item.exists) return false
    return runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            item.fileOnDisk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.entity.mimeType.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, item.entity.displayName))
        true
    }.getOrDefault(false)
}

private data class StorageFileItem(
    val entity: ManagedFileEntity,
    val fileOnDisk: File,
    val category: StorageCategory,
    val exists: Boolean,
) {
    val effectiveSize: Long
        get() = if (exists) {
            fileOnDisk.length().takeIf { it > 0L } ?: entity.sizeBytes
        } else {
            entity.sizeBytes
        }
}

private data class StorageStats(
    val totalCount: Int,
    val totalSize: Long,
    val largestFile: StorageFileItem?,
    val missingCount: Int,
    val byCategory: Map<StorageCategory, CategoryStat>,
)

private data class CategoryStat(
    val count: Int,
    val sizeBytes: Long,
)

private enum class StorageCategory {
    ALL,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    OTHER;

    companion object {
        fun contentCategories(): List<StorageCategory> = listOf(IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER)
    }
}

private enum class StorageSort {
    NEWEST,
    LARGEST,
    NAME,
}

@Composable
private fun StorageCategory.label(): String = when (this) {
    StorageCategory.ALL -> stringResource(R.string.setting_files_page_category_all)
    StorageCategory.IMAGE -> stringResource(R.string.setting_files_page_category_images)
    StorageCategory.VIDEO -> stringResource(R.string.setting_files_page_category_videos)
    StorageCategory.AUDIO -> stringResource(R.string.setting_files_page_category_audio)
    StorageCategory.DOCUMENT -> stringResource(R.string.setting_files_page_category_documents)
    StorageCategory.OTHER -> stringResource(R.string.setting_files_page_category_other)
}

private fun StorageCategory.icon(): ImageVector = when (this) {
    StorageCategory.ALL -> HugeIcons.File02
    StorageCategory.IMAGE -> HugeIcons.Image02
    StorageCategory.VIDEO -> HugeIcons.Video01
    StorageCategory.AUDIO -> HugeIcons.MusicNote03
    StorageCategory.DOCUMENT -> HugeIcons.File02
    StorageCategory.OTHER -> HugeIcons.File02
}

@Composable
private fun StorageCategory.color(): Color = when (this) {
    StorageCategory.IMAGE -> MaterialTheme.colorScheme.primary
    StorageCategory.VIDEO -> MaterialTheme.colorScheme.tertiary
    StorageCategory.AUDIO -> MaterialTheme.colorScheme.secondary
    StorageCategory.DOCUMENT -> MaterialTheme.colorScheme.error
    StorageCategory.OTHER,
    StorageCategory.ALL,
        -> MaterialTheme.colorScheme.outline
}

@Composable
private fun StorageSort.label(): String = when (this) {
    StorageSort.NEWEST -> stringResource(R.string.setting_files_page_sort_newest)
    StorageSort.LARGEST -> stringResource(R.string.setting_files_page_sort_largest)
    StorageSort.NAME -> stringResource(R.string.setting_files_page_sort_name)
}

private fun List<StorageFileItem>.storageStats(): StorageStats {
    val totalSize = sumOf { it.effectiveSize }
    val byCategory = StorageCategory.contentCategories().associateWith { category ->
        val categoryItems = filter { it.category == category }
        CategoryStat(
            count = categoryItems.size,
            sizeBytes = categoryItems.sumOf { it.effectiveSize }
        )
    }
    return StorageStats(
        totalCount = size,
        totalSize = totalSize,
        largestFile = maxByOrNull { it.effectiveSize },
        missingCount = count { !it.exists },
        byCategory = byCategory,
    )
}

private fun ManagedFileEntity.storageCategory(): StorageCategory {
    val mime = mimeType.lowercase()
    val extension = displayName.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("image/") || extension in imageExtensions -> StorageCategory.IMAGE
        mime.startsWith("video/") || extension in videoExtensions -> StorageCategory.VIDEO
        mime.startsWith("audio/") || extension in audioExtensions -> StorageCategory.AUDIO
        mime.startsWith("text/") ||
            mime in documentMimeTypes ||
            extension in documentExtensions ->
            StorageCategory.DOCUMENT

        else -> StorageCategory.OTHER
    }
}

private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "svg")
private val videoExtensions = setOf("mp4", "m4v", "mov", "webm", "mkv", "avi", "3gp")
private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus")
private val documentExtensions = setOf(
    "txt",
    "md",
    "pdf",
    "doc",
    "docx",
    "ppt",
    "pptx",
    "xls",
    "xlsx",
    "csv",
    "json",
    "xml",
    "html",
    "htm",
)
private val documentMimeTypes = setOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/json",
    "application/xml",
    "text/markdown",
)

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1fKB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1fMB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.1fGB", gb)
}

private fun formatDate(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(millis)
}
