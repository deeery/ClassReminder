# StuMate（原 ClassReminder）— 项目长期记忆

## 项目概况
Android 应用（Kotlin + Jetpack Compose + Room + Foreground Service），周课表 + 便签 + 上课提醒。
- **项目名已改为 `StuMate-Android-Preview`**（Gradle `rootProject.name`），启动器显示名 **StuMate**
  （debug 变体 `StuMate-test`）。**包名 `com.example.classreminder` 与类名保持不动** —— 应用名和包名是两回事。
- 包名 `com.example.classreminder`，远程 `https://github.com/deeery/ClassReminder.git`。

## 构建环境（必须离线）
```bash
export JAVA_HOME=/c/Users/Administrator/.workbuddy-ai/binaries/jdk17/jdk-17.0.20.1+1
export PATH="$JAVA_HOME/bin:$PATH"
/c/Users/Administrator/.gradle/wrapper/dists/gradle-8.4-bin/gradle-8.4/bin/gradle <task> --offline
```
- **kapt 若报 `Unable to delete directory .../tmp/kapt3/incrementalData/debug`**：
  先 `gradle --stop` + `rm -rf app/build/tmp/kapt3 app/build/classes` 再重试。

## 用户偏好（重要）
- **UI 改动的验证方式：默认只跑到 `compileDebugKotlin` / `testDebugUnitTest`，不主动构建 APK。**
  用户要看效果时会明确说，届时再决定构建或做预览。
  （例外：**实机验证 UI 效果时，构建 + 安装 debug 包是被接受的**。）
- **UI 效果用户希望亲自验收**。纯 Compose 无法在浏览器跑时，做法是：
  用 **HTML/CSS 1:1 复刻**该组件的渲染逻辑，参数与源码常量逐一对应，
  做成可交互验收页（支持切主题、对照改动前后、模拟窄屏），再用 `present_files` 打开。
- **★ 预览页文件命名约定：`material3-preview-vX.Y.html`，版本号递增、不覆盖旧版**，
  统一放 `build/preview/`。示例：`material3-preview.html`（v1）→ `material3-preview-v2.0.html`。

## 设计规范
- 主题在 `ui/Theme.kt`：`LightColors` / `DarkColors`，`ThemeMode` 三态（跟随系统/浅色/深色）。
- **深色下「需要品牌蓝、但字号小」的地方用 `GoogleBlueLight`（`#8AB4F8`）** —— 主色 `#1A73E8`
  对深色卡片只有 3.8:1，小字发闷；纯白又太素（用户原话「寡淡」）。命名色值收在 `Theme.kt`，别再各自硬编码。
- 首屏问候语：**38sp / `FontWeight.Light` / lineHeight 46sp**（40sp 是上限，窄屏「早上好。」会折行）。
- 浅深两套色**必须分别标定 α**：同一个 α 在深背景常失效（如 `onSurface` 与 `surface` 仅差 1.78:1）。
  深色下的描边 / 高亮一律走「主色 + 逐级透明度」，不要用灰。
- 高亮分级收敛在 `HighlightSpec.of()`（`maxOf` 取最强档，不叠加）。
  - 需要「锚定用户自选颜色」时传 `accentColor`：色相跟着它走，选中/悬停只调明度与条宽，**不换色**。
- **受控色板存索引（0..7）而非 ARGB** —— 深浅主题各取一套合适色值（见 `notePaletteColor`）。
- 参考设计系统见 `~/.workbuddy-ai/skills/awesome-design-md/`（54 套）。

## ★★★ Compose 手势三大坑（本项目反复踩，务必先读）

