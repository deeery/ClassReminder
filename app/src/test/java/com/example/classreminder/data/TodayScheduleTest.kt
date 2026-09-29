package com.example.classreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 2026-09-15 是周二 */
class TodayScheduleTest {

    private fun at(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 15, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun cls(
        title: String,
        day: String,
        start: String,
        end: String,
        weeks: String = "",
        date: String = ""
    ) = ClassEntity(
        id = title.hashCode(),
        title = title,
        dayOfWeek = day,
        startTime = start,
        endTime = end,
        room = "A101",
        weeks = weeks,
        date = date
    )

    private val tuesday = listOf(
        cls("第一节", "Tuesday", "08:00", "09:35"),
        cls("第二节", "Tuesday", "09:50", "12:15"),
        cls("第三节", "Tuesday", "15:10", "16:45"),
        cls("周一的课", "Monday", "08:00", "09:35")
    )

    @Test
    fun countsOnlyTodaysClasses() {
        val today = TodaySchedule.today(tuesday, currentWeek = null, now = at(7, 0))
        assertEquals(listOf("第一节", "第二节", "第三节"), today.map { it.entity.title })
    }

    @Test
    fun remainingShrinksThroughTheDay() {
        val today = TodaySchedule.today(tuesday, currentWeek = null, now = at(10, 0))

        // 10:00：第一节已下课、第二节正在上 → 还剩 2 节（不是总数 3）
        assertEquals(2, TodaySchedule.remaining(today, at(10, 0)).size)
        // 13:00 空闲时段：还剩第三节
        assertEquals(1, TodaySchedule.remaining(today, at(13, 0)).size)
        // 14:00：还剩 1 节
        assertEquals(1, TodaySchedule.remaining(today, at(14, 0)).size)
        // 16:45 之后：全部结束
        assertTrue(TodaySchedule.remaining(today, at(16, 46)).isEmpty())
    }

    @Test
    fun nextClassIsTheNextUnfinishedOne() {
        val today = TodaySchedule.today(tuesday, currentWeek = null, now = at(10, 0))

        // 空闲时段里「下一节」不能还停在今天最早那节（它已经上完了）
        assertEquals("第二节", TodaySchedule.remaining(today, at(10, 0)).first().entity.title)
        assertEquals("第三节", TodaySchedule.remaining(today, at(12, 30)).first().entity.title)
    }

    @Test
    fun respectsCurrentWeek() {
        val withWeeks = listOf(
            cls("第 5-14 周", "Tuesday", "08:00", "09:35", weeks = "5-14周"),
            cls("第 1-8 周", "Tuesday", "09:50", "12:15", weeks = "1-8周"),
            cls("没写周次", "Tuesday", "15:10", "16:45")
        )

        val week1 = TodaySchedule.today(withWeeks, currentWeek = 1, now = at(7, 0))
        assertEquals(listOf("第 1-8 周", "没写周次"), week1.map { it.entity.title })

        val week6 = TodaySchedule.today(withWeeks, currentWeek = 6, now = at(7, 0))
        assertEquals(listOf("第 5-14 周", "第 1-8 周", "没写周次"), week6.map { it.entity.title })

        // 未校准周次时不限制
        assertEquals(3, TodaySchedule.today(withWeeks, currentWeek = null, now = at(7, 0)).size)
    }

    @Test
    fun skipsBrokenTimes() {
        val broken = listOf(
            cls("坏时间", "Tuesday", "8点", "9点"),
            cls("正常", "Tuesday", "08:00", "09:00")
        )
        assertEquals(listOf("正常"), TodaySchedule.today(broken, null, at(7, 0)).map { it.entity.title })
        assertNull(TodaySchedule.today(broken, null, at(7, 0)).firstOrNull { it.entity.title == "坏时间" })
    }

    @Test
    fun oneOffReminderOnlyFiresOnItsOwnDate() {
        val classes = listOf(
            cls("每周课", "Tuesday", "08:00", "09:35", weeks = "1-16周"),
            cls("临时提醒", "Tuesday", "14:00", "15:00", date = "2026-09-15")   // 就是今天
        )

        val today = TodaySchedule.today(classes, currentWeek = 1, now = at(7, 0))
        assertEquals(listOf("每周课", "临时提醒"), today.map { it.entity.title })

        // 下周二（2026-09-22）：每周课照常，临时提醒不再出现
        val nextTuesday = at(7, 0) + 7L * 24 * 60 * 60 * 1000
        assertEquals(listOf("每周课"), TodaySchedule.today(classes, 1, nextTuesday).map { it.entity.title })
        // 周三：两样都没有
        assertTrue(TodaySchedule.today(classes, 1, at(7, 0) + 24 * 60 * 60 * 1000L).isEmpty())
    }

    @Test
    fun oneOffReminderIgnoresWeekCalibration() {
        // 临时提醒的日期不在任何"周次"里，未校准周次也必须照常生效
        val classes = listOf(cls("临时提醒", "Tuesday", "14:00", "15:00", date = "2026-09-15"))
        assertEquals(1, TodaySchedule.today(classes, currentWeek = null, now = at(7, 0)).size)
        assertEquals("临时提醒", TodaySchedule.today(classes, null, at(7, 0)).single().entity.title)
    }

    @Test
    fun oneOffDateRangeCheckForWeekView() {
        val oneOff = cls("临时提醒", "Sunday", "14:00", "15:00", date = "2026-09-20")
        val longTerm = cls("每周课", "Monday", "08:00", "09:35", weeks = "1-16周")

        // 9-14 ~ 9-21 这一周包含 9-20
        val weekStart = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 14, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000

        assertTrue(TodaySchedule.occursInRange(oneOff, weekStart, weekEnd))
        assertFalse(TodaySchedule.occursInRange(oneOff, weekEnd, weekEnd + 7L * 24 * 60 * 60 * 1000))
        // 长期课程不受这个判断影响
        assertTrue(TodaySchedule.occursInRange(longTerm, weekEnd, weekEnd + 7L * 24 * 60 * 60 * 1000))
    }

    @Test
    fun dayNameMatchesStoredDayNames() {
        assertEquals("Tuesday", TodaySchedule.dayNameOf(at(10, 0)))
        assertEquals("Monday", TodaySchedule.dayNameOf(at(0, 0) - 24 * 60 * 60 * 1000L))
    }
}
