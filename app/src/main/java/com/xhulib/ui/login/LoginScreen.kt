package com.xhulib.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xhulib.R
import com.xhulib.data.Opac
import com.xhulib.ui.common.containerViewModel
import com.xhulib.ui.common.appContainer

/**
 * 登录页。
 *
 * 登录需要「账号 + 密码 + 4 位图形验证码」，且验证码是一次性的：
 * 每次提交后（无论成败）都会自动换一张，用户重新输入即可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val viewModel: LoginViewModel = containerViewModel { container ->
        LoginViewModel(container.sessionManager, container.accountStore)
    }
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    // 进入页面自动取一次验证码：本机已保存账号时账号密码都已填好，直接输验证码即可
    LaunchedEffect(Unit) { viewModel.refreshCaptcha() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.login_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LoginHeader()

            if (state.prefilled) {
                Text(
                    text = stringResource(R.string.login_prefilled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            LoginTypeSelector(
                selected = state.loginType,
                enabled = !state.submitting,
                onSelect = viewModel::onLoginTypeChange,
            )

            OutlinedTextField(
                value = state.number,
                onValueChange = viewModel::onNumberChange,
                label = { Text(stringResource(state.loginType.labelRes)) },
                singleLine = true,
                enabled = !state.submitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (state.loginType == Opac.LoginType.EMAIL) {
                        KeyboardType.Email
                    } else {
                        KeyboardType.Text
                    },
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    // 账号栏失焦时，如果账号换了就换一张验证码（换账号 = 换会话）
                    .onFocusChanged { if (!it.isFocused) viewModel.refreshCaptchaIfNeeded() },
            )

            PasswordField(
                value = state.password,
                enabled = !state.submitting,
                onValueChange = viewModel::onPasswordChange,
            )

            CaptchaRow(
                captcha = state.captcha,
                image = state.captchaImage,
                loading = state.captchaLoading,
                hasNumber = state.number.isNotBlank(),
                enabled = !state.submitting,
                onCaptchaChange = viewModel::onCaptchaChange,
                onRefresh = viewModel::refreshCaptcha,
                onSubmit = {
                    focusManager.clearFocus()
                    viewModel.submit(onLoggedIn)
                },
            )

            Text(
                text = stringResource(
                    if (state.captchaLoading) R.string.login_preparing else R.string.login_captcha_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            val errorText = state.errorRes?.let { stringResource(it) } ?: state.error
            errorText?.let { message -> ErrorBanner(message) }

            RememberPasswordRow(
                checked = state.rememberPassword,
                enabled = !state.submitting,
                onCheckedChange = viewModel::onRememberPasswordChange,
            )

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.submit(onLoggedIn)
                },
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(
                        if (state.submitting) R.string.login_submitting else R.string.login_action,
                    ),
                )
            }

            Text(
                text = stringResource(R.string.login_network_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LoginHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.School,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun LoginTypeSelector(
    selected: Opac.LoginType,
    enabled: Boolean,
    onSelect: (Opac.LoginType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.login_type_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Opac.LoginType.entries.forEach { type ->
                FilterChip(
                    selected = selected == type,
                    onClick = { onSelect(type) },
                    enabled = enabled,
                    label = { Text(stringResource(type.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.login_password)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                val icon = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                val description = if (visible) {
                    R.string.login_hide_password
                } else {
                    R.string.login_show_password
                }
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(description),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CaptchaRow(
    captcha: String,
    image: ImageBitmap?,
    loading: Boolean,
    hasNumber: Boolean,
    enabled: Boolean,
    onCaptchaChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = captcha,
            onValueChange = onCaptchaChange,
            label = { Text(stringResource(R.string.login_captcha)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.weight(1f),
        )

        // 图 160×40：点击就换一张（验证码一次性，失败后也必须换）
        Surface(
            onClick = onRefresh,
            enabled = enabled && !loading,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .width(140.dp)
                .height(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                when {
                    loading -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )

                    image != null -> Image(
                        bitmap = image,
                        contentDescription = stringResource(R.string.login_captcha_desc),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                    )

                    else -> Text(
                        text = stringResource(
                            if (hasNumber) R.string.login_captcha_retry else R.string.login_captcha_need_number,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RememberPasswordRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.login_remember_password),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.login_remember_password_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

