package com.xhulib.ui.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xhulib.R
import com.xhulib.data.Opac
import com.xhulib.ui.common.EmptyView
import com.xhulib.ui.nav.WebOnlyTask

/**
 * 「需要到图书馆网站办理」的提示页（违章缴款 / 读者挂失）。
 *
 * 这两个功能本应用**不代办**（涉及缴款；挂失需输密码且自己不能解除），
 * 页面只做说明并引导用户用浏览器打开 OPAC 首页，**不发起任何写操作请求**。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebOnlyScreen(taskKey: String, onBack: () -> Unit) {
    val task = WebOnlyTask.fromKey(taskKey)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        task?.let { stringResource(it.titleRes) }
                            ?: stringResource(R.string.webonly_fallback_title),
                    )
                },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (task == null) {
                EmptyView(text = stringResource(R.string.empty_record))
            } else {
                WebOnlyContent(task)
            }
        }
    }
}

@Composable
private fun WebOnlyContent(task: WebOnlyTask) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = task.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(task.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(task.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.goto_web_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                WebPortalAction()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.webonly_no_write_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun WebOnlyTask.icon(): ImageVector = when (this) {
    WebOnlyTask.Fines -> Icons.Filled.Payments
    WebOnlyTask.ReportLoss -> Icons.Filled.ReportProblem
    WebOnlyTask.EmailVerify -> Icons.Filled.MarkEmailRead
    WebOnlyTask.CourseManage -> Icons.Filled.MenuBook
}

/**
 * 醒目展示图书馆网站地址，并提供「用浏览器打开」按钮。
 *
 * 「我的书评 / 书刊遗失 / 账目清单」有内容时的提示页也复用这个块。
 */
@Composable
internal fun WebPortalAction(
    modifier: Modifier = Modifier,
    url: String = Opac.WEB_PORTAL,
) {
    val context = LocalContext.current
    val noBrowserText = stringResource(R.string.error_no_browser)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = url,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { openInBrowser(context, url, noBrowserText) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { openInBrowser(context, url, noBrowserText) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.open_in_browser))
        }
    }
}

/** 用系统浏览器打开网页端；没有可用浏览器时提示而不是崩溃。 */
private fun openInBrowser(context: Context, url: String, failureMessage: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
        }
}
