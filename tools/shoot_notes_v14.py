"""便签「标题 + 内容」实机验收截图（v1.4）。

验收目标（每一项都要有图或落盘证据，不靠描述）：
  ① 迁移后的老便签：原本的正文进了标题，正文为空 → 列表只有一行标题
  ② 新建弹窗：标题 + 内容两个输入框都在，且「内容可空」
  ③ 保存后列表两行：标题 + 内容摘要
  ④ 平板横屏双栏：右栏详情把标题放大 + 分隔线 + 正文
  ⑤ 手机 / 平板竖屏 / 平板横屏三种形态都不炸版

用法：
  python shoot_notes_v14.py <输出目录>

⚠️ 用 ASCII 文本输入：`adb shell input text` 不吃非 ASCII，中文只能靠 IME，
   脚本里用不了。要截中文效果图请改用 seed 脚本直接写库。
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB") or r"E:\AndroidSDK\platform-tools\adb.exe"
PKG = "com.example.classreminder.test"
ACT = "com.example.classreminder.MainActivity"
REMOTE_XML = "/sdcard/stumate-notes.xml"

CHROME = {
    "今天", "课表", "便签", "设置", "快速便签", "搜索", "提醒服务未启动",
    "运行中", "未启动", "标题", "内容", "保存", "取消", "添加便签", "编辑便签",
    "周一是第几周", "本周是第几周",
}


def sh(*args, binary=False):
    r = subprocess.run([ADB, *args], capture_output=True, check=False)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def set_form(w, h, dpi):
    sh("shell", "wm", "size", f"{w}x{h}")
    sh("shell", "wm", "density", str(dpi))
    time.sleep(2.5)


def reset_form():
    sh("shell", "wm", "size", "reset")
    sh("shell", "wm", "density", "reset")
    time.sleep(2.5)


def restart(display="0"):
    sh("shell", "am", "force-stop", PKG)
    time.sleep(0.8)
    # ⚠️ 必须指定 --display 0：这台模拟器上挂着 emulator 的虚拟副屏（display 2），
    #    `am start` 会沿用上次的 display，于是 screencap 截到的是主屏的桌面。
    sh("shell", "am", "start", "--display", display, "-n", f"{PKG}/{ACT}")
    deadline = time.time() + 40
    while time.time() < deadline:
        if PKG in sh("shell", "dumpsys", "window", "displays"):
            break
        time.sleep(1.0)
    time.sleep(3.0)


def dump():
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
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds") or "")
        if label and m:
            x1, y1, x2, y2 = (int(v) for v in m.groups())
            out.append((label, ((x1 + x2) // 2, (y1 + y2) // 2)))
    return out


def tap(label, nodes=None):
    nodes = nodes if nodes is not None else dump()
    for d, (cx, cy) in nodes:
        if d == label:
            sh("shell", "input", "tap", str(cx), str(cy))
            time.sleep(1.6)
            return True
    print(f"     ! 找不到 {label!r}（候选：{[d for d, _ in nodes]})")
    return False


def tap_first_note(min_y=120):
    """点内容区里第一条便签。排除骨架文字与时间刻度，避免点到标题栏。"""
    nodes = dump()
    for label, (cx, cy) in nodes:
        if label in CHROME or len(label) < 2:
            continue
        if re.fullmatch(r"\d{1,2}:\d{2}", label):
            continue
        if cy < min_y or cx < 90:
            continue
        sh("shell", "input", "tap", str(cx), str(cy))
        time.sleep(1.6)
        print(f"     选中便签：{label!r} @({cx},{cy})")
        return True
    print(f"     ! 没找到可点的便签（候选：{[d for d, _ in nodes]})")
    return False


def type_text(s):
    sh("shell", "input", "text", s.replace(" ", "%s"))
    time.sleep(0.8)


def shot(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = sh("exec-out", "screencap", "-p", binary=True)
    with open(path, "wb") as f:
        f.write(data)
    print(f"     -> {os.path.basename(path)}  {len(data) // 1024} KB")
    return len(data)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "build/notes-v14-shots"
    sizes = {}

    # ── ① 手机竖屏 ──
    print("[1] 手机竖屏：迁移后的老便签 + 新建两行便签")
    reset_form()
    restart()
    tap("便签")
    sizes["phone-1-list-migrated"] = shot(f"{out}/phone-1-list-migrated.png")

    tap("添加便签")
    sizes["phone-2-dialog-empty"] = shot(f"{out}/phone-2-dialog-empty.png")

    tap("标题")
    type_text("Lab report")
    tap("内容")
    type_text("Due Friday, bring the printed copy")
    sizes["phone-3-dialog-filled"] = shot(f"{out}/phone-3-dialog-filled.png")

    tap("保存")
    sizes["phone-4-list-twoline"] = shot(f"{out}/phone-4-list-twoline.png")

    # ── ② 平板竖屏：只应有侧栏，不开双栏 ──
    print("[2] 平板竖屏 800dp x 1280dp")
    set_form(800, 1280, 160)
    restart()
    tap("便签")
    sizes["tablet-p-notes"] = shot(f"{out}/tablet-portrait-notes.png")

    # ── ③ 平板横屏：侧栏 + 便签双栏，选中后右栏出详情 ──
    print("[3] 平板横屏 1280dp x 800dp")
    set_form(1280, 800, 160)
    restart()
    tap("便签")
    sizes["tablet-l-notes"] = shot(f"{out}/tablet-landscape-notes.png")
    tap_first_note(min_y=120)
    sizes["tablet-l-notes-detail"] = shot(f"{out}/tablet-landscape-notes-detail.png")

    # ── 收尾：恢复手机形态，别把设备留在奇怪的状态 ──
    reset_form()
    restart()

    print("\n=== 同一形态内各图字节数必须互不相同（相同 = 界面没变过）===")
    for group in ("phone", "tablet-p", "tablet-l"):
        vals = {k: v for k, v in sizes.items() if k.startswith(group)}
        uniq = len(set(vals.values()))
        print(f"  [{'OK' if uniq == len(vals) else 'SUSPECT'}] "
              f"{group}: {len(vals)} 张 / {uniq} 种字节数")


if __name__ == "__main__":
    main()
