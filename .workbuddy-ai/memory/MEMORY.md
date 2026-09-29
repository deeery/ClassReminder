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
- **UI 改动的验证方式：只跑到 `compileDebugKotlin` / `testDebugUnitTest`，不要构建 APK。**
  用户要看效果时会明确说，届时再决定构建或做预览。
- **UI 效果用户希望亲自验收**。纯 Compose 无法在浏览器跑时，做法是：
  用 **HTML/CSS 1:1 复刻**该组件的渲染逻辑，参数与源码常量逐一对应，
  做成可交互验收页（支持切主题、对照改动前后、模拟窄屏），产出到
  `build/preview/<组件名>-preview.html`，再用 `present_files` 打开。

## 设计规范
- 主题在 `ui/Theme.kt`：`LightColors` / `DarkColors`，`ThemeMode` 三态（跟随系统/浅色/深色）。
- 浅深两套色**必须分别标定 α**：同一个 α 在深背景常失效（如 `onSurface` 与 `surface` 仅差 1.78:1）。
  深色下的描边 / 高亮一律走「主色 + 逐级透明度」，不要用灰。
- 高亮分级收敛在 `HighlightSpec.of()`（`maxOf` 取最强档，不叠加）。
- 参考设计系统见 `~/.workbuddy-ai/skills/awesome-design-md/`（54 套）。

## 已知技巧
- `LocalDensity.current` **不能写在 `remember` 内部**（Composable 调用不允许）；
  要提到外面并作为 key，`toPx()` 用 `with(density) { ... }`。
- 网格渲染性能：传给 `GridCell` 的必须是布尔/数值（不要传每帧变的 `mapping` 或块高），
  点击回调收数据对象而非闭包，否则每帧重组。

## Git
- 凭据 helper 路径（`~/.gitconfig` 里 `credential.helper` 为空，禁用了所有 helper）：
  ```
  git -c credential.helper="C:/Users/Administrator/.workbuddy-ai/binaries/PortableGit/versions/1.2.0/mingw64/bin/git-credential-wincred.exe" push origin main
  ```
- 历史上有过一次分叉：远程 `main` 被机器人推成 v1.1（删了大量代码）。
  已用 B 方案处理：远程 v1.1 保底到 `backup-v1.1`，本地 force-with-lease 成权威。
