package com.histmersion
package domain

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.HintProposals.*
import com.histmersion.api.automation.Managers.HintEffect
import com.histmersion.api.automation.Managers
import com.histmersion.lint.{tolerated, LintRule}

/** A model's proposed hint action, checked by code before a person sees it (phase 3). */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class HintProposalsSuite extends munit.FunSuite:

  private val items = Set(HintProposals.Item("tin"), HintProposals.Item("copper"), HintProposals.Item("zz-goal"))
  private def chk(
      a: HintAnswer,
      last: HintProposals.Last = HintProposals.Last(false),
      item: Option[HintProposals.Item] = None
  ) =
    check(a, items.contains, item, last)

  test("none (or nothing) proposes nothing; an unknown action is sent back, and on the last attempt proposes nothing"):
    assertEquals(chk(HintAnswer(HintAnswer.Action("none"))), Right((None, Nil)))
    assertEquals(chk(HintAnswer(HintAnswer.Action(""))), Right((None, Nil)))
    assert(
      chk(HintAnswer(HintAnswer.Action("delete"))).left
        .exists(_.head.sentence.contains("unknown hint answer action 'delete'"))
    )
    assert(
      chk(HintAnswer(HintAnswer.Action("delete")), last = HintProposals.Last(true)).exists((e, ws) =>
        e.isEmpty && ws.head.value.startsWith("nothing proposed")
      )
    )

  test(
    "hold: the targets as listed, a date that parses or a source; an unknown target is sent back, dropped on the last attempt"
  ):
    assertEquals(
      chk(
        HintAnswer(
          HintAnswer.Action("Hold"),
          targets = List(HintAnswer.Targets("tin")),
          until = Some(HintAnswer.Until("2026-12-01"))
        )
      ).map(_._1),
      Right(
        Some(
          HintEffect(
            HintEffect.Action("hold"),
            List(HintEffect.Targets("tin")),
            until = Some(HintEffect.Until("2026-12-01"))
          )
        )
      )
    )
    assert(
      chk(HintAnswer(HintAnswer.Action("hold"), targets = List(HintAnswer.Targets("tin")))).left
        .exists(_.exists(_.sentence.contains("give `until`")))
    )
    assert(
      chk(
        HintAnswer(
          HintAnswer.Action("hold"),
          targets = List(HintAnswer.Targets("tin")),
          until = Some(HintAnswer.Until("next week"))
        )
      ).left
        .exists(_.exists(_.sentence.contains("not a date")))
    )
    assert(
      chk(
        HintAnswer(
          HintAnswer.Action("hold"),
          targets = List(HintAnswer.Targets("tin"), HintAnswer.Targets("lead")),
          until = Some(HintAnswer.Until("2026-12-01"))
        )
      ).left
        .exists(_.exists(_.sentence.contains("not among the manager's items: lead")))
    )
    assertEquals(
      chk(
        HintAnswer(
          HintAnswer.Action("hold"),
          targets = List(HintAnswer.Targets("tin"), HintAnswer.Targets("lead")),
          untilSource = Some(HintAnswer.UntilSource("10.1/x"))
        ),
        last = HintProposals.Last(true)
      ),
      Right(
        (
          Some(
            HintEffect(
              HintEffect.Action("hold"),
              List(HintEffect.Targets("tin")),
              untilSource = Some(HintEffect.UntilSource("10.1/x"))
            )
          ),
          List("dropped, not among the manager's items: lead")
        )
      )
    )
    assert(
      chk(
        HintAnswer(
          HintAnswer.Action("hold"),
          targets = List(HintAnswer.Targets("lead")),
          until = Some(HintAnswer.Until("2026-12-01"))
        ),
        last = HintProposals.Last(true)
      ).exists(_._1.isEmpty)
    )

  test("prioritise and skip: targets, a tag, or the item the hint is about; the priority within bounds"):
    assertEquals(
      chk(
        HintAnswer(
          HintAnswer.Action("prioritise"),
          tag = Some(HintAnswer.Tag("hittite")),
          priority = Some(HintEffect.Priority(7))
        )
      ).map(_._1),
      Right(
        Some(
          HintEffect(
            HintEffect.Action("prioritise"),
            Nil,
            Some(HintEffect.Tag("hittite")),
            priority = Some(HintEffect.Priority(7))
          )
        )
      )
    )
    assert(
      chk(
        HintAnswer(
          HintAnswer.Action("prioritise"),
          targets = List(HintAnswer.Targets("tin")),
          priority = Some(HintEffect.Priority(99))
        )
      ).left
        .exists(_.exists(_.sentence.contains("outside")))
    )
    assertEquals(
      chk(HintAnswer(HintAnswer.Action("skip")), item = Some(HintProposals.Item("copper"))).map(_._1),
      Right(Some(HintEffect(HintEffect.Action("skip"), List(HintEffect.Targets("copper")))))
    )
    assert(chk(HintAnswer(HintAnswer.Action("skip"))).left.exists(_.exists(_.sentence.contains("name the items"))))
    assertEquals(
      chk(
        HintAnswer(
          HintAnswer.Action("skip"),
          targets = List(HintAnswer.Targets("copper")),
          priority = Some(HintEffect.Priority(5))
        )
      ).map(_._1.flatMap(_.priority)),
      Right(None),
      "a priority belongs to prioritise only"
    )

  test("config: settings as key=value that parse; none, or a typo, is sent back"):
    assertEquals(
      chk(
        HintAnswer(
          HintAnswer.Action("config"),
          settings = List(HintAnswer.Settings("max-runs=1"), HintAnswer.Settings("cooldownMinutes=60"))
        )
      ).map(_._1.flatMap(_.config)),
      Right(
        Some(
          Managers.ManagerConfig(
            maxRuns = Some(Managers.ManagerConfig.MaxRuns(1)),
            cooldownMinutes = Some(Managers.ManagerConfig.CooldownMinutes(60))
          )
        )
      )
    )
    assert(chk(HintAnswer(HintAnswer.Action("config"))).left.exists(_.head.sentence.contains("give the settings")))
    assert(
      chk(HintAnswer(HintAnswer.Action("config"), settings = List(HintAnswer.Settings("speed=fast")))).left
        .exists(_.head match
          case Problem.Invalid.Undeclared(field, _, at) =>
            field == Problem.Field("speed") && at == Vector(Problem.Field("config"))
          case _ => false)
    )
