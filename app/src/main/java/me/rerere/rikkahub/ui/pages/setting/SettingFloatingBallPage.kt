package me.rerere.rikkahub.ui.pages.setting

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.floating.FloatingBallService
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun SettingFloatingBallPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var pendingEnable by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showAssistantSheet by remember { mutableStateOf(false) }

    val mediaProjectionManager = remember(context) {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        pendingEnable = false
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            FloatingBallService.start(context, result.resultCode, data)
            vm.updateSettings(settings.copy(floatingBallEnabled = true))
        } else {
            vm.updateSettings(settings.copy(floatingBallEnabled = false))
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
                if (pendingEnable && overlayGranted) {
                    screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestEnable() {
        val modelOk = settings.floatingBallModelId?.let { id ->
            settings.providers.flatMap { it.models }.any { it.id == id && it.type == ModelType.CHAT && it.inputModalities.contains(Modality.IMAGE) }
        } ?: false
        if (!modelOk) {
            vm.updateSettings(settings.copy(floatingBallEnabled = false))
            showModelDialog = true
            return
        }

        if (!Settings.canDrawOverlays(context)) {
            pendingEnable = true
            showOverlayDialog = true
            return
        }

        pendingEnable = true
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    fun disable() {
        pendingEnable = false
        FloatingBallService.stop(context)
        vm.updateSettings(settings.copy(floatingBallEnabled = false))
    }

    val visionProviders = remember(settings.providers) {
        settings.providers.map { provider ->
            provider.copyProvider(
                models = provider.models.filter { model ->
                    model.type == ModelType.CHAT && model.inputModalities.contains(Modality.IMAGE)
                }
            )
        }
    }

    val selectedAssistantId = settings.floatingBallAssistantId ?: settings.getCurrentAssistant().id
    val selectedAssistantName = settings.assistants.find { it.id == selectedAssistantId }?.name
        ?.ifEmpty { stringResource(R.string.assistant_page_default_assistant) }
        ?: stringResource(R.string.assistant_page_default_assistant)

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("悬浮小球") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FormItem(
                    label = { Text("启用") },
                    description = {
                        Text("开启后会常驻一个小球；点击小球会自动截图并弹出小对话框")
                    },
                    tail = {
                        Switch(
                            checked = settings.floatingBallEnabled,
                            onCheckedChange = { checked ->
                                if (checked) requestEnable() else disable()
                            }
                        )
                    },
                )
            }

            item {
                FormItem(
                    label = { Text("权限") },
                    description = {
                        Text("需要：悬浮窗权限 + 屏幕录制授权")
                        Text("悬浮窗：${if (overlayGranted) "已开启" else "未开启"}")
                    }
                )
            }

            item {
                FormItem(
                    label = { Text("助手") },
                    description = { Text("小球对话使用的助手") },
                    tail = {
                        TextButton(onClick = { showAssistantSheet = true }) {
                            Text(selectedAssistantName)
                        }
                    }
                )
            }

            item {
                FormItem(
                    label = { Text("模型") },
                    description = { Text("只能选择支持图像输入的聊天模型") },
                    tail = {
                        ModelSelector(
                            modelId = settings.floatingBallModelId,
                            providers = visionProviders,
                            type = ModelType.CHAT,
                            onSelect = { model ->
                                vm.updateSettings(settings.copy(floatingBallModelId = model.id))
                            },
                        )
                    }
                )
            }
        }
    }

    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = {
                showOverlayDialog = false
                pendingEnable = false
            },
            title = { Text("需要悬浮窗权限") },
            text = { Text("请先在系统设置中允许本应用显示在其他应用上层。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverlayDialog = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("去开启")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOverlayDialog = false
                        pendingEnable = false
                        vm.updateSettings(settings.copy(floatingBallEnabled = false))
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("请先选择模型") },
            text = { Text("悬浮小球只能使用支持图像输入的聊天模型。") },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }

    if (showAssistantSheet) {
        AssistantSelectSheet(
            assistants = settings.assistants,
            selectedId = selectedAssistantId,
            onSelect = { id ->
                showAssistantSheet = false
                vm.updateSettings(settings.copy(floatingBallAssistantId = id))
            },
            onDismiss = { showAssistantSheet = false }
        )
    }
}

@Composable
private fun AssistantSelectSheet(
    assistants: List<me.rerere.rikkahub.data.model.Assistant>,
    selectedId: Uuid,
    onSelect: (Uuid) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = assistants, key = { it.id }) { assistant ->
                val selected = assistant.id == selectedId
                Card(
                    onClick = { onSelect(assistant.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    androidx.compose.material3.ListItem(
                        headlineContent = {
                            Text(
                                text = assistant.name.ifEmpty { "默认助手" },
                                maxLines = 1,
                            )
                        }
                    )
                }
            }
        }
    }
}
