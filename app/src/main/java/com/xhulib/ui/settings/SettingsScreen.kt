package com.xhulib.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xhulib.R
import com.xhulib.data.DataFreshness
import com.xhulib.data.Opac
import com.xhulib.data.store.AppLanguage
import com.xhulib.data.store.ThemeMode
import com.xhulib.ui.common.BigCard
import com.xhulib.ui.common.SectionRow
import com.xhulib.ui.common.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 危险区里等待二次确认的操作。 */
private enum class LogoutAction { Logout, ForgetAccount }

private const val GITHUB_URL = "https://github.com/AzulCat7720/XHULib"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val container = appContainer()
    val sessionManager = container.sessionManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val animationsEnabled by container.settingsStore.animationsEnabled.collectAsState()
    val themeMode by container.settingsStore.themeMode.collectAsState()
    val language by container.settingsStore.language.collectAsState()
    val backgroundPath by container.settingsStore.backgroundImagePath.collectAsState()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 相册选图 → 复制进私有目录 → 记下路径。
    // 相册返回的 Uri 只有临时权限，重启后就失效，不能直接存。
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = container.backgroundImageStore.save(uri)
            container.settingsStore.setBackgroundImage(path)
        }
    }

    var pendingAction by remember { mutableStateOf<LogoutAction?>(null) }
    var working by remember { mutableStateOf(false) }
    val versionName = remember(context) { appVersionName(context) }

    // 离线缓存占用；读盘放到 IO 线程
    var cacheBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { container.pageCache.sizeBytes() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---------------------------------------------------------- 服务器 / 关于
            SectionLabel(stringResource(R.string.settings_section_server))
            SectionRow(
                title = stringResource(R.string.settings_server_address),
                subtitle = Opac.BASE_URL,
                leadingIcon = Icons.Filled.Dns,
                trailingText = stringResource(R.string.settings_server_readonly),
            )
            SectionRow(
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(
                    R.string.settings_about_value,
                    stringResource(R.string.app_name),
                    versionName?.let { "v$it" } ?: stringResource(R.string.settings_version_unknown),
                ),
                leadingIcon = Icons.Filled.Info,
            )
            SectionRow(
                title = stringResource(R.string.settings_github),
                subtitle = GITHUB_URL.removePrefix("https://"),
                leadingIcon = Icons.Filled.Code,
                onClick = { openUrl(context, GITHUB_URL) },
            )
            HorizontalDivider()

            // ---------------------------------------------------------- 显示
            // ---------------------------------------------------------- 语言
            SectionLabel(stringResource(R.string.settings_section_language))
            LanguagePicker(
                current = language,
                onSelect = { picked ->
                    container.settingsStore.setLanguage(picked)
                    // 换语言要重建 Activity，让 attachBaseContext 重新套用配置
                    context.findActivity()?.recreate()
                },
            )
            HorizontalDivider()

            SectionLabel(stringResource(R.string.settings_section_display))
            NightModeSwitch(
                checked = isDark,
                followingSystem = themeMode == ThemeMode.SYSTEM,
                onCheckedChange = { dark ->
                    container.settingsStore.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
                },
                onFollowSystem = { container.settingsStore.setThemeMode(ThemeMode.SYSTEM) },
            )
            BackgroundImageRow(
                hasImage = backgroundPath != null,
                onPick = { pickImage.launch("image/*") },
                onClear = {
                    container.backgroundImageStore.clear()
                    container.settingsStore.setBackgroundImage(null)
                },
            )
            AnimationSwitch(
                checked = animationsEnabled,
                onCheckedChange = container.settingsStore::setAnimationsEnabled,
            )
            HorizontalDivider()

            // ---------------------------------------------------------- 本地数据
            SectionLabel(stringResource(R.string.settings_section_storage))
            CacheRow(
                sizeText = formatSize(cacheBytes),
                enabled = cacheBytes > 0L,
                onClear = {
                    cacheBytes = 0L
                    scope.launch {
                        withContext(Dispatchers.IO) { container.pageCache.clear() }
                        DataFreshness.reset()
                    }
                },
            )
            HorizontalDivider()

            // ---------------------------------------------------------- 说明
            SectionLabel(stringResource(R.string.settings_section_readme))
            BigCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.settings_readme),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------------------------------------------------- 危险区
            SectionLabel(stringResource(R.string.settings_section_danger), danger = true)
            SectionRow(
                title = stringResource(R.string.settings_logout),
                subtitle = stringResource(R.string.settings_logout_desc),
                leadingIcon = Icons.AutoMirrored.Filled.Logout,
                onClick = { pendingAction = LogoutAction.Logout },
            )
            SectionRow(
                title = stringResource(R.string.settings_logout_forget),
                subtitle = stringResource(R.string.settings_logout_forget_desc),
                leadingIcon = Icons.Filled.DeleteForever,
                onClick = { pendingAction = LogoutAction.ForgetAccount },
            )
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    pendingAction?.let { action ->
        val forget = action == LogoutAction.ForgetAccount
        AlertDialog(
            onDismissRequest = { if (!working) pendingAction = null },
            title = {
                Text(
                    stringResource(
                        if (forget) R.string.settings_logout_forget_confirm_title
                        else R.string.settings_logout_confirm_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (forget) R.string.settings_logout_forget_confirm_message
                        else R.string.settings_logout_confirm_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !working,
                    onClick = {
                        working = true
                        scope.launch {
                            try {
                                sessionManager.logout(forgetAccount = forget)
                                pendingAction = null
                                onLoggedOut()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                pendingAction = null
                            } finally {
                                working = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(if (forget) R.string.settings_logout_forget else R.string.settings_logout))
                }
            },
            dismissButton = {
                TextButton(enabled = !working, onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String, danger: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** 语言选择：跟随系统 / 简体中文 / 繁體中文 / English。 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Translate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppLanguage.entries.forEach { option ->
                FilterChip(
                    selected = option == current,
                    onClick = { if (option != current) onSelect(option) },
                    label = { Text(languageLabel(option)) },
                )
            }
        }
    }
}

/**
 * 语言名一律用它自己的语言书写（简体中文 / 繁體中文 / English），
 * 这样任何界面语言下用户都能认出自己的语言；只有「跟随系统」需要翻译。
 */
@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_follow_system)
    AppLanguage.SIMPLIFIED -> "简体中文"
    AppLanguage.TRADITIONAL -> "繁體中文"
    AppLanguage.ENGLISH -> "English"
}

/** 夜间模式开关；旁带一个「跟随系统」用于恢复自动。 */
@Composable
private fun NightModeSwitch(
    checked: Boolean,
    followingSystem: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onFollowSystem: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.DarkMode,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_night_mode),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    if (followingSystem) R.string.settings_night_mode_system
                    else R.string.settings_night_mode_manual,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (!followingSystem) {
            TextButton(onClick = onFollowSystem) {
                Text(stringResource(R.string.settings_follow_system))
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 自定义全局背景图片。 */
@Composable
private fun BackgroundImageRow(
    hasImage: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Wallpaper,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_background),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    if (hasImage) R.string.settings_background_set
                    else R.string.settings_background_desc,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (hasImage) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.settings_background_clear))
            }
        }
        TextButton(onClick = onPick) {
            Text(stringResource(R.string.settings_background_pick))
        }
    }
}

/** 带开关的设置行（[SectionRow] 只支持文字尾部，装不下 Switch）。 */
@Composable
private fun AnimationSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Animation,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_animations),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_animations_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 离线缓存行：显示占用体积，可一键清除。 */
@Composable
private fun CacheRow(sizeText: String, enabled: Boolean, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Storage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_cache),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_cache_desc, sizeText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(12.dp))
        TextButton(onClick = onClear, enabled = enabled) {
            Text(stringResource(R.string.settings_cache_clear))
        }
    }
}

/** 用浏览器打开外部链接。 */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 把字节数格式化成人看的大小。 */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 KB"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

/** 应用版本号；拿不到时返回 null，由调用方给出兜底文案。 */
@Suppress("DEPRECATION")
private fun appVersionName(context: Context): String? = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()?.takeIf { it.isNotBlank() }

/** 从 Compose 的 Context 里找到宿主 Activity（换语言时需要 recreate）。 */
private fun Context.findActivity(): android.app.Activity? {
    var current: Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}
