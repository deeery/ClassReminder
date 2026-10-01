package com.example.classreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.classreminder.data.sync.AccountSession
import com.example.classreminder.data.sync.ApiException
import com.example.classreminder.data.sync.AuthUser
import com.example.classreminder.data.sync.SyncEngine
import com.example.classreminder.data.sync.SyncPhase
import com.example.classreminder.data.sync.SyncState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置页里的「账号」+「云同步」两块。
 *
 * ## 与桌面端的差异（有意为之，不是漏做）
 *
 * 桌面端是 Windows Fluent：横向的设置行 + 幽灵按钮 + 左侧边栏。
 * 安卓这边按 Material 3 走：竖向堆叠的卡片、底部弹出的登录表单、
 * 主动作做成实心按钮。**功能逻辑完全一致**，只是不照搬视觉。
 *
 * ## 三态不可省
 *
 * 未登录 / 已登录未同步 / 已登录已同步，三种状态下这一块的内容完全不同，
 * 所以 [accountAndSyncSection] 用的是「按状态换整块内容」而不是「藏几行」。
 */
@Composable
fun accountAndSyncSection(
    accountSession: AccountSession,
    syncEngine: SyncEngine,
    onSignedInChanged: () -> Unit
) {
    SectionTitle("账号")
    AccountCard(accountSession, onSignedInChanged)
    SectionTitle("云同步")
    SyncCard(accountSession, syncEngine)
}

// ── 账号卡 ──────────────────────────────────────────────────────

@Composable
private fun AccountCard(session: AccountSession, onSignedInChanged: () -> Unit) {
    val user by session.user.collectAsState()
    var showAuth by remember { mutableStateOf(false) }

    SettingsCardCompat {
        if (user == null) {
            Text("未登录", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                "登录后可以在多台设备之间同步课程表与便签。" +
                    "不登录也能正常使用，数据只留在本机。",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { showAuth = true }, modifier = Modifier.fillMaxWidth()) {
                Text("登录 / 注册")
            }
        } else {
            AccountSignedIn(user!!, session, onSignedInChanged)
        }
    }

    if (showAuth) {
        AuthDialog(
            session = session,
            onDismiss = { showAuth = false },
            onSuccess = {
                showAuth = false
                onSignedInChanged()
            }
        )
    }
}

