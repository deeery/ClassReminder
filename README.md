# ClassReminder — Android 课程提醒 App

一个 Android 课程提醒应用，使用 **Kotlin + Jetpack Compose + Room + Foreground Service** 构建。

## 功能

- **课程管理** — 添加 / 编辑 / 删除每周课程，数据持久化到 Room 数据库
- **智能提醒** — 前台 Service 每 30 秒轮询，课程即将开始或正在上课时发送高优先级通知
- **锁屏弹窗** — 可选全屏锁屏弹窗（LockOverlayActivity），不错过每一节课
- **周课表** — 底部栏切换「列表」和「课表」两种视图，课表按星期几分组，可折叠
- **动态前台通知** — 前台通知实时显示检测状态：正在上课/即将上课/今日课程概况
- **浅色/深色主题** — 三种模式：跟随系统、浅色、深色，设置页一键切换
- **开机自启** — 可开启开机自动启动提醒服务
- **自定义图标** — 支持启动器图标和通知栏图标

## 架构

```
MainActivity (单 Activity)
  └─ ClassReminderTheme (浅色/深色支持)
       └─ Surface + Scaffold
            ├─ TopAppBar × 底部导航栏（列表 / 课表 / 设置）
            ├─ ClassListView (所有课程列表)
            ├─ WeekView (按周分组，可折叠)
            └─ SettingsPage (主题、权限、提醒设置)
  └─ MainViewModel ──→ Room Database
  └─ ClassReminderService (Foreground Service，每 30 秒轮询)
       └─ LockOverlayActivity (锁屏弹窗)
  └─ BootReceiver (开机自启)
```

## 关键文件

| 文件 | 作用 |
|---|---|
| `ui/MainScreen.kt` | Compose UI：底部栏、列表视图、周课表、设置页面、添加/编辑对话框 |
| `ui/Theme.kt` | 自定义主题：浅色/深色调色板、ThemeMode 枚举 |
| `data/MainViewModel.kt` | ViewModel：连接 UI 和 Room 数据库 |
| `data/ClassEntity.kt` | Room 实体（id, 标题, 星期, 开始/结束时间, 教室） |
| `data/ClassDao.kt` | Room DAO（查询、插入、删除） |
| `data/AppDatabase.kt` | Room 数据库单例 |
| `ClassReminderService.kt` | 前台 Service：轮询检查课程、动态更新前台通知、发送提醒 |
| `LockOverlayActivity.kt` | 锁屏覆盖 Activity |
| `BootReceiver.kt` | 开机自启 BroadcastReceiver（带权限检查） |
| `Prefs.kt` | SharedPreferences 封装（提醒时间、主题模式、开机自启等） |

## 更新日志

### v1.1

- **版本升级**：versionName 升级到 1.1（versionCode 2），正式发布版

### v2.0 — 稳定版

- **修复所有崩溃问题**：Android 14 foregroundServiceType 权限缺失、无通知权限时 startForeground 崩溃、POST_NOTIFICATIONS 权限检查
- **数据持久化**：UI 通过 ViewModel 读写 Room 数据库，课程数据不再丢失
- **底部导航栏**：列表 / 课表 / 设置三个入口，设置改为全屏页面
- **周课表视图**：按周一至周日分组展示，可折叠每天课程
- **前台通知动态化**：实时显示「正在上课」「即将上课」「今日 N 节课」等信息
- **浅色/深色主题**：完整配色方案，支持跟随系统、浅色、深色三种模式
- **UI 统一**：所有交互组件（按钮、开关、单选按钮、FAB）统一使用蓝色主题色
- **图标替换**：桌面图标和通知图标替换为自定义时钟图标（矢量 + 自适应图标）
- **异形屏适配**：状态栏颜色与 TopAppBar 一致，配合浅色/深色模式
- **去除冗余代码**：删除了死代码、废弃的 AlarmManager 通知通道、READ_EXTERNAL_STORAGE 权限
- **Service 自动启动**：App 启动时自动检查权限并启动提醒 Service

## 如何运行

1. 在 Android Studio 中打开此项目。
2. 等待 Gradle 导入完成并构建。
3. 在 Android 设备或模拟器上运行（最低 Android 5.0，目标 Android 14）。
