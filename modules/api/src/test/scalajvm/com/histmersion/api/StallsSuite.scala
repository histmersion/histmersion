package com.histmersion.api

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Stalls.*
import com.histmersion.api.automation.StallsMarkdown

/** The stall report, pure (#3762 proposal 4): which goals count as stalled, the one reason each stands still (in order:
  * draining, a person, a hold, paused managers, a budget, quota, the queue, a block, running, a failed item, no
  * candidates, idle), the action, the order and the counts.
  */
class StallsSuite extends munit.FunSuite:

  private def f(slug: String = "g", idle: Option[Int] = Some(5)) = Facts(slug, idleDays = idle)

  test("stalled: no progress in N days, or never"):
    assert(stalled(f(idle = Some(3)), 3))
    assert(!stalled(f(idle = Some(2)), 3))
    assert(stalled(f(idle = None), 3))

  test("the first reason that holds is named, with its action"):
    def code(x: Facts) = classify(x)._1
    val base = f()
    assertEquals(code(base.modify(_.draining).setTo(true).modify(_.approvals).setTo(List(1L -> "goal-plan"))), Code.Drain)
    val (c, why, action) =
      classify(base.modify(_.approvals).setTo(List(7L -> "goal-plan", 3L -> "goal-plan", 9L -> "article-publish")))
    assertEquals(c, Code.Person)
    assert(why.contains("3 open approvals (goal-plan 2, article-publish 1; oldest #3)"), why)
    assert(action.contains("decide-all approve --subject g"), action)
    assertEquals(code(base.modify(_.personHeld).setTo(2)), Code.Held)
    assertEquals(code(base.modify(_.paused).setTo(List("seed:g"))), Code.Paused)
    assertEquals(
      code(base.modify(_.waits).setTo(List(SchedWait("blocked", "budget: today's agent spend reached …")))),
      Code.Budget
    )
    assertEquals(
      code(base.modify(_.waits).setTo(List(SchedWait("blocked", "starved: reviewer chain below its plan floor")))),
      Code.Quota
    )
    assertEquals(code(base.modify(_.waits).setTo(List(SchedWait("pool", "plan pool chatgpt-plan …")))), Code.Quota)
    val q = classify(base.modify(_.waits).setTo(List(SchedWait("capacity", "no place free", 12))))
    assert(q._1 == Code.Queue && q._2.contains("#12") && q._3.contains("focus priority g"), q)
    assertEquals(code(base.modify(_.waits).setTo(List(SchedWait("blocked", "its goal's plan comes first")))), Code.Blocked)
    assertEquals(code(base.modify(_.running).setTo(1)), Code.Running)
    assertEquals(code(base.modify(_.lastStop).setTo(Some("stopped: 3 failed runs"))), Code.Failed)
    assertEquals(code(base.modify(_.noCandidates).setTo(true)), Code.NoCandidates)
    assertEquals(
      code(base.modify(_.noCandidates).setTo(true).modify(_.sources).setTo(2)),
      Code.Idle,
      "it has sources: not a candidates problem"
    )
    assert(classify(base)._2.startsWith("nothing open"))
    assert(classify(base.modify(_.open).setTo(2))._2.contains("none due yet"))

  test("the report: only the stalled, the longest idle first (never first of all), counts per reason"):
    val r = report(
      List(f("a", Some(4)), f("b", None), f("c", Some(9)), f("d", Some(1)), f("e", Some(4)).modify(_.running).setTo(1)),
      3,
      "now"
    )
    assertEquals(r.stalled.map(_.slug), List("b", "c", "a", "e"))
    assertEquals(r.goals, 5)
    assertEquals(r.byReason, List(ReasonCount(Code.Idle, 3), ReasonCount(Code.Running, 1)))
    val md = StallsMarkdown.report(r)
    assert(
      md.startsWith("# Stalled goals: 4 of 5 with no progress in 3 day(s)") && md.contains("**b**") && md.contains(
        "never progressed"
      ),
      md
    )
    assert(StallsMarkdown.report(report(Nil, 3, "now")).contains("Every unfinished goal progressed"))
