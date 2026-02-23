package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.model.ScheduleType
import me.rerere.rikkahub.data.model.ScheduledTask
import me.rerere.rikkahub.data.model.TaskRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.uuid.Uuid

class ScheduledPromptTimeTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun `next daily trigger should stay on same day when time not passed`() {
        val now = ZonedDateTime.of(2026, 2, 23, 8, 0, 0, 0, zone)
        val task = task(
            scheduleType = ScheduleType.DAILY,
            timeMinutesOfDay = 9 * 60
        )

        val next = ScheduledPromptTime.nextTriggerAt(task, now)
        assertEquals(2026, next.year)
        assertEquals(2, next.monthValue)
        assertEquals(23, next.dayOfMonth)
        assertEquals(9, next.hour)
        assertEquals(0, next.minute)
    }

    @Test
    fun `next weekly trigger should jump to next week when time already passed`() {
        val now = ZonedDateTime.of(2026, 2, 23, 20, 0, 0, 0, zone) // Monday
        val task = task(
            scheduleType = ScheduleType.WEEKLY,
            timeMinutesOfDay = 9 * 60,
            dayOfWeek = DayOfWeek.MONDAY.value
        )

        val next = ScheduledPromptTime.nextTriggerAt(task, now)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(3, next.monthValue)
        assertEquals(2, next.dayOfMonth)
        assertEquals(9, next.hour)
    }

    @Test
    fun `catch up should be false for newly created task`() {
        val now = ZonedDateTime.of(2026, 2, 23, 8, 0, 0, 0, zone)
        val createdAt = ZonedDateTime.of(2026, 2, 23, 7, 50, 0, 0, zone).toInstant().toEpochMilli()
        val task = task(
            scheduleType = ScheduleType.DAILY,
            timeMinutesOfDay = 7 * 60,
            createdAt = createdAt,
            lastRunAt = 0L
        )

        assertFalse(ScheduledPromptTime.shouldRunCatchUp(task, now))
    }

    @Test
    fun `catch up should be true when last run is before latest due`() {
        val now = ZonedDateTime.of(2026, 2, 23, 8, 0, 0, 0, zone)
        val createdAt = ZonedDateTime.of(2026, 2, 20, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val lastRunAt = ZonedDateTime.of(2026, 2, 21, 7, 0, 0, 0, zone).toInstant().toEpochMilli()
        val task = task(
            scheduleType = ScheduleType.DAILY,
            timeMinutesOfDay = 7 * 60,
            createdAt = createdAt,
            lastRunAt = lastRunAt
        )

        assertTrue(ScheduledPromptTime.shouldRunCatchUp(task, now))
    }

    @Test
    fun `catch up should be false while task is running`() {
        val now = ZonedDateTime.of(2026, 2, 23, 8, 0, 0, 0, zone)
        val createdAt = ZonedDateTime.of(2026, 2, 20, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val task = task(
            scheduleType = ScheduleType.DAILY,
            timeMinutesOfDay = 7 * 60,
            createdAt = createdAt,
            lastRunAt = 0L,
            lastStatus = TaskRunStatus.RUNNING
        )

        assertFalse(ScheduledPromptTime.shouldRunCatchUp(task, now))
    }

    private fun task(
        scheduleType: ScheduleType,
        timeMinutesOfDay: Int,
        dayOfWeek: Int? = null,
        createdAt: Long = 0L,
        lastRunAt: Long = 0L,
        lastStatus: TaskRunStatus = TaskRunStatus.IDLE
    ): ScheduledTask {
        return ScheduledTask(
            id = Uuid.random(),
            scheduleType = scheduleType,
            timeMinutesOfDay = timeMinutesOfDay,
            dayOfWeek = dayOfWeek,
            createdAt = createdAt,
            lastRunAt = lastRunAt,
            lastStatus = lastStatus,
            prompt = "hello"
        )
    }
}
