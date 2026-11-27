package com.histmersion
package api
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Queue.*
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}

/** A list item's goal and the run or job that wrote it and that ticked it off. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class QueueLinksSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val open =
    QueueItem(
      QueueItem.Id(7),
      QueueItem.List("acquire"),
      QueueItem.Text("Drake 2012"),
      None,
      QueueItem.Status(Open),
      goal = Some(Goal.Slug("collapse")),
      createdBy = Some(QueueItem.CreatedBy("run-1"))
    )

  test("ticking an item off records the run or job that did it; reopening forgets it"):
    val done = applyPatch(open, fulfil(open, Citekey("drake2012"), Some(QueueItem.ClosedBy("job-9"))))
    assertEquals((done.status, done.closedBy, done.createdBy), (Done, Some("job-9"), Some("run-1")))
    assert(done.reason.exists(_.recordsIngestOf(Citekey("drake2012"))))
    assertEquals(
      applyPatch(done, QueuePatch(text = Some(QueueItem.Text("Drake 2012, the book")))).closedBy,
      Some("job-9"),
      "an edit keeps it"
    )
    assertEquals(applyPatch(done, QueuePatch(status = Some(QueueItem.Status(Open)))).closedBy, None)
    assertEquals(
      applyPatch(open, QueuePatch(status = Some(QueueItem.Status(Done)))).closedBy,
      None,
      "ticked off by a person"
    )

  test("a goal is set, kept, and cleared by a blank"):
    assertEquals(applyPatch(open, QueuePatch(goal = Some(Goal.Slug("anatolia")))).goal, Some("anatolia"))
    assertEquals(applyPatch(open, QueuePatch(text = Some(QueueItem.Text("x")))).goal, Some("collapse"))
    assertEquals(applyPatch(open, QueuePatch(goal = Some(Goal.Slug("  ")))).goal, None)

  test("an item's goals: its own first, then the goals that link it, each once, by item"):
    val got = itemGoals(
      List(QueueItem.Id(7) -> GoalRef(Goal.Slug("collapse"), Goal.Title("Collapse"))),
      List(
        QueueItem.Id(9) -> GoalRef(Goal.Slug("anatolia"), Goal.Title("Anatolia")),
        QueueItem.Id(7) -> GoalRef(Goal.Slug("anatolia"), Goal.Title("Anatolia")),
        QueueItem.Id(7) -> GoalRef(Goal.Slug("collapse"), Goal.Title("Collapse"))
      )
    )
    assertEquals(
      got,
      List(
        ItemGoals(
          QueueItem.Id(7),
          List(
            GoalRef(Goal.Slug("collapse"), Goal.Title("Collapse")),
            GoalRef(Goal.Slug("anatolia"), Goal.Title("Anatolia"))
          )
        ),
        ItemGoals(QueueItem.Id(9), List(GoalRef(Goal.Slug("anatolia"), Goal.Title("Anatolia"))))
      )
    )

  test("an item from a client may name its goal and the run writing it; an unknown key is still refused"):
    val in = RawJson.decode[QueueAdd]("""{"text":"a line","goal":"collapse","job":"run-1"}""")
    assertEquals(in.map(a => (a.goal, a.job)), Right((Some("collapse"), Some("run-1"))))
    assert(RawJson.decode[QueueAdd]("""{"text":"a line","run":"x"}""").isLeft)
