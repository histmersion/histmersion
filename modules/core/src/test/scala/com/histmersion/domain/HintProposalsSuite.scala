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
    assertEquals(chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("none"))), Right((None, Nil)))
    assertEquals(chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action(""))), Right((None, Nil)))
    assert(chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("delete"))).left.exists(_.head.contains("is not an action")))
    assert(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("delete")), last = true).exists((e, ws) => e.isEmpty && ws.head.startsWith("nothing proposed"))
    )

  test(
    "hold: the targets as listed, a date that parses or a source; an unknown target is sent back, dropped on the last attempt"
  ):
    assertEquals(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("Hold"), targets = List("tin"), until = Some("2026-12-01"))).map(_._1),
      Right(Some(HintEffect(com.histmersion.api.automation.Managers.HintEffect.Action("hold"), List(com.histmersion.api.automation.Managers.HintEffect.Targets("tin")), until = Some("2026-12-01"))))
    )
    assert(chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("hold"), targets = List("tin"))).left.exists(_.exists(_.contains("give `until`"))))
    assert(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("hold"), targets = List("tin"), until = Some("next week"))).left
        .exists(_.exists(_.contains("not a date")))
    )
    assert(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("hold"), targets = List("tin", "lead"), until = Some("2026-12-01"))).left
        .exists(_.exists(_.contains("not among the manager's items: lead")))
    )
    assertEquals(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("hold"), targets = List("tin", "lead"), untilSource = Some("10.1/x")), last = true),
      Right(
        (
          Some(HintEffect(com.histmersion.api.automation.Managers.HintEffect.Action("hold"), List(com.histmersion.api.automation.Managers.HintEffect.Targets("tin")), untilSource = Some("10.1/x"))),
          List("dropped, not among the manager's items: lead")
        )
      )
    )
    assert(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("hold"), targets = List("lead"), until = Some("2026-12-01")), last = true).exists(_._1.isEmpty)
    )

  test("prioritise and skip: targets, a tag, or the item the hint is about; the priority within bounds"):
    assertEquals(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("prioritise"), tag = Some("hittite"), priority = Some(7))).map(_._1),
      Right(Some(HintEffect(com.histmersion.api.automation.Managers.HintEffect.Action("prioritise"), Nil, Some(com.histmersion.api.automation.Managers.HintEffect.Tag("hittite")), priority = Some(7))))
    )
    assert(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("prioritise"), targets = List("tin"), priority = Some(99))).left
        .exists(_.exists(_.contains("outside")))
    )
    assertEquals(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("skip")), item = Some("copper")).map(_._1),
      Right(Some(HintEffect(com.histmersion.api.automation.Managers.HintEffect.Action("skip"), List(com.histmersion.api.automation.Managers.HintEffect.Targets("copper")))))
    )
    assert(chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("skip"))).left.exists(_.exists(_.contains("name the items"))))
    assertEquals(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("skip"), targets = List("copper"), priority = Some(5))).map(_._1.flatMap(_.priority)),
      Right(None),
      "a priority belongs to prioritise only"
    )

  test("config: settings as key=value that parse; none, or a typo, is sent back"):
    assertEquals(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("config"), settings = List("max-runs=1", "cooldownMinutes=60"))).map(_._1.flatMap(_.config)),
      Right(Some(Managers.ManagerConfig(maxRuns = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxRuns(1)), cooldownMinutes = Some(60))))
    )
    assert(chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("config"))).left.exists(_.head.contains("give the settings")))
    assert(
      chk(HintAnswer(com.histmersion.domain.HintProposals.HintAnswer.Action("config"), settings = List("speed=fast"))).left.exists(_.head.contains("unknown manager setting"))
    )
