package me.rerere.rikkahub.ui.components.ui.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun rememberPythonStoragePermissionRequest(
    onGranted: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onGrantedState = rememberUpdatedState(onGranted)

    @Suppress("DEPRECATION")
    val storagePermission = remember {
        PermissionInfo(
            permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
            displayName = { Text("Storage") },
            usage = { Text("Allow RikkaHub to write Python outputs to /storage/emulated/0/rikkahub_file.") },
            required = true
        )
    }

    val permissionState = rememberPermissionState(
        permissions = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) setOf(storagePermission) else emptySet()
    )
    PermissionManager(permissionState = permissionState)

    var showAllFilesDialog by remember { mutableStateOf(false) }
    var pendingGrant by remember { mutableStateOf(false) }

    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        permissionState.allRequiredPermissionsGranted
    }

    LaunchedEffect(hasPermission, pendingGrant) {
        if (pendingGrant && hasPermission) {
            pendingGrant = false
            onGrantedState.value()
        }
    }

    DisposableEffect(lifecycleOwner, pendingGrant) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (pendingGrant && Environment.isExternalStorageManager()) {
                        pendingGrant = false
                        onGrantedState.value()
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    if (showAllFilesDialog) {
        AlertDialog(
            onDismissRequest = {
                showAllFilesDialog = false
                pendingGrant = false
            },
            title = { Text("Storage access required") },
            text = {
                Text("Grant \"All files access\" so Python outputs can be saved to /storage/emulated/0/rikkahub_file.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAllFilesDialog = false
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAllFilesDialog = false
                        pendingGrant = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    return {
        if (hasPermission) {
            onGrantedState.value()
        } else {
            pendingGrant = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                showAllFilesDialog = true
            } else {
                permissionState.requestPermissions()
            }
        }
    }
}
