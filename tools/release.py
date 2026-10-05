"""安卓端发布工具：把 release APK 归档进 dist/、建 GitHub Release 并上传。

为什么要和桌面端各写一份
------------------------
两端**不共享代码**（见项目约定），发布链路也一样：桌面端传的是
`StuMate-patch-x.y.z.zip` + 绿色版 zip，安卓端只有**一个** APK 资产。
硬把桌面那个脚本改造成通用的，只会让两边都变难读。

但资产命名这条规则两端必须一致，因为**客户端是按名字找资产的**：
安卓端 `UpdateCenter.apkAssetName()` 生成 `StuMate-<version>-release.apk`，
和这里 `dist/` 的命名、以及历史上 1.3 / 1.4 那两个包保持一致。
名字对不上的后果是「检查到有新版本，但找不到 APK」，用户只能去下载页。

用法
----
    # 构建 release APK（R8 + 签名），并归档到 dist/
    python tools/release.py pack

    # 从 git log 生成 release notes
    python tools/release.py notes --since v1.4

    # 建 tag + Release + 上传 APK
    python tools/release.py publish --notes-file /tmp/notes.md

    # 只看要做什么，不真调 API
    python tools/release.py publish --dry-run

⚠️ token 从 `~/.git-credentials` 读（git 的 credential store）。
   **绝不要把 token 打进 APK** —— APK 可以被反编译，等于把密码发出去。
   客户端匿名调 api.github.com 就够了，仓库是 public。
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request

REPO = "deeery/ClassReminder"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(ROOT, "dist")
API = "https://api.github.com"

# Gradle 没 wrapper（这个仓库本来就没有），借本机那一份。
#
# ⚠️ Windows 下必须调 `gradle.bat`。同目录的 `gradle` 是 POSIX shell 脚本，
# 直接 CreateProcess 会得到 `WinError 193 %1 不是有效的 Win32 应用程序` ——
# 报错完全不提「你调错了文件」，容易以为是 JDK 装坏了。
GRADLE_BIN = os.path.expanduser("~/.gradle/wrapper/dists/gradle-8.4-bin/gradle-8.4/bin")
GRADLE = os.path.join(GRADLE_BIN, "gradle.bat" if os.name == "nt" else "gradle")
JAVA_HOME = "E:/DevTools/Java/jdk-17.0.12+7"
ANDROID_HOME = "E:/AndroidSDK"

# 独立构建目录（另一会话可能正在这个仓库上跑 Gradle）。
ALT_BUILD = "F:/DownloadQQ/stumate-android-altbuild"
ALT_CACHE = "F:/DownloadQQ/stumate-android-altcache"
ALT_INIT = "F:/DownloadQQ/alt-build-init-android.gradle.kts"

APK_CANDIDATES = [
    os.path.join(ALT_BUILD, "outputs", "apk", "release", "app-release.apk"),
    os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk"),
]


def die(msg):
    print("ERROR: " + msg, file=sys.stderr)
    sys.exit(1)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def human(n):
    return f"{n:,} 字节（{n / 1048576:.2f} MB）"


def version_name():
    """版本号唯一来源 = app/build.gradle.kts 里 `stumateVersionName` 的默认值。

    绝不在这里写死 —— 一旦脚本和构建脚本各记一份，发出去的包和 Release 上的
    文件名就会对不上，而客户端是按 `StuMate-<version>-release.apk` 找资产的。

    ⚠️ 只读**默认值**，不读 `-PstumateVersionName` 的覆盖值：那个开关是给验收
    造「自称旧版」的包用的，正式发布的版本号永远取默认值。
    """
    text = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    m = re.search(r'stumateVersionName"\)\.getOrElse\("([^"]+)"\)', text)
    if not m:
        die("app/build.gradle.kts 里没找到 stumateVersionName 的默认值")
    return m.group(1)


def version_code():
    text = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    m = re.search(r"versionCode\s*=\s*(\d+)", text)
    return int(m.group(1)) if m else 0


def apk_name(version):
    return f"StuMate-{version}-release.apk"


# ── 构建 ────────────────────────────────────────────────────────

def build_release():
    env = dict(os.environ)
    env["JAVA_HOME"] = JAVA_HOME
    env["ANDROID_HOME"] = ANDROID_HOME
    cmd = [GRADLE, "--offline", "--no-build-cache",
           "--project-cache-dir", ALT_CACHE,
           "-I", ALT_INIT,
           ":app:assembleRelease"]
    print("$ " + " ".join(cmd))
    r = subprocess.run(cmd, cwd=ROOT, env=env)
    if r.returncode != 0:
        die("assembleRelease 失败")


def find_apk(explicit=None):
    for c in ([explicit] if explicit else APK_CANDIDATES):
        if c and os.path.isfile(c):
            return os.path.abspath(c)
    die("找不到 release APK，试过：\n  " + "\n  ".join(str(c) for c in APK_CANDIDATES)
        + "\n先跑：python tools/release.py pack")


def pack(args):
    version = args.version or version_name()
    if not args.skip_build:
        build_release()
    src = find_apk(args.apk)
    if src.endswith("app-release-unsigned.apk"):
        die("只找到 unsigned APK —— keystore.properties 没配好，装不上，不发布")

    os.makedirs(DIST, exist_ok=True)
    out = os.path.join(DIST, apk_name(version))
    shutil.copy2(src, out)

    print(f"版本     {version} (versionCode {version_code()})")
    print(f"源 APK   {src}")
    print(f"归档到   {out}")
    print(f"  {human(os.path.getsize(out))}")
    print(f"  sha256 {sha256(out)}")
    print()
    print("⚠️ 客户端按这个名字找资产：" + apk_name(version))
    print("   名字改了就要同步改 UpdateCenter.apkAssetName()，否则用户只能去下载页。")


# ── release notes ───────────────────────────────────────────────

def git(*a):
    r = subprocess.run(["git", "-C", ROOT, *a], capture_output=True, text=True)
    return r.stdout.strip()


def gen_notes(args):
    version = args.version or version_name()
    rng = f"{args.since}..HEAD" if args.since else "-20"
    log = git("log", "--pretty=format:%h%x09%s", rng)
    lines = [l for l in log.splitlines() if l.strip()]
    body = [f"## StuMate 安卓版 {version}", ""]
    if not lines:
        body.append("（没有新的提交）")
    for l in lines:
        h, _, subject = l.partition("\t")
        body.append(f"- {subject} (`{h}`)")
    body += ["", "### 下载", "",
             f"- **`{apk_name(version)}`** —— 直接安装；App 内「设置 → 关于 → 检查更新」"
             "也会自动下载这一个包。"]
    return "\n".join(body)


# ── GitHub API ──────────────────────────────────────────────────

def gh_token():
    if os.environ.get("GITHUB_TOKEN"):
        return os.environ["GITHUB_TOKEN"]
    path = os.path.expanduser("~/.git-credentials")
    if not os.path.isfile(path):
        die(f"找不到 {path}；也可以用环境变量 GITHUB_TOKEN")
    for line in open(path, encoding="utf-8"):
        m = re.match(r"https://([^:]+):([^@]+)@github\.com", line.strip())
        if m:
            return m.group(2)
    die("~/.git-credentials 里没找到 github.com 的凭据")


def api(method, url, token, payload=None, raw=None, ctype="application/json"):
    data = raw if raw is not None else (
        json.dumps(payload).encode() if payload is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "StuMate-Release-Script")
    if data is not None:
        req.add_header("Content-Type", ctype)
    try:
        with urllib.request.urlopen(req, timeout=180) as r:
            body = r.read()
            return json.loads(body) if body else None
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")[:600]
        die(f"{method} {url} → HTTP {e.code}\n{detail}")


def publish(args):
    version = args.version or version_name()
    tag = args.tag or f"v{version}"
    apk = os.path.join(DIST, apk_name(version))
    if not os.path.isfile(apk):
        die(f"缺资产 {apk}；先跑 `python tools/release.py pack`")

    notes = (open(args.notes_file, encoding="utf-8").read()
             if args.notes_file else gen_notes(args))

    if args.dry_run:
        print(f"[dry-run] 仓库 {args.repo}   tag {tag}   StuMate 安卓版 {version}")
        print(f"[dry-run] 资产 {os.path.basename(apk)}  {human(os.path.getsize(apk))}")
        print("[dry-run] notes 前 12 行：")
        print("\n".join(notes.splitlines()[:12]))
        return

    token = gh_token()
    head = git("rev-parse", "HEAD")

    if git("ls-remote", "--tags", "origin", tag):
        print(f"tag {tag} 已存在，跳过创建")
    else:
        print(f"创建并推送 tag {tag} → {head[:8]}")
        subprocess.run(["git", "-C", ROOT, "tag", "-a", tag, "-m", f"StuMate 安卓版 {version}"],
                       check=True)
        subprocess.run(["git", "-C", ROOT, "push", "origin", tag], check=True)

    print("创建 Release …")
    rel = api("POST", f"{API}/repos/{args.repo}/releases", token, payload={
        "tag_name": tag,
        "name": f"StuMate 安卓版 {version}",
        "body": notes,
        "draft": False,
        "prerelease": False,
    })
    print("  " + rel["html_url"])

    name = os.path.basename(apk)
    print(f"上传 {name} …")
    api("POST",
        f"https://uploads.github.com/repos/{args.repo}/releases/{rel['id']}/assets?name={name}",
        token, raw=open(apk, "rb").read(), ctype="application/vnd.android.package-archive")
    print("  OK")

    print("\n完成。客户端会从这里读到版本：")
    print(f"  {API}/repos/{args.repo}/releases/latest")


def main():
    p = argparse.ArgumentParser(description="StuMate 安卓端发布工具")
    sub = p.add_subparsers(dest="cmd", required=True)

    pk = sub.add_parser("pack", help="构建 release APK 并归档到 dist/")
    pk.add_argument("--version", help="默认取 build.gradle.kts 的 versionName")
    pk.add_argument("--apk", help="已有 APK 路径（跳过查找）")
    pk.add_argument("--skip-build", action="store_true", help="不重新构建，直接用现有 APK")
    pk.set_defaults(func=pack)

    nt = sub.add_parser("notes", help="从 git log 生成 release notes")
    nt.add_argument("--version")
    nt.add_argument("--since", help="起始 tag，如 v1.4")
    nt.set_defaults(func=lambda a: print(gen_notes(a)))

    pb = sub.add_parser("publish", help="建 tag + Release + 上传 APK")
    pb.add_argument("--version")
    pb.add_argument("--tag")
    pb.add_argument("--repo", default=REPO)
    pb.add_argument("--notes-file")
    pb.add_argument("--since")
    pb.add_argument("--dry-run", action="store_true")
    pb.set_defaults(func=publish)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
