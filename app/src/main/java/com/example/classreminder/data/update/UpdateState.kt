package com.example.classreminder.data.update

/**
 * Release 上的一个下载资产。
 *
 * @param sha256 GitHub 新版 API 会带 `digest` 字段；拿不到时为 null（老 Release 没有），
 *        此时跳过校验而不是报错 —— 宁可少一层校验，也不能因此装不上。
 */
data class UpdateAsset(
    val name: String,
    val url: String,
    val size: Long,
    val sha256: String?
)

/**
 * 更新检查的状态机。**与桌面端逐字段对齐**（两端不共享代码，但语义要一致）。
 *
 * 把「装不了」也做成正常状态（[NeedsFullPackage]）而不是异常：
 * 安卓上「安装未知应用」权限没开、或者下载目录不可写，都是预期内的分支。
 */
sealed interface UpdateState {

    object Idle : UpdateState

    object Checking : UpdateState

    data class UpToDate(val current: String) : UpdateState

    /**
     * @param apk APK 资产；为 null 说明这个 Release 没挂 APK
     * @param blocked 非 null 表示一定装不了（如没开「安装未知应用」权限）
     */
    data class Available(
        val version: String,
        val notes: String,
        val pageUrl: String,
        val apk: UpdateAsset?,
        val blocked: String?
    ) : UpdateState {
        val canSelfInstall: Boolean get() = apk != null && blocked == null
    }

    data class Downloading(val received: Long, val total: Long) : UpdateState {
        /** 0~100；总长度未知时返回 -1 */
        val percent: Int get() = if (total <= 0) -1 else ((received * 100) / total).toInt()
    }

    /** APK 已下好并交给系统安装器了。安卓不允许应用自己静默安装，所以到此为止 */
    data class InstallerLaunched(val version: String) : UpdateState

    data class NeedsFullPackage(
        val version: String,
        val reason: String,
        val pageUrl: String
    ) : UpdateState

    data class Failed(val message: String) : UpdateState
}
