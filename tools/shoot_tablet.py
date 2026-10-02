"""平板适配实机验收：按逻辑尺寸切换设备形态，逐个页面截图。

为什么用 `wm size` / `wm density` 而不是新建一个平板 AVD：
唯一的系统镜像已装在手机 AVD 上，而 AVD 目录 6.6 GB —— 复制一份只为改
两行 LCD 配置太重。`wm size/density` 改的是**逻辑**尺寸与密度，
应用侧读到的 `Configuration.screenWidthDp` 就是我们要验证的那个值，
对「断点有没有生效」这个目的来说完全等价。

⚠️ 断点判定用的是 `screenWidthDp`，所以这里必须让 density 与 size 配合出
想要的 dp 宽度：dp = px / (density / 160)。本脚本统一用 **density=160**，
于是「1px = 1dp」，尺寸直接就是 dp，不用心算。

用法：
  python shoot_tablet.py <输出目录>
"""
import ctypes
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB") or r"E:\AndroidSDK\platform-tools\adb.exe"
PKG = "com.example.classreminder.test"
ACT = "com.example.classreminder.MainActivity"
REMOTE_XML = "/sdcard/stumate-ui.xml"


def sh(*args, binary=False):
    return subprocess.run([ADB, *args], capture_output=True,
                          check=False).stdout if binary else subprocess.run(
        [ADB, *args], capture_output=True, text=True, check=False).stdout


def set_form(w, h, dpi):
    sh("shell", "wm", "size", f"{w}x{h}")
    sh("shell", "wm", "density", str(dpi))
    time.sleep(2.5)


def reset_form():
    sh("shell", "wm", "size", "reset")
    sh("shell", "wm", "density", "reset")
    time.sleep(2.5)


def restart():
    sh("shell", "am", "force-stop", PKG)
    time.sleep(0.8)
    sh("shell", "am", "start", "-n", f"{PKG}/{ACT}")
    # 等窗口真的画出来，而不是死等固定秒数
    deadline = time.time() + 40
    while time.time() < deadline:
        if "mCurrentFocus" in sh("shell", "dumpsys", "window", "displays"):
            out = sh("shell", "dumpsys", "window", "displays")
            if PKG in out:
                break
        time.sleep(1.0)
    time.sleep(3.0)


