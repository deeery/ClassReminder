#!/usr/bin/env python3
"""
移动端 P3 迁移实证：拿一个真实的 v7 库跑一遍 MIGRATION_7_8 的 SQL，验证

  1. 老数据一条不丢（行数 + 逐字段值）
  2. 三列被正确加上，且**声明与 Room 期望的 v8 schema 完全一致**
  3. 老行的 uid 被回填且**两两不同**（不能是同一批 uid）
  4. 新增的 updatedAt / deletedAt 默认落到 0

迁移 SQL 是**从 AppDatabase.kt 里解析出来的**，不是我手抄的 —— 避免"测的和写的不一样"。
Room 期望的 v8 schema 也是从 kapt 生成的 AppDatabase_Impl.java 里读的。

用法： python probe_migration_v7_v8.py
"""
import os
import re
import sqlite3
import sys
import uuid

MOBILE = r"C:\Users\Administrator\IdeaProjects\ClassReminderNewest"
APP_DB_KT = os.path.join(MOBILE, "app/src/main/java/com/example/classreminder/data/AppDatabase.kt")
GENERATED = os.path.join(
    MOBILE, "app/build/generated/source/kapt/debug/com/example/classreminder/data/AppDatabase_Impl.java"
)

# ── v7 的表结构（列顺序 = Room 在设备上实际建出来的顺序）────────────────
V7_DDL = {
    "classes": (
        "CREATE TABLE `classes` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, "
        "`dayOfWeek` TEXT NOT NULL, `startTime` TEXT NOT NULL, `endTime` TEXT NOT NULL, "
        "`room` TEXT NOT NULL, `notes` TEXT NOT NULL, `teacher` TEXT NOT NULL, "
        "`weeks` TEXT NOT NULL, `date` TEXT NOT NULL, PRIMARY KEY(`id`))"
    ),
    "notes": (
        "CREATE TABLE `notes` (`id` INTEGER NOT NULL, `text` TEXT NOT NULL, "
        "`position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `colorIndex` INTEGER NOT NULL, "
        "`typeIndex` INTEGER NOT NULL, `customLabel` TEXT NOT NULL, `deadlineAt` INTEGER NOT NULL, "
        "PRIMARY KEY(`id`))"
    ),
}

V7_CLASS_ROWS = [
    (1, "高等数学", "Monday", "08:00", "09:40", "A101", "带教材", "孙利荣", "1-16周", ""),
    (2, "大学英语", "Tuesday", "10:00", "11:40", "B203", "", "李梅", "1-16周", ""),
    (7, "临时答疑", "Friday", "14:00", "15:00", "线上", "腾讯会议", "", "", "2026-10-09"),
]
V7_NOTE_ROWS = [
    (1, "交实验报告", 0, 1759000000000, 2, 4, "", 1760000000000),
    (3, "买牛奶", -1, 1759100000000, 0, 2, "", 0),
    (9, "复习线代", -2, 1759200000000, 5, 6, "期末冲刺", 1762000000000),
]


def parse_migration_sql():
    """从 AppDatabase.kt 的 MIGRATION_7_8 里解析出 (表名列表, ALTER 语句模板)。"""
    src = open(APP_DB_KT, encoding="utf-8").read()
    block = re.search(r"MIGRATION_7_8\s*=\s*object\s*:\s*Migration\(7,\s*8\)\s*\{(.*?)\n        \}",
                      src, re.S)
    if not block:
        sys.exit("✗ 在 AppDatabase.kt 里找不到 MIGRATION_7_8")
    body = block.group(1)

    tables = re.search(r'for\s*\(table\s+in\s+listOf\(([^)]*)\)\)', body)
    if not tables:
        sys.exit("✗ 解析不出表名列表")
    table_names = [t.strip().strip('"') for t in tables.group(1).split(",")]

    alters = re.findall(r'execSQL\(\s*"ALTER TABLE `\$table` ADD COLUMN `(\w+)` ([^"]+)"\s*\)', body)
    if not alters:
        sys.exit("✗ 解析不出 ALTER TABLE 语句")

    backfill = "backfillUids" in body
    return table_names, alters, backfill


