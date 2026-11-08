package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import java.time.Instant

/** The five-field cron of a goal's schedules: parsing, the next firing in UTC, the day-of-month / day-of-week rule,
  * shortcuts, and what is refused.
  */
class CronSuite extends munit.FunSuite:

  private def at(s: String) = Instant.parse(s)
  private def next(expr: String, after: String): Option[Instant] =
    Cron.parse(Cron.Text(expr)).toOption.get.next(at(after))

  test("the next firing is strictly after the given instant, in UTC"):
    assertEquals(next("30 2 * * *", "2026-10-04T01:00:00Z"), Some(at("2026-10-04T02:30:00Z")))
    assertEquals(
      next("30 2 * * *", "2026-10-04T02:30:00Z"),
      Some(at("2026-10-05T02:30:00Z")),
      "not the same minute again"
    )
    assertEquals(next("30 2 * * *", "2026-10-04T02:29:59Z"), Some(at("2026-10-04T02:30:00Z")))

  test("steps, ranges and lists"):
    assertEquals(next("*/15 * * * *", "2026-10-04T10:07:00Z"), Some(at("2026-10-04T10:15:00Z")))
    assertEquals(next("0 9-17/4 * * *", "2026-10-04T10:00:00Z"), Some(at("2026-10-04T13:00:00Z")))
    assertEquals(next("0 6,18 * * *", "2026-10-04T07:00:00Z"), Some(at("2026-10-04T18:00:00Z")))
    assertEquals(next("5/20 * * * *", "2026-10-04T10:06:00Z"), Some(at("2026-10-04T10:25:00Z")))

  test("day of week: 0 and 7 are Sunday; restricted with day of month, EITHER matches"):
    // 2026-10-04 is a Sunday
    assertEquals(next("0 0 * * 0", "2026-10-03T12:00:00Z"), Some(at("2026-10-04T00:00:00Z")))
    assertEquals(next("0 0 * * 7", "2026-10-03T12:00:00Z"), Some(at("2026-10-04T00:00:00Z")))
    assertEquals(next("0 0 * * 1", "2026-10-04T12:00:00Z"), Some(at("2026-10-05T00:00:00Z")))
    // the 10th OR any Monday: Monday the 5th comes first
    assertEquals(next("0 0 10 * 1", "2026-10-04T12:00:00Z"), Some(at("2026-10-05T00:00:00Z")))

  test("shortcuts, months and leap days"):
    assertEquals(next("@daily", "2026-10-04T12:00:00Z"), Some(at("2026-10-05T00:00:00Z")))
    assertEquals(next("@monthly", "2026-10-04T12:00:00Z"), Some(at("2026-11-01T00:00:00Z")))
    assertEquals(next("@weekly", "2026-10-04T12:00:00Z"), Some(at("2026-10-11T00:00:00Z")))
    assertEquals(next("0 0 1 1 *", "2026-10-04T12:00:00Z"), Some(at("2027-01-01T00:00:00Z")))
    assertEquals(next("0 0 29 2 *", "2026-10-04T12:00:00Z"), Some(at("2028-02-29T00:00:00Z")))

  test("refused: the wrong number of fields, out of range, backwards, a bad step, a date that never comes"):
    assert(Cron.parse(Cron.Text("* * * *")).left.exists(_.value.contains("five fields")))
    assert(Cron.parse(Cron.Text("60 * * * *")).left.exists(_.value.contains("minute")))
    assert(Cron.parse(Cron.Text("0 24 * * *")).left.exists(_.value.contains("hour")))
    assert(Cron.parse(Cron.Text("0 0 5-2 * *")).left.exists(_.value.contains("backwards")))
    assert(Cron.parse(Cron.Text("*/0 * * * *")).left.exists(_.value.contains("step")))
    assert(Cron.parse(Cron.Text("0 0 30 2 *")).left.exists(_.value.contains("never fires")))
    assert(Cron.parse(Cron.Text("")).isLeft)

  test("the shortest gap between firings (how a too-frequent schedule is refused)"):
    val from = at("2026-10-04T00:00:00Z")
    assertEquals(Cron.parse(Cron.Text("*/15 * * * *")).toOption.get.shortestGapMinutes(from), Some(15L))
    assertEquals(Cron.parse(Cron.Text("@daily")).toOption.get.shortestGapMinutes(from), Some(1440L))
    assertEquals(Cron.parse(Cron.Text("0 6,7 * * *")).toOption.get.shortestGapMinutes(from), Some(60L))
