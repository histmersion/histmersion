package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Stalls.*
import com.histmersion.api.automation.StallsMarkdown
import com.histmersion.api.automation.Stalls
import com.histmersion.lint.{tolerated, LintRule}

/** The stall report, pure (#3762 proposal 4): which goals count as stalled, the one reason each stands still (in order:
  * draining, a person, a hold, paused managers, a budget, quota, the queue, a block, running, a failed item, no
  * candidates, idle), the action, the order and the counts.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class StallsSuite extends munit.FunSuite:

  private def f(slug: Facts.Slug = Facts.Slug("g"), idle: Option[Facts.IdleDays] = Some(Facts.IdleDays(5))) =
    Facts(slug, idleDays = idle)

  test("stalled: no progress in N days, or never"):
    assert(stalled(f(idle = Some(Facts.IdleDays(3))), Stalls.StallReport.Days(3)))
    assert(!stalled(f(idle = Some(Facts.IdleDays(2))), Stalls.StallReport.Days(3)))
    assert(stalled(f(idle = None), Stalls.StallReport.Days(3)))

  test("the first reason that holds is named, with its action"):
    def code(x: Facts) = classify(x)._1
    val base = f()
    assertEquals(
      code(
        base
          .modify(_.draining)
          .setTo(Facts.Draining.Draining)
          .modify(_.approvals)
          .setTo(List(Facts.ApprovalId(1L) -> Facts.ApprovalKind("goal-plan")))
      ),
      Code.Drain
    )
    val (c, why, action) =
      classify(
        base
          .modify(_.approvals)
          .setTo(
            List(
              Facts.ApprovalId(7L) -> Facts.ApprovalKind("goal-plan"),
              Facts.ApprovalId(3L) -> Facts.ApprovalKind("goal-plan"),
              Facts.ApprovalId(9L) -> Facts.ApprovalKind("article-publish")
            )
          )
      )
    assertEquals(c, Code.Person)
    assert(why.value.contains("3 open approvals (goal-plan 2, article-publish 1; oldest #3)"), why)
    assert(action.value.contains("decide-all approve --subject g"), action)
    assertEquals(code(base.modify(_.personHeld).setTo(Facts.PersonHeld(2))), Code.Held)
    assertEquals(code(base.modify(_.paused).setTo(List(Facts.Paused("seed:g")))), Code.Paused)
    assertEquals(
      code(
        base
          .modify(_.waits)
          .setTo(
            List(
              SchedWait(
                SchedWait.Code("blocked"),
                SchedWait.Why.Budget(SchedWait.Detail("today's agent spend reached …"))
              )
            )
          )
      ),
      Code.Budget
    )
    assertEquals(
      code(
        base
          .modify(_.waits)
          .setTo(
            List(
              SchedWait(
                SchedWait.Code("blocked"),
                SchedWait.Why.Starved(SchedWait.Detail("reviewer chain below its plan floor"))
              )
            )
          )
      ),
      Code.Quota
    )
    assertEquals(
      code(
        base
          .modify(_.waits)
          .setTo(
            List(SchedWait(SchedWait.Code("pool"), SchedWait.Why.Other(SchedWait.Detail("plan pool chatgpt-plan …"))))
          )
      ),
      Code.Quota
    )
    val q = classify(
      base
        .modify(_.waits)
        .setTo(
          List(
            SchedWait(
              SchedWait.Code("capacity"),
              SchedWait.Why.Other(SchedWait.Detail("no place free")),
              SchedWait.Position(12)
            )
          )
        )
    )
    assert(q._1 == Stall.Code(Code.Queue) && q._2.value.contains("#12") && q._3.value.contains("focus priority g"), q)
    assertEquals(
      code(
        base
          .modify(_.waits)
          .setTo(
            List(
              SchedWait(SchedWait.Code("blocked"), SchedWait.Why.Other(SchedWait.Detail("its goal's plan comes first")))
            )
          )
      ),
      Code.Blocked
    )
    assertEquals(code(base.modify(_.running).setTo(Facts.Running(1))), Code.Running)
    assertEquals(code(base.modify(_.lastStop).setTo(Some(Facts.LastStop("stopped: 3 failed runs")))), Code.Failed)
    assertEquals(code(base.modify(_.noCandidates).setTo(Facts.NoCandidates.NoCandidates)), Code.NoCandidates)
    assertEquals(
      code(
        base.modify(_.noCandidates).setTo(Facts.NoCandidates.NoCandidates).modify(_.sources).setTo(Facts.Sources(2))
      ),
      Code.Idle,
      "it has sources: not a candidates problem"
    )
    assert(classify(base)._2.value.startsWith("nothing open"))
    assert(classify(base.modify(_.open).setTo(Facts.Open(2)))._2.value.contains("none due yet"))

  test("the report: only the stalled, the longest idle first (never first of all), counts per reason"):
    val r = report(
      List(
        f(Facts.Slug("a"), Some(Facts.IdleDays(4))),
        f(Facts.Slug("b"), None),
        f(Facts.Slug("c"), Some(Facts.IdleDays(9))),
        f(Facts.Slug("d"), Some(Facts.IdleDays(1))),
        f(Facts.Slug("e"), Some(Facts.IdleDays(4))).modify(_.running).setTo(Facts.Running(1))
      ),
      Stalls.StallReport.Days(3),
      Stalls.StallReport.At("now")
    )
    assertEquals(r.stalled.map(_.slug), List("b", "c", "a", "e"))
    assertEquals(r.goals, 5)
    assertEquals(
      r.byReason,
      List(
        ReasonCount(ReasonCount.Code(Code.Idle), Stalls.ReasonCount.Count(3)),
        ReasonCount(ReasonCount.Code(Code.Running), Stalls.ReasonCount.Count(1))
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
        .report(report(Nil, Stalls.StallReport.Days(3), Stalls.StallReport.At("now")))
        .contains("Every unfinished goal progressed")
    )