def dump():
    """返回 [(label, (cx, cy))]，label 取 content-desc 或 text。

    ⚠️ 导航项**不能只按 content-desc 找**：`NavigationBarItem` /
    `NavigationRailItem` 会把子节点的语义合并到自己身上，图标上的
    `contentDescription` 被 label 的 text 覆盖 —— 于是按 content-desc 一个都找不到
    （实测候选里只剩 '折叠周一' 这种别的控件）。所以两个属性都要看。

    比写死坐标稳得多：同一个控件在手机 / 平板 / 横竖屏下位置完全不同，
    写死坐标会在换形态时静默点到别的地方，截出一堆「看起来没改」的图。
    """
    for _ in range(3):
        sh("shell", "uiautomator", "dump", REMOTE_XML)
        raw = sh("shell", "cat", REMOTE_XML)
        if "<hierarchy" in raw:
            break
        time.sleep(1.5)
    else:
        return []
    try:
        root = ET.fromstring(raw[raw.index("<hierarchy"):])
    except ET.ParseError:
        return []
    out = []
    for node in root.iter("node"):
        label = (node.get("content-desc") or node.get("text") or "").strip()
        bounds = node.get("bounds") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if label and m:
            x1, y1, x2, y2 = (int(v) for v in m.groups())
            out.append((label, ((x1 + x2) // 2, (y1 + y2) // 2)))
    return out


def tap_desc(desc, nodes=None):
    nodes = nodes if nodes is not None else dump()
    for d, (cx, cy) in nodes:
        if d == desc:
            sh("shell", "input", "tap", str(cx), str(cy))
            time.sleep(1.6)
            return True
    print(f"     ! 找不到 {desc!r}（候选：{[d for d, _ in nodes]}）")
    return False


def tap_xy(x, y, wait=1.6):
    sh("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


# 这些是导航 / 页面骨架上的文字，不是「一条便签」或「一节课」，
# 按文字选课程块时要把它们排除掉，否则会点到标题或分段控件上
CHROME_LABELS = {
    "今天", "课表", "便签", "设置", "未启动", "运行中",
    "表格", "列表", "校准周数", "添加课程或便签", "全部", "快速便签",
    "周一", "周二", "周三", "周四", "周五", "周六", "周日",
}


def tap_first_content(min_y=140, min_len=3):
    """点中内容区里的第一个「实体」（一条便签 / 一节课）。

    按文字找而不是写死坐标：课程块在 7 列网格里的位置随周次、课程数变化，
    写死坐标在换数据后会静默点空 —— 截图看着「功能没生效」，其实只是没点到。

    ⚠️ 必须排除**时间轴刻度**（`06:00` / `09:00`…）：它们是网格里最靠左、
    最靠上的一批文字节点，按「第一个非骨架文字」去点必然先撞上它们，
    而刻度是不可点的 —— 于是又变成「点了没反应」。实测踩过。
    """
    nodes = dump()
    for label, (cx, cy) in nodes:
        if label in CHROME_LABELS or len(label) < min_len:
            continue
        if re.fullmatch(r"\d{1,2}:\d{2}", label):  # 时间轴刻度
            continue
        if cy < min_y:
            continue
        # 落在侧栏里的不要（侧栏文字已在上面的集合里，这里是双保险）
        if cx < 90:
            continue
        sh("shell", "input", "tap", str(cx), str(cy))
        time.sleep(1.6)
        print(f"     选中内容：{label!r} @({cx},{cy})")
        return True
    print(f"     ! 内容区没找到可点的实体（候选：{[d for d, _ in nodes]}）")
    return False


def shot(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = sh("exec-out", "screencap", "-p", binary=True)
    with open(path, "wb") as f:
        f.write(data)
    # 同批图字节数完全一样 = 根本没变（见技能 screenshot-false-negative）
    print(f"     -> {os.path.basename(path)}  {len(data) // 1024} KB")
    return len(data)


def size_of(path):
    out = sh("shell", "wm", "size")
    den = sh("shell", "wm", "density")
    print(f"     形态 {out.strip()} / {den.strip()}")
    return out, den


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "build/tablet-shots"
    sizes = {}

    # ── ① 手机竖屏（回归：必须与改动前一致）──
    print("[1] 手机 411dp × 914dp（1080x2400 @ 420dpi）")
    reset_form()
    restart()
    for tab in ("今天", "课表", "便签", "设置"):
        nodes = dump()
        tap_desc(tab, nodes)
        sizes[f"phone-{tab}"] = shot(os.path.join(out_dir, f"phone-{tab}.png"))
    size_of(out_dir)

    # ── ② 平板竖屏：只应出现侧栏，不开双栏 ──
    print("[2] 平板竖屏 800dp × 1280dp（density 160）")
    set_form(800, 1280, 160)
    restart()
    for tab in ("今天", "课表", "便签", "设置"):
        nodes = dump()
        tap_desc(tab, nodes)
        sizes[f"tablet-portrait-{tab}"] = shot(
            os.path.join(out_dir, f"tablet-portrait-{tab}.png"))

    # ── ③ 平板横屏：侧栏 + 便签双栏 + 课表双栏 ──
    print("[3] 平板横屏 1280dp × 800dp（density 160）")
    set_form(1280, 800, 160)
    restart()
    for tab in ("今天", "课表", "便签", "设置"):
        nodes = dump()
        tap_desc(tab, nodes)
        sizes[f"tablet-landscape-{tab}"] = shot(
            os.path.join(out_dir, f"tablet-landscape-{tab}.png"))
        # 课表 / 便签页再选一条内容，验证「选中 → 右栏出内容」
        if tab == "课表":
            # 列表模式没有网格选中（那是它自己的逻辑），先切到「表格」再点一节课
            tap_desc("表格")
            tap_first_content(min_y=200)
            sizes[f"tablet-landscape-{tab}-selected"] = shot(
                os.path.join(out_dir, f"tablet-landscape-{tab}-selected.png"))
        elif tab == "便签":
            tap_first_content(min_y=120)
            sizes[f"tablet-landscape-{tab}-selected"] = shot(
                os.path.join(out_dir, f"tablet-landscape-{tab}-selected.png"))

    # 收尾：恢复成手机形态，别把设备留在奇怪的状态
    reset_form()
    restart()

    print("\n=== 同一形态下各图字节数必须互不相同（相同 = 没变过）===")
    for group in ("phone", "tablet-portrait", "tablet-landscape"):
        vals = {k: v for k, v in sizes.items() if k.startswith(group)}
        uniq = len(set(vals.values()))
        flag = "OK" if uniq == len(vals) else "SUSPECT"
        print(f"  [{flag}] {group}: {len(vals)} 张 / {uniq} 种字节数")


if __name__ == "__main__":
    main()
