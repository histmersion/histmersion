package com.histmersion.domain

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.HintProposals.*
import com.histmersion.api.automation.Managers.HintEffect
import com.histmersion.api.automation.Managers

/** A model's proposed hint action, checked by code before a person sees it (phase 3). */
class HintProposalsSuite extends munit.FunSuite:

  private val items = Set("tin", "copper", "zz-goal")
  private def chk(a: HintAnswer, last: Boolean = false, item: Option[String] = None) =
    check(a, items.contains, item, last)

  test("none (or nothing) proposes nothing; an unknown action is sent back, and on the last attempt proposes nothing"):
    assertEquals(chk(HintAnswer("none")), Right((None, Nil)))
    assertEquals(chk(HintAnswer("")), Right((None, Nil)))
    assert(chk(HintAnswer("delete")).left.exists(_.head.contains("is not an action")))
    assert(
      chk(HintAnswer("delete"), last = true).exists((e, ws) => e.isEmpty && ws.head.startsWith("nothing proposed"))
    )

  test(
    "hold: the targets as listed, a date that parses or a source; an unknown target is sent back, dropped on the last attempt"
  ):
    assertEquals(
      chk(HintAnswer("Hold", targets = List("tin"), until = Some("2026-12-01"))).map(_._1),
      Right(Some(HintEffect("hold", List("tin"), until = Some("2026-12-01"))))
    )
    assert(chk(HintAnswer("hold", targets = List("tin"))).left.exists(_.exists(_.contains("give `until`"))))
    assert(
      chk(HintAnswer("hold", targets = List("tin"), until = Some("next week"))).left
        .exists(_.exists(_.contains("not a date")))
    )
    assert(
      chk(HintAnswer("hold", targets = List("tin", "lead"), until = Some("2026-12-01"))).left
        .exists(_.exists(_.contains("not among the manager's items: lead")))
    )
    assertEquals(
      chk(HintAnswer("hold", targets = List("tin", "lead"), untilSource = Some("10.1/x")), last = true),
      Right(
        (
          Some(HintEffect("hold", List("tin"), untilSource = Some("10.1/x"))),
          List("dropped, not among the manager's items: lead")
        )
      )
    )
    assert(
      chk(HintAnswer("hold", targets = List("lead"), until = Some("2026-12-01")), last = true).exists(_._1.isEmpty)
    )

  test("prioritise and skip: targets, a tag, or the item the hint is about; the priority within bounds"):
    assertEquals(
      chk(HintAnswer("prioritise", tag = Some("hittite"), priority = Some(7))).map(_._1),
      Right(Some(HintEffect("prioritise", Nil, Some("hittite"), priority = Some(7))))
    )
    assert(
      chk(HintAnswer("prioritise", targets = List("tin"), priority = Some(99))).left
        .exists(_.exists(_.contains("outside")))
    )
    assertEquals(
      chk(HintAnswer("skip"), item = Some("copper")).map(_._1),
      Right(Some(HintEffect("skip", List("copper"))))
    )
    assert(chk(HintAnswer("skip")).left.exists(_.exists(_.contains("name the items"))))
    assertEquals(
      chk(HintAnswer("skip", targets = List("copper"), priority = Some(5))).map(_._1.flatMap(_.priority)),
      Right(None),
      "a priority belongs to prioritise only"
    )

  test("config: settings as key=value that parse; none, or a typo, is sent back"):
    assertEquals(
      chk(HintAnswer("config", settings = List("max-runs=1", "cooldownMinutes=60"))).map(_._1.flatMap(_.config)),
      Right(Some(Managers.ManagerConfig(maxRuns = Some(1), cooldownMinutes = Some(60))))
    )
    assert(chk(HintAnswer("config")).left.exists(_.head.contains("give the settings")))
    assert(
      chk(HintAnswer("config", settings = List("speed=fast"))).left.exists(_.head.contains("unknown manager setting"))
    )
