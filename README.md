# ClassReminder — Android 课程提醒 App

一个 Android 课程提醒应用，使用 Jetpack Compose + Room + Foreground Service 构建。

## 功能

- 添加 / 编辑 / 删除每周课程
- 前台 Service 每 30 秒轮询检查即将开始的课程，发送高优先级通知
- 可选锁屏弹窗（通过 LockOverlayActivity 实现）
- 开机自启
- 可配置提前提醒时间

## 架构

```
MainActivity (单 Activity)
  └─ MainScreen (Compose UI) ──→ MainViewModel ──→ Room Database
  └─ ClassReminderService (Foreground Service, 每 30 秒轮询读取 Room)
       └─ LockOverlayActivity (锁屏弹窗)
  └─ BootReceiver (开机启动 Service)
```

## 关键文件

| 文件 | 作用 |
|---|---|
| `ui/MainScreen.kt` | Compose UI：课程列表、添加/编辑对话框、设置对话框 |
| `data/MainViewModel.kt` | ViewModel：连接 UI 和 Room 数据库 |
| `data/ClassEntity.kt` | Room 实体 |
| `data/ClassDao.kt` | Room DAO（查询、插入、删除） |
| `data/AppDatabase.kt` | Room 数据库单例 |
| `ClassReminderService.kt` | 前台 Service，轮询 Room 数据库检查课程提醒 |
| `LockOverlayActivity.kt` | 锁屏覆盖 Activity |
| `BootReceiver.kt` | 开机自启 BroadcastReceiver |
| `Prefs.kt` | SharedPreferences 封装 |

## 如何运行

1. 在 Android Studio 中打开此项目。
2. 等待 Gradle 导入完成并构建。
3. 在 Android 设备或模拟器上运行。
