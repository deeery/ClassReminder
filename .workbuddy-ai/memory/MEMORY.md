# ClassReminder — 项目长期记忆

## 项目概况
Android 应用（Kotlin + Jetpack Compose + Room + Foreground Service），周课表 + 上课提醒。
包名 `com.example.classreminder`，远程 `https://github.com/deeery/ClassReminder.git`。

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
- **点击坐标必须按设备分辨率换算**：本项目设备 `wm size` = 1080x2400，
  而截图/预览图常是 480x1200 → 换算比 **2.25**。
  **最可靠：先 `uiautomator dump` 读真实 `bounds`，取中心点点击。**
- 常用坐标（1080x2400）：底部导航「今天」(159,2310)、「课表」(417,2310)、「便签」(722,2310)、「设置」(1011,2310)。
- `uiautomator dump` 偶发失败（拉不到文件），重试一次即可。
- **双击**：`adb shell "input tap X Y; input tap X Y"` 两进程开销可能超 300ms 窗口 → 不成立。
  最稳路径是「单击选中 → 点左下角浮出的『编辑』按钮」。
- **logcat 为空不代表工具坏**：先 `adb shell log -t TEST hello` 自证；若 shell 日志能读到，
  那就是应用真的没打日志（代码没执行到）。
- app 偶发进入「UI 完全无响应」的僵死态（截图 md5 不变、CPU 0%、`top` 的 `TIME+` 不涨）→
  `am force-stop` + 重启即可，不是代码问题。

## 已知技巧
- `LocalDensity.current` **不能写在 `remember` 内部**（Composable 调用不允许）；
  要提到外面并作为 key，`toPx()` 用 `with(density) { ... }`。
- 网格渲染性能：传给 `GridCell` 的必须是布尔/数值（不要传每帧变的 `mapping` 或块高），
  点击回调收数据对象而非闭包，否则每帧重组。
- `pointerInput(key)` 的 block 只在 key 变化时重建，内部闭包会「冻」在创建那刻；
  读外部状态要用 `rememberUpdatedState` 包一层，或像 `wasSelectedAtPress` 那样用局部 var。

## Git
- 凭据 helper 路径（`~/.gitconfig` 里 `credential.helper` 为空，禁用了所有 helper）：
  ```
  git -c credential.helper="C:/Users/Administrator/.workbuddy-ai/binaries/PortableGit/versions/1.2.0/mingw64/bin/git-credential-wincred.exe" push origin main
  ```
- 历史上有过一次分叉：远程 `main` 被机器人推成 v1.1（删了大量代码）。
  已用 B 方案处理：远程 v1.1 保底到 `backup-v1.1`，本地 force-with-lease 成权威。
