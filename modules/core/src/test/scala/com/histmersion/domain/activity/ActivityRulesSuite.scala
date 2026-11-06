package com.histmersion.domain.activity

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Activity
import com.histmersion.api.automation.Activity.{Entry, Show}

import java.time.Instant

class ActivityRulesSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-30T12:00:00Z")
  private def at(min: Int) = Entry.At(now.minusSeconds(min * 60L).toString)

  private val entries = List(
    Entry(com.histmersion.api.automation.Activity.Entry.Key("run:r1"), com.histmersion.api.automation.Activity.Entry.Kind("run"), at(1), state = com.histmersion.api.automation.Activity.Entry.State("running"), what = com.histmersion.api.automation.Activity.Entry.What("review-article"), goal = Some("g1"), job = Some("r1")),
    Entry(com.histmersion.api.automation.Activity.Entry.Key("job:j1"), com.histmersion.api.automation.Activity.Entry.Kind("job"), at(2), state = com.histmersion.api.automation.Activity.Entry.State("failed"), what = com.histmersion.api.automation.Activity.Entry.What("media-import"), job = Some("j1"), error = Some("HTTP 500")),
    Entry(com.histmersion.api.automation.Activity.Entry.Key("log:10"), com.histmersion.api.automation.Activity.Entry.Kind("log"), at(3), text = "a run's line", goal = Some("g1"), job = Some("r1"), by = Some("agent")),
    Entry(com.histmersion.api.automation.Activity.Entry.Key("log:11"), com.histmersion.api.automation.Activity.Entry.Kind("log"), at(3), text = "a job's line", job = Some("j1"), by = Some("job")),
    Entry(com.histmersion.api.automation.Activity.Entry.Key("log:12"), com.histmersion.api.automation.Activity.Entry.Kind("log"), at(3), text = "a person's line"),
    Entry(com.histmersion.api.automation.Activity.Entry.Key("decision:5"), com.histmersion.api.automation.Activity.Entry.Kind("decision"), at(4), state = com.histmersion.api.automation.Activity.Entry.State("approved"), what = com.histmersion.api.automation.Activity.Entry.What("edge"), goal = Some("g1")),
    Entry(com.histmersion.api.automation.Activity.Entry.Key("job:j0"), com.histmersion.api.automation.Activity.Entry.Kind("job"), at(60 * 24 * 9), state = com.histmersion.api.automation.Activity.Entry.State("failed"), what = com.histmersion.api.automation.Activity.Entry.What("basemap-fetch"))
  )

  private def keys(p: Activity.Page) = p.entries.map(_.key)

  test("newest first; entries of one instant by key, descending"):
    assertEquals(
      keys(ActivityRules.page(entries, ActivityQuery(), Activity.Counts())),
      List("run:r1", "job:j1", "log:12", "log:11", "log:10", "decision:5", "job:j0")
    )

  test("pages follow on without a gap or a repeat, also inside one instant"):
    def walk(q: ActivityQuery, acc: List[Entry.Key]): List[Entry.Key] =
      val p = ActivityRules.page(entries, q, Activity.Counts())
      p.next match
        case Some(c) => walk(q.modify(_.before).setTo(Some(ActivityCursor.parse(ActivityCursor.Wire(c.value)).fold(p => fail(p.value), identity))), acc ++ keys(p))
        case None    => acc ++ keys(p)
    for size <- 1 to 4 do
      assertEquals(
        walk(ActivityQuery(limit = ActivityQuery.Limit(size)), Nil),
        keys(ActivityRules.page(entries, ActivityQuery(), Activity.Counts())),
        s"page size $size"
      )
    assertEquals(
      ActivityRules.page(entries, ActivityQuery(limit = com.histmersion.domain.activity.ActivityQuery.Limit(7)), Activity.Counts()).next,
      None,
      "the last page has no next"
    )

  test("each chip shows what it names"):
    def shown(s: Show) = keys(ActivityRules.page(entries, ActivityQuery(show = s), Activity.Counts())).toSet
    assertEquals(shown(Show.Running), Set("run:r1"))
    assertEquals(shown(Show.Agents), Set("run:r1", "log:10"))
    assertEquals(shown(Show.Jobs), Set("job:j1", "job:j0", "log:11"))
    assertEquals(shown(Show.Yours), Set("decision:5"))
    assertEquals(shown(Show.Problems), Set("job:j1", "job:j0"))
    assertEquals(shown(Show.Everything).size, entries.size)

  test("a goal's slice and a time bound"):
    assertEquals(
      keys(ActivityRules.page(entries, ActivityQuery(goal = Some(com.histmersion.domain.activity.ActivityQuery.Goal("g1"))), Activity.Counts())),
      List("run:r1", "log:10", "decision:5")
    )
    assertEquals(
      keys(ActivityRules.page(entries, ActivityQuery(since = Some(now.minusSeconds(150))), Activity.Counts())),
      List("run:r1", "job:j1")
    )

  test("the counts: running now, and the problems of the last 7 days only"):
    assertEquals(
      ActivityRules.counts(entries, now.minusSeconds(7 * 86400)),
      Activity.Counts(running = com.histmersion.api.automation.Activity.Counts.Running(1), problemsWeek = com.histmersion.api.automation.Activity.Counts.ProblemsWeek(1))
    )

  test("a cursor round-trips; a bad one is refused"):
    val c = ActivityCursor(Instant.parse("2026-10-30T11:58:00.123456Z"), com.histmersion.domain.activity.ActivityCursor.Key("log:12"))
    assertEquals(ActivityCursor.parse(c.wire), Right(c))
    assert(ActivityCursor.parse(com.histmersion.domain.activity.ActivityCursor.Wire("yesterday~log:1")).isLeft)
    assert(ActivityCursor.parse(com.histmersion.domain.activity.ActivityCursor.Wire("2026-10-30T11:58:00Z")).isLeft)

  test("the query: the chips' words, a time ago, a capped limit; anything else a 400 naming what is allowed"):
    assertEquals(
      ActivityRules.query(Some(com.histmersion.api.automation.Activity.Show.Wire("problems")), Some(com.histmersion.api.automation.ListFilters.TimeBound("7d")), Some(com.histmersion.domain.activity.ActivityQuery.Goal(" g1 ")), Some(com.histmersion.domain.activity.ActivityQuery.Limit(1000)), None, now),
      Right(ActivityQuery(Show.Problems, Some(now.minusSeconds(7 * 86400)), Some(com.histmersion.domain.activity.ActivityQuery.Goal("g1")), ActivityQuery.Limit(Activity.MaxLimit), None))
    )
    assertEquals(ActivityRules.query(None, None, None, None, None, now), Right(ActivityQuery()))
    assert(
      ActivityRules
        .query(Some(com.histmersion.api.automation.Activity.Show.Wire("everything else")), None, None, None, None, now)
        .left
        .exists(_.message.value.contains("running"))
    )
    assert(ActivityRules.query(None, Some(com.histmersion.api.automation.ListFilters.TimeBound("last week")), None, None, None, now).isLeft)
    assert(ActivityRules.query(None, None, None, None, Some(com.histmersion.domain.activity.ActivityCursor.Wire("nope")), now).isLeft)