1. **看样式用 `clickable` 会把 down 事件吃掉，导致外层 `detectTapGestures` 全废。**
   - 同一节点的多个 `pointerInput`，Main pass 执行顺序是**「链尾（最内层）→ 链首」**。
   - 若 `clickable` 排在链尾，它的 `detectTapAndPress` 最先 `awaitFirstDown` 并 `consume()`，
     外层 `detectTapGestures` 的 `awaitFirstDown(requireUnconsumed = true)` 拿不到事件 → **所有回调不触发**。
   - `clickable` 的 `onClick` **为空也照样 consume**。
   - **修复：只要悬停/按下反馈时改用 `Modifier.hoverable(interactionSource)`**；
     按下反馈自己在 `onPress` 里 `interaction.emit(PressInteraction.Press/Release)`。

2. **`onPress` 写选中、`onTap` 又读 `selected` 做判断 → 逻辑自噬。**
   - `onPress` 在按下当帧就点亮，300ms 后 `onTap` 读到的必然已为 true，
     「点第一下」被误判成「点第二下」，净效果为零（表现为点了没反应）。
   - **修复：`onPress` 里先存快照 `wasSelectedAtPress = currentSelected`，`onTap` 只依据快照。**

3. **`detectTapGestures` 的 `onPress` 入参是 `Offset`，不是 `PointerInputChange`，没有 `consume()`。**
   - 想靠 consume down 来阻止上层手势是行不通的（编译报 `Unresolved reference: consume`）。
   - **替代方案：落点命中判定** —— 用 `onTap` 的 `Offset.y` 去比
     `listState.layoutInfo.visibleItemsInfo[].offset..offset+size`（同一坐标系），命中卡片就不执行 deselect。

## 实机验证（adb）要点
- **点击坐标必须先 `uiautomator dump` 读真实 `bounds` 取中心，不要按历史分辨率硬算。**
  本机 AVD `Medium_Phone` 实际 `wm size` = **1080x2400，density 420**（2026-09-30 实测）。
  （更早记录过的 1272x2800 / 560 是**另一台** AVD，别再混用。）
- 本机真实坐标（1080x2400）：底部导航「今天」(127,2275)、「课表」(402,2275)、「便签」(677,2275)、「设置」(952,2275)。
  ⚠️ **点 y > 2400 会下拉通知栏**；BACK 关通知栏时有可能把应用一起退出（回到桌面）。
- `uiautomator dump` 偶发失败（拉不到文件），重试一次即可。`adb pull` 需前缀 `MSYS_NO_PATHCONV=1`。
- **双击**：`adb shell "input tap X Y; input tap X Y"` 两进程开销可能超 300ms 窗口 → 不成立。
  最稳路径是「单击选中 → 点左下角浮出的『编辑』按钮」（或直接改库造数据）。
- **`adb shell input text` 只支持 ASCII** —— 中文输入无效，测试数据请用 `Note-A` 之类。
- **长链 `adb && sleep && adb` 偶发 `Error: sandbox-center cmd decisionRecord missing actual resource subject`**，拆成单条即可。
- **logcat 为空不代表工具坏**：先 `adb shell log -t TEST hello` 自证；若 shell 日志能读到，
  那就是应用真的没打日志（代码没执行到）。
- app 偶发进入「UI 完全无响应」的僵死态（截图 md5 不变、CPU 0%、`top` 的 `TIME+` 不涨）→
  `am force-stop` + 重启即可，不是代码问题。

## ★ WAL 模式下改设备数据库（模拟器无 root、无 sqlite3）
1. `run-as com.example.classreminder base64 databases/<db>` 逐个导出**三个**文件：主库 + `-wal` + `-shm`
   （只导主库会 `database disk image is malformed`）。用 `tr -d '\r'` 清换行污染。
2. 本地 Python `sqlite3` 打开主库（会自动 checkpoint 合并 WAL）→ 改数据 → 备份。
3. `am force-stop` 应用。
4. 推回时**必须用 stdin 管道**：`adb shell "run-as ... base64 -d > databases/<db>" < xxx.b64`
   —— 把 base64 当命令行参数会 `Argument list too long`。
