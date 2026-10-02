# ── R8 / ProGuard 规则（release 变体）────────────────────────────
#
# 这个 App **自身没有任何反射**（已全仓 grep `Class.forName` /
# `getDeclaredField` / `getDeclaredMethod` / `isAccessible` 确认过），
# 所以不需要靠 keep 规则去「救」某个反射调用点。
# Room / Compose / 协程 / AndroidX 都自带 consumer rules，这里不重复抄。
#
# 下面只留两条真正有价值的：

# 1) 崩溃栈要可读。
#    不保留的话，release 包的堆栈全是 a.b.c，用户反馈「一点就闪退」时完全没法定位。
#    这是 release 包里最容易后悔的一件事。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 2) 给 Room 的生成物兜个底。
#    kapt 生成的 `AppDatabase_Impl` 是通过反射按名字找出来的，一旦被裁掉，
#    报错形式是「运行时找不到实现」或直接 AbstractMethodError，
#    很难和混淆联系起来。Room 的 consumer rules 通常会处理，
#    但这里显式写一遍，成本极低、坏了很难查。
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