@Composable
private fun AccountSignedIn(
    user: AuthUser,
    session: AccountSession,
    onSignedInChanged: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var confirmingOut by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(user)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.nickname.ifBlank { user.email },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                user.email,
                fontSize = 12.sp,
                color = scheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Divider(color = scheme.outlineVariant)
    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = { confirmingOut = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("退出登录")
    }

    if (confirmingOut) {
        AlertDialog(
            onDismissRequest = { confirmingOut = false },
            title = { Text("退出登录？") },
            text = {
                Text(
                    "退出后本机数据不会被删除，但会停止与云端同步。" +
                        "换账号登录时，另一台设备上的数据会成为准的版本。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingOut = false
                    // 用 remember 拿到的 scope，不要 MainScope()：
                    // 后者每次调用都新建一个协程作用域，永不取消 ——
                    // 协程会一直挂在 Activity 之外，界面早已销毁它还在跑。
                    scope.launch {
                        runCatching { session.logout() }
                        onSignedInChanged()
                    }
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingOut = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun Avatar(user: AuthUser) {
    val scheme = MaterialTheme.colorScheme
    // 用邮箱首字母当头像，省掉图片资源和网络请求 ——
    // 账号区只需要一个「这是谁」的视觉锚点，头像本身没有信息量。
    val initial = (user.nickname.ifBlank { user.email }).take(1).uppercase()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(scheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initial.ifBlank { "?" },
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onPrimaryContainer
        )
    }
}

// ── 登录 / 注册表单 ─────────────────────────────────────────────

private const val MODE_LOGIN = 0
private const val MODE_REGISTER = 1

@Composable
private fun AuthDialog(
    session: AccountSession,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(MODE_LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 密码下限 8 位，与服务端一致。放在按钮的 enabled 里而不是提交时判断，
    // 是为了让用户**在按之前**就知道哪里不够，而不是点了才被拒。
    val canSubmit = email.isNotBlank() && password.length >= 8 &&
        (mode == MODE_LOGIN || invite.isNotBlank())

    fun submit() {
        if (busy || !canSubmit) return
        error = null
        scope.launch {
            busy = true
            try {
                if (mode == MODE_LOGIN) {
                    session.login(email.trim(), password)
                } else {
                    session.register(email.trim(), password, invite.trim())
                }
                onSuccess()
            } catch (e: ApiException) {
                error = friendlyAuthError(e.code, e.message)
            } catch (t: Throwable) {
                error = t.message ?: "出错了，请重试"
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(if (mode == MODE_LOGIN) "登录 StuMate" else "注册 StuMate")
        },
        text = {
            Column {
                // 登录 / 注册用分段切换而不是两个按钮：两者是同一件事的两个状态，
                // 放一起能让用户知道「注册需要邀请码」是注册**特有**的要求。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeTab(
                        text = "登录",
                        selected = mode == MODE_LOGIN,
                        modifier = Modifier.weight(1f)
                    ) {
                        mode = MODE_LOGIN
                        error = null
                    }
                    ModeTab(
                        text = "注册",
                        selected = mode == MODE_REGISTER,
                        modifier = Modifier.weight(1f)
                    ) {
                        mode = MODE_REGISTER
                        error = null
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    label = { Text("邮箱") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    // 显示/隐藏用文字按钮而不是眼睛图标：
                    // `material-icons-core` 只有十来枚常用图标（Search / Edit / Home …），
                    // Visibility 这类在 `material-icons-extended` 里，
                    // 为了两个图标引入一整个扩展包不值当。
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "隐藏" else "显示", fontSize = 12.sp)
                        }
                    },
                    supportingText = {
                        Text("至少 8 位", fontSize = 11.sp)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (mode == MODE_REGISTER) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = invite,
                        onValueChange = { invite = it.uppercase(); error = null },
                        label = { Text("邀请码") },
                        singleLine = true,
                        supportingText = {
                            Text("格式 XXXX-XXXX-XXXX，向已有账号的人索取", fontSize = 11.sp)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 描边排在 clip 之前，否则圆角会把描边切出缺口
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = canSubmit && !busy) {
                // 不用 CircularProgressIndicator：material3 1.1.2 与 animation-core 1.6.0
                // 存在 KeyframesSpecConfig.at() 的签名错配（NoSuchMethodError，必崩）。
                // 文字态既规避了这个坑，也和卡片里其它状态文案保持一致的观感。
                Text(
                    when {
                        !busy -> if (mode == MODE_LOGIN) "登录" else "注册"
                        mode == MODE_LOGIN -> "登录中…"
                        else -> "注册中…"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        }
    )
}

@Composable
private fun ModeTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
        )
    }
}

// ── 云同步卡 ────────────────────────────────────────────────────

@Composable
private fun SyncCard(session: AccountSession, engine: SyncEngine) {
    val state by engine.state.collectAsState()
    // 必须订阅 session.user，不能直接调 session.isSignedIn()：
    // 后者是普通函数，值变了 Compose 没有任何理由重组这张卡 ——
    // 实测表现是「刚登录完，账号卡已经是登录态，云同步卡还写着未登录」。
    val user by session.user.collectAsState()

    SettingsCardCompat {
        if (user == null) {
            Text("未登录", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                "登录后会自动在多台设备之间同步课程表与便签。" +
                    "设置项不参与同步，未登录时数据只留在本机。",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            val busy = state.phase == SyncPhase.BUSY
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(state.phase)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.message.ifBlank { "还没同步过" },
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    color = if (state.phase == SyncPhase.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(
                    onClick = { engine.syncNow() },
                    enabled = !busy
                ) {
                    Text(if (busy) "同步中…" else "立即同步")
                }
            }

            if (state.lastSyncedAt > 0L) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "上次同步：${formatSyncTime(state.lastSyncedAt)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // LWW 的已知限制必须如实告知（设计 §5.5 明确「不隐藏」）。
            // 两台设备离线改同一条记录时后同步的会覆盖先同步的，
            // 用户有权知道这件事存在，而不是事后才发现改动没了。
            if (state.overriddenCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "有 ${state.overriddenCount} 条本地记录被服务端版本覆盖。" +
                        "两台设备离线时改同一门课，后同步的那台会赢。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // 首端切换的自动备份（设计 §5.8 要求「在 UI 上明确告知路径」）。
            // 这一刻用户本地数据被清空过，不告诉他备份在哪，他会以为数据没了。
            state.backupPath?.let { path ->
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        "首次同步前已把本机原有数据备份到：",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(path, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "启动时同步一次，改动后 30 秒内没有新改动就会自动同步",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * 状态点。
 *
 * 用色点而不是图标：这里要区分的是「正常 / 进行中 / 失败 / 跳过」四种，
 * 每一种旁边都有文字说明，颜色只是辅助 —— 不能只靠颜色区分状态
 * （色觉障碍用户读不出来）。
 */
@Composable
private fun StatusDot(phase: SyncPhase) {
    val scheme = MaterialTheme.colorScheme
    val color = when (phase) {
        SyncPhase.BUSY -> scheme.primary
        SyncPhase.FAILED -> scheme.error
        SyncPhase.OFFLINE -> scheme.onSurface.copy(alpha = 0.5f)
        SyncPhase.SKIPPED -> scheme.onSurface.copy(alpha = 0.5f)
        SyncPhase.IDLE -> scheme.primary
        SyncPhase.SUCCESS -> scheme.tertiary
    }
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, scheme.outlineVariant, CircleShape)
    )
}

// ── 小工具 ──────────────────────────────────────────────────────

/**
 * 把服务端错误码翻成人话。
 *
 * 直出后端英文错误对用户毫无意义（「BAD_CREDENTIALS」没人看得懂是密码错还是邮箱错）。
 * 未识别的码退回原始 message —— 至少不丢信息。
 */
private fun friendlyAuthError(code: String, message: String): String = when (code) {
    "BAD_CREDENTIALS" -> "邮箱或密码不对。"
    "EMAIL_TAKEN" -> "这个邮箱已经注册过了，直接登录即可。"
    "INVALID_INVITE" -> "邀请码无效、已过期或者已经被用完了。"
    "RATE_LIMITED" -> "尝试太频繁了，请过几分钟再试。"
    "PASSWORD_ALREADY_SET" -> "这个账号已经设过密码了。"
    "NEED_INVITE" -> "需要一个邀请码才能注册。"
    "NETWORK" -> "连不上服务器，请检查网络后重试。"
    "BAD_REQUEST" -> message
    else -> message
}

private fun formatSyncTime(epochMs: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(epochMs))

/**
 * 复用设置页已有的卡片样式。
 *
 * 这里只做一次转发，是为了让「账号 / 同步」这两块的调用点读起来和设置页其余部分
 * 一致（`SettingsCardCompat { … }`），视觉与间距仍由`SettingsCard` 单点决定。
 */
@Composable
private fun SettingsCardCompat(content: @Composable ColumnScope.() -> Unit) {
    SettingsCard(content)
}