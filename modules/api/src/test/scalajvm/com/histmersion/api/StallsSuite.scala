package com.histmersion
package api

import com.histmersion.api.automation.Stall

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.StallsMarkdown
import com.histmersion.lint.{tolerated, LintRule}

/** The stall report, pure (#3762 proposal 4): which goals count as stalled, the one reason each stands still (in order:
  * draining, a person, a hold, paused managers, a budget, quota, the queue, a block, running, a failed item, no
  * candidates, idle), the action, the order and the counts.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class StallsSuite extends munit.FunSuite:

  private def f(
      slug: Stall.Facts.Slug = Stall.Facts.Slug("g"),
      idle: Option[Stall.Facts.IdleDays] = Some(Stall.Facts.IdleDays(5))
  ) =
    Stall.Facts(slug, idleDays = idle)

  test("stalled: no progress in N days, or never"):
    assert(Stall.stalled(f(idle = Some(Stall.Facts.IdleDays(3))), Stall.StallReport.Days(3)))
    assert(!Stall.stalled(f(idle = Some(Stall.Facts.IdleDays(2))), Stall.StallReport.Days(3)))
    assert(Stall.stalled(f(idle = None), Stall.StallReport.Days(3)))

  test("the first reason that holds is named, with its action"):
    def code(x: Stall.Facts) = Stall.classify(x)._1
    val base = f()
    assertEquals(
      code(
        base
          .modify(_.draining)
          .setTo(Stall.Facts.Draining.Draining)
          .modify(_.approvals)
          .setTo(List(Stall.Facts.ApprovalId(1L) -> Stall.Facts.ApprovalKind("goal-plan")))
      ),
      Stall.Code.Drain
    )
    val (c, why, action) =
      Stall.classify(
        base
          .modify(_.approvals)
          .setTo(
            List(
              Stall.Facts.ApprovalId(7L) -> Stall.Facts.ApprovalKind("goal-plan"),
              Stall.Facts.ApprovalId(3L) -> Stall.Facts.ApprovalKind("goal-plan"),
              Stall.Facts.ApprovalId(9L) -> Stall.Facts.ApprovalKind("article-publish")
            )
          )
      )
    assertEquals(c, Stall.Code.Person)
    assert(why.value.contains("3 open approvals (goal-plan 2, article-publish 1; oldest #3)"), why)
    assert(action.value.contains("decide-all approve --subject g"), action)
    assertEquals(code(base.modify(_.personHeld).setTo(Stall.Facts.PersonHeld(2))), Stall.Code.Held)
    assertEquals(code(base.modify(_.paused).setTo(List(Stall.Facts.Paused("seed:g")))), Stall.Code.Paused)
    assertEquals(
      code(
        base
          .modify(_.waits)
          .setTo(
            List(
              Stall.SchedWait(
                Stall.SchedWait.Code("blocked"),
                Stall.SchedWait.Why.Budget(Stall.SchedWait.Detail("today's agent spend reached …"))
              )
            )
          )
      ),
      Stall.Code.Budget
    )
    assertEquals(
      code(
        base
          .modify(_.waits)
          .setTo(
            List(
              Stall.SchedWait(
                Stall.SchedWait.Code("blocked"),
                Stall.SchedWait.Why.Starved(Stall.SchedWait.Detail("reviewer chain below its plan floor"))
              )
            )
          )
      ),
      Stall.Code.Quota
    )
    assertEquals(
      code(
        base
          .modify(_.waits)
          .setTo(
            List(
              Stall.SchedWait(
                Stall.SchedWait.Code("pool"),
                Stall.SchedWait.Why.Other(Stall.SchedWait.Detail("plan pool chatgpt-plan …"))
              )
            )
          )
      ),
      Stall.Code.Quota
    )
    val q = Stall.classify(
      base
        .modify(_.waits)
        .setTo(
          List(
            Stall.SchedWait(
              Stall.SchedWait.Code("capacity"),
              Stall.SchedWait.Why.Other(Stall.SchedWait.Detail("no place free")),
              Stall.SchedWait.Position(12)
            )
          )
        )
    )
    assert(
      q._1 == Stall.Code(Stall.Code.Queue) && q._2.value.contains("#12") && q._3.value.contains("focus priority g"),
      q
    )
    assertEquals(
      code(
        base
          .modify(_.waits)
          .setTo(
            List(
              Stall.SchedWait(
                Stall.SchedWait.Code("blocked"),
                Stall.SchedWait.Why.Other(Stall.SchedWait.Detail("its goal's plan comes first"))
              )
            )
          )
      ),
      Stall.Code.Blocked
    )
    assertEquals(code(base.modify(_.running).setTo(Stall.Facts.Running(1))), Stall.Code.Running)
    assertEquals(
      code(base.modify(_.lastStop).setTo(Some(Stall.Facts.LastStop("stopped: 3 failed runs")))),
      Stall.Code.Failed
    )
    assertEquals(
      code(base.modify(_.noCandidates).setTo(Stall.Facts.NoCandidates.NoCandidates)),
      Stall.Code.NoCandidates
    )
    assertEquals(
      code(
        base
          .modify(_.noCandidates)
          .setTo(Stall.Facts.NoCandidates.NoCandidates)
          .modify(_.sources)
          .setTo(Stall.Facts.Sources(2))
      ),
      Stall.Code.Idle,
      "it has sources: not a candidates problem"
    )
    assert(Stall.classify(base)._2.value.startsWith("nothing open"))
    assert(Stall.classify(base.modify(_.open).setTo(Stall.Facts.Open(2)))._2.value.contains("none due yet"))

  test("the report: only the stalled, the longest idle first (never first of all), counts per reason"):
    val r = Stall.report(
      List(
        f(Stall.Facts.Slug("a"), Some(Stall.Facts.IdleDays(4))),
        f(Stall.Facts.Slug("b"), None),
        f(Stall.Facts.Slug("c"), Some(Stall.Facts.IdleDays(9))),
        f(Stall.Facts.Slug("d"), Some(Stall.Facts.IdleDays(1))),
        f(Stall.Facts.Slug("e"), Some(Stall.Facts.IdleDays(4))).modify(_.running).setTo(Stall.Facts.Running(1))
      ),
      Stall.StallReport.Days(3),
      Stall.StallReport.At("now")
    )
    assertEquals(r.stalled.map(_.slug), List("b", "c", "a", "e"))
    assertEquals(r.goals, 5)
    assertEquals(
      r.byReason,
      List(
        Stall.ReasonCount(Stall.ReasonCount.Code(Stall.Code.Idle), Stall.ReasonCount.Count(3)),
        Stall.ReasonCount(Stall.ReasonCount.Code(Stall.Code.Running), Stall.ReasonCount.Count(1))
      )
    )
    val md = StallsMarkdown.report(r)
    assert(
      md.startsWith("# Stalled goals: 4 of 5 with no progress in 3 day(s)") && md.contains("**b**") && md.contains(
        "never progressed"
      ),
      md
    )
    assert(
      StallsMarkdown
        .report(Stall.report(Nil, Stall.StallReport.Days(3), Stall.StallReport.At("now")))
        .contains("Every unfinished goal progressed")
    )