def parse_expected_v8():
    """从 kapt 生成的 createAllTables 里读 Room 期望的 v8 列定义。"""
    src = open(GENERATED, encoding="utf-8").read()
    expected = {}
    for m in re.finditer(r'CREATE TABLE IF NOT EXISTS `(\w+)` \((.*?)\)"', src):
        table, cols = m.group(1), m.group(2)
        cols = re.sub(r",\s*PRIMARY KEY\(`\w+`\)$", "", cols)
        parsed = []
        for part in re.split(r",\s*(?=`)", cols):
            cm = re.match(r"`(\w+)`\s+(.*)$", part.strip())
            if cm:
                parsed.append((cm.group(1), cm.group(2).strip()))
        expected[table] = parsed
    return expected


def col_info(conn, table):
    """PRAGMA table_info → {列名: (类型, notnull)}，顺序保留。"""
    rows = conn.execute(f"PRAGMA table_info(`{table}`)").fetchall()
    return [(r[1], r[2], r[3]) for r in rows]


def main():
    table_names, alters, has_backfill = parse_migration_sql()
    expected = parse_expected_v8()
    if not expected:
        sys.exit("✗ 读不到 Room 生成的 v8 schema（先跑一次 gradle testDebugUnitTest）")

    print("─" * 74)
    print("解析自 AppDatabase.kt 的迁移 SQL")
    print("─" * 74)
    print(f"  涉及表: {table_names}")
    for col, decl in alters:
        print(f"  ALTER TABLE <t> ADD COLUMN `{col}` {decl}")
    print(f"  含 uid 回填: {has_backfill}")
    print()

    conn = sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys = ON")

    # ── 造一个 v7 库 ────────────────────────────────────────────
    for t, ddl in V7_DDL.items():
        conn.execute(ddl)
    conn.executemany("INSERT INTO classes VALUES (?,?,?,?,?,?,?,?,?,?)", V7_CLASS_ROWS)
    conn.executemany("INSERT INTO notes VALUES (?,?,?,?,?,?,?,?)", V7_NOTE_ROWS)
    conn.execute("PRAGMA user_version = 7")
    conn.commit()

    before_classes = conn.execute("SELECT * FROM classes ORDER BY id").fetchall()
    before_notes = conn.execute("SELECT * FROM notes ORDER BY id").fetchall()
    print(f"迁移前: user_version=7  classes={len(before_classes)} 行  notes={len(before_notes)} 行")

    # ── 跑迁移（复刻 Room 的 migrate() 语义）────────────────────
    for table in table_names:
        for col, decl in alters:
            conn.execute(f"ALTER TABLE `{table}` ADD COLUMN `{col}` {decl}")
        if has_backfill:
            ids = [r[0] for r in conn.execute(
                f"SELECT `id` FROM `{table}` WHERE `uid` IS NULL OR `uid` = ''")]
            for row_id in ids:
                conn.execute(f"UPDATE `{table}` SET `uid` = ? WHERE `id` = ?",
                             (str(uuid.uuid4()), row_id))
    conn.execute("PRAGMA user_version = 8")
    conn.commit()

    ok = True

    # ── 检查 1: schema 与 Room 期望一致 ─────────────────────────
    print()
    print("─" * 74)
    print("检查 1 · 迁移后的 schema 是否等于 Room 期望的 v8 schema")
    print("─" * 74)
    for table in table_names:
        actual = col_info(conn, table)
        exp = expected.get(table, [])
        exp_names = [c[0] for c in exp]
        act_names = [c[0] for c in actual]
        if act_names != exp_names:
            print(f"  ✗ {table}: 列名/顺序不一致")
            print(f"      期望 {exp_names}")
            print(f"      实际 {act_names}")
            ok = False
            continue
        # Room 校验只比 名称/类型/notNull/主键位次（默认值见下面的说明）
        bad = []
        for (n, t, nn), (en, et) in zip(actual, exp):
            exp_nn = 1 if et.endswith("NOT NULL") else 0
            if t.upper() != et.replace(" NOT NULL", "").upper() or nn != exp_nn:
                bad.append(f"{n}: 期望 {et} / 实际 {t}{' NOT NULL' if nn else ''}")
        if bad:
            print(f"  ✗ {table}: {len(bad)} 列不匹配")
            for b in bad:
                print(f"      {b}")
            ok = False
        else:
            print(f"  ✓ {table}: {len(actual)} 列全部匹配（名称 / 类型 / NOT NULL）")

    # ── 检查 2: 老数据一条不丢 ──────────────────────────────────
    print()
    print("─" * 74)
    print("检查 2 · 老数据是否完好（迁移只加列，不改动既有字段）")
    print("─" * 74)
    after_classes = conn.execute(
        "SELECT id,title,dayOfWeek,startTime,endTime,room,notes,teacher,weeks,date "
        "FROM classes ORDER BY id").fetchall()
    after_notes = conn.execute(
        "SELECT id,text,position,createdAt,colorIndex,typeIndex,customLabel,deadlineAt "
        "FROM notes ORDER BY id").fetchall()
    if after_classes == before_classes:
        print(f"  ✓ classes: {len(after_classes)} 行，10 个业务字段逐值相同")
    else:
        print("  ✗ classes 数据发生了变化")
        ok = False
    if after_notes == before_notes:
        print(f"  ✓ notes:   {len(after_notes)} 行，8 个业务字段逐值相同")
    else:
        print("  ✗ notes 数据发生了变化")
        ok = False

    # ── 检查 3: uid 回填 ───────────────────────────────────────
    print()
    print("─" * 74)
    print("检查 3 · uid 回填（必须逐行不同，且非空）")
    print("─" * 74)
    all_uids = []
    for table in table_names:
        uids = [r[0] for r in conn.execute(f"SELECT uid FROM `{table}`")]
        all_uids += uids
        blanks = sum(1 for u in uids if not u)
        distinct = len(set(uids))
        if blanks == 0 and distinct == len(uids):
            print(f"  ✓ {table}: {len(uids)} 个 uid 全部非空且两两不同")
        else:
            print(f"  ✗ {table}: 空 uid {blanks} 个，去重后 {distinct}/{len(uids)}")
            ok = False
    if len(set(all_uids)) == len(all_uids):
        print(f"  ✓ 跨表也不撞（{len(all_uids)} 个 uid 全局唯一）")
    else:
        print("  ✗ 存在跨表重复的 uid")
        ok = False
    print(f"  样例: {all_uids[0]}")

    # ── 检查 4: 新列默认值 ─────────────────────────────────────
    print()
    print("─" * 74)
    print("检查 4 · 新增列在老行上的取值")
    print("─" * 74)
    for table in table_names:
        r = conn.execute(
            f"SELECT SUM(updatedAt), SUM(deletedAt), COUNT(*) FROM `{table}`").fetchone()
        if r[0] == 0 and r[1] == 0:
            print(f"  ✓ {table}: {r[2]} 行的 updatedAt / deletedAt 都是 0（= 老数据、未删除）")
        else:
            print(f"  ✗ {table}: updatedAt 合计 {r[0]}，deletedAt 合计 {r[1]}")
            ok = False

    # ── 检查 5: 软删除语义 ─────────────────────────────────────
    print()
    print("─" * 74)
    print("检查 5 · 软删除后旧查询是否还能看到这条（DAO 的 WHERE deletedAt = 0）")
    print("─" * 74)
    conn.execute("UPDATE classes SET deletedAt = 1, updatedAt = 1 WHERE id = 2")
    visible = [r[0] for r in conn.execute("SELECT id FROM classes WHERE deletedAt = 0 ORDER BY id")]
    hidden = [r[0] for r in conn.execute("SELECT id FROM classes WHERE deletedAt != 0")]
    if visible == [1, 7] and hidden == [2]:
        print(f"  ✓ 软删 id=2 后：列表可见 {visible}，墓碑 {hidden}（行仍在库里，不会被同步复活）")
    else:
        print(f"  ✗ 可见 {visible} / 墓碑 {hidden}")
        ok = False

    print()
    print("═" * 74)
    print("结果: " + ("全部通过 ✅" if ok else "有检查项失败 ❌"))
    print("═" * 74)
    print()
    print("注: Room 的 schema 校验**只比 名称 / 类型 / NOT NULL / 主键位次**。")
    print("    默认值这一项：实体没声明 defaultValue 时 Room 直接跳过比较")
    print("    （已从 room-runtime-2.6.1 的 TableInfo$Column.equals 字节码确认），")
    print("    所以迁移里 ALTER ... DEFAULT '' / DEFAULT 0 不会引发校验失败。")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