5. 推回前先 `rm -f` 两个辅助文件（`-wal` / `-shm`），再重启 App。
- 不要用 `tar` 导出：二进制流会被 Windows 换行破坏（`tar: Skipping to next header`）。
- 像素级验证：`pip install pillow` 到隔离 venv
  `C:/Users/Administrator/.workbuddy-ai/binaries/python/envs/default/Scripts/python.exe`，读 RGB 判定底纹/配色是否真的生效。

## 已知技巧
- **★ kapt 把 KDoc 转成 Java stub 时，注释里的 `\uXXXX`（带反斜杠）会被 javac 当成 Unicode 转义解析**，
  报「非法的 Unicode 转义」，而且报错指向 `app/build/tmp/kapt3/stubs/...` 的**生成文件**（不是源码，第一次看很容易懵）。
  → KDoc 里提 unicode 转义要写 `U+XXXX`，不要带反斜杠。
- **★ `LazyColumn` 的 content lambda 是 `LazyListScope`，不是 `@Composable`，里面不能调 `remember`。**
  写 `remember` 在 `items(...)` 之间会报 `@Composable invocations can only happen from the context of a @Composable function`。
  修法：把 `remember` 提到 `LazyColumn` **之外**，lambda 里只引用算好的值。
- **★ `import androidx.compose.material3.*` 会撞平台同名类**：`DatePickerDialog` / `TimePickerDialog`
  会被解析成 material3 的同名 `@Composable`（参数表完全不同）。修法：**别名导入**
  `import android.app.DatePickerDialog as SysDatePickerDialog`。
- `LocalDensity.current` **不能写在 `remember` 内部**（Composable 调用不允许）；
  要提到外面并作为 key，`toPx()` 用 `with(density) { ... }`。
- 网格渲染性能：传给 `GridCell` 的必须是布尔/数值（不要传每帧变的 `mapping` 或块高），
  点击回调收数据对象而非闭包，否则每帧重组。
- `pointerInput(key)` 的 block 只在 key 变化时重建，内部闭包会「冻」在创建那刻；
  读外部状态要用 `rememberUpdatedState` 包一层，或像 `wasSelectedAtPress` 那样用局部 var。
- **深浅判定统一用 `MaterialTheme.colorScheme.surface.luminance() < 0.5f`**
  （`notePalette()` 与课表底纹都走这一套，别引入第二套标准）。
- **同一 α 在深浅两套主题下的感知强度不等价**：浅色是「深灰压白」、深色是「浅灰提黑」，
  后者天然更弱。需要观感对齐时，深色要**单独给一套略高的 α**（如斑马纹 0.03 → 0.06）。

## 数据备份（模块化导入 / 导出）
- `data/backup/MiniJson.kt`（自研极简 JSON，离线构建拉不到 Gson/Moshi）+ `data/backup/Backup.kt`（模块 / 文档 / 编解码）。
- 模块：`BackupModule.COURSES / NOTES / SETTINGS`。**单模块导出与整体导出的文件格式完全一样**，
  只差 `modules` 里有几个键 → 解析只有一条路径，不要写成两套。
- 入口：`MainViewModel.buildBackupText / localCount / importBackup`；UI 在 `MainScreen.BackupSection`。
- 导入策略：**逐模块确认覆盖**（课表一次、便签一次、设置一次），不做增量合并。
- 设置模块只收用户偏好（不含 `first_run` / `last_tab`）；导入后需重启才全部生效。

## Git
- 凭据 helper 路径（`~/.gitconfig` 里 `credential.helper` 为空，禁用了所有 helper）：
  ```
  git -c credential.helper="C:/Users/Administrator/.workbuddy-ai/binaries/PortableGit/versions/1.2.0/mingw64/bin/git-credential-wincred.exe" push origin main
  ```
- 历史上有过一次分叉：远程 `main` 被机器人推成 v1.1（删了大量代码）。
  已用 B 方案处理：远程 v1.1 保底到 `backup-v1.1`，本地 force-with-lease 成权威。
