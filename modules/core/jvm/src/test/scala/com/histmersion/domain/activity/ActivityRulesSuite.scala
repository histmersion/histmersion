package com.histmersion.domain.activity

import com.histmersion.api.Activity
import com.histmersion.api.Activity.{Entry, Show}

import java.time.Instant

class ActivityRulesSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-30T12:00:00Z")
  private def at(min: Int) = now.minusSeconds(min * 60L).toString

  private val entries = List(
    Entry("run:r1", "run", at(1), state = "running", what = "review-article", goal = Some("g1"), job = Some("r1")),
    Entry("job:j1", "job", at(2), state = "failed", what = "media-import", job = Some("j1"), error = Some("HTTP 500")),
    Entry("log:10", "log", at(3), text = "a run's line", goal = Some("g1"), job = Some("r1"), by = Some("agent")),
    Entry("log:11", "log", at(3), text = "a job's line", job = Some("j1"), by = Some("job")),
    Entry("log:12", "log", at(3), text = "a person's line"),
    Entry("decision:5", "decision", at(4), state = "approved", what = "edge", goal = Some("g1")),
    Entry("job:j0", "job", at(60 * 24 * 9), state = "failed", what = "basemap-fetch")
  )

  private def keys(p: Activity.Page) = p.entries.map(_.key)

  test("newest first; entries of one instant by key, descending"):
    assertEquals(
      keys(ActivityRules.page(entries, ActivityQuery(), Activity.Counts())),
      List("run:r1", "job:j1", "log:12", "log:11", "log:10", "decision:5", "job:j0")
    )

  test("pages follow on without a gap or a repeat, also inside one instant"):
    def walk(q: ActivityQuery, acc: List[String]): List[String] =
      val p = ActivityRules.page(entries, q, Activity.Counts())
      p.next match
        case Some(c) => walk(q.copy(before = Some(ActivityCursor.parse(c).fold(fail(_), identity))), acc ++ keys(p))
        case None    => acc ++ keys(p)
    for size <- 1 to 4 do
      assertEquals(
        walk(ActivityQuery(limit = size), Nil),
        keys(ActivityRules.page(entries, ActivityQuery(), Activity.Counts())),
        s"page size $size"
      )
    assertEquals(
      ActivityRules.page(entries, ActivityQuery(limit = 7), Activity.Counts()).next,
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
      keys(ActivityRules.page(entries, ActivityQuery(goal = Some("g1")), Activity.Counts())),
      List("run:r1", "log:10", "decision:5")
    )
    assertEquals(
      keys(ActivityRules.page(entries, ActivityQuery(since = Some(now.minusSeconds(150))), Activity.Counts())),
      List("run:r1", "job:j1")
    )

  test("the counts: running now, and the problems of the last 7 days only"):
    assertEquals(
      ActivityRules.counts(entries, now.minusSeconds(7 * 86400)),
      Activity.Counts(running = 1, problemsWeek = 1)
    )

  test("a cursor round-trips; a bad one is refused"):
    val c = ActivityCursor(Instant.parse("2026-10-30T11:58:00.123456Z"), "log:12")
    assertEquals(ActivityCursor.parse(c.wire), Right(c))
    assert(ActivityCursor.parse("yesterday~log:1").isLeft)
    assert(ActivityCursor.parse("2026-10-30T11:58:00Z").isLeft)

  test("the query: the chips' words, a time ago, a capped limit; anything else a 400 naming what is allowed"):
    assertEquals(
      ActivityRules.query(Some("problems"), Some("7d"), Some(" g1 "), Some(1000), None, now),
      Right(ActivityQuery(Show.Problems, Some(now.minusSeconds(7 * 86400)), Some("g1"), Activity.MaxLimit, None))
    )
    assertEquals(ActivityRules.query(None, None, None, None, None, now), Right(ActivityQuery()))
    assert(
      ActivityRules
        .query(Some("everything else"), None, None, None, None, now)
        .left
        .exists(_.message.contains("running"))
    )
    assert(ActivityRules.query(None, Some("last week"), None, None, None, now).isLeft)
    assert(ActivityRules.query(None, None, None, None, Some("nope"), now).isLeft)
