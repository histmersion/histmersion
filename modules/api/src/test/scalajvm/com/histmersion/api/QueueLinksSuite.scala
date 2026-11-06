package com.histmersion.api

import com.histmersion.api.automation.Queue.*

/** A list item's goal and the run or job that wrote it and that ticked it off. */
class QueueLinksSuite extends munit.FunSuite:

  private val open =
    QueueItem(com.histmersion.api.automation.Queue.QueueItem.Id(7), com.histmersion.api.automation.Queue.QueueItem.List("acquire"), com.histmersion.api.automation.Queue.QueueItem.Text("Drake 2012"), None, Open, goal = Some("collapse"), createdBy = Some("run-1"))

  test("ticking an item off records the run or job that did it; reopening forgets it"):
    val done = applyPatch(open, fulfil(open, com.histmersion.api.library.Citekey("drake2012"), Some(com.histmersion.api.automation.Queue.QueueItem.ClosedBy("job-9"))))
    assertEquals((done.status, done.closedBy, done.createdBy), (Done, Some("job-9"), Some("run-1")))
    assert(done.reason.exists(_.contains("[[drake2012]]")))
    assertEquals(
      applyPatch(done, QueuePatch(text = Some(com.histmersion.api.automation.Queue.QueueItem.Text("Drake 2012, the book")))).closedBy,
      Some("job-9"),
      "an edit keeps it"
    )
    assertEquals(applyPatch(done, QueuePatch(status = Some(Open))).closedBy, None)
    assertEquals(applyPatch(open, QueuePatch(status = Some(Done))).closedBy, None, "ticked off by a person")

  test("a goal is set, kept, and cleared by a blank"):
    assertEquals(applyPatch(open, QueuePatch(goal = Some(com.histmersion.api.automation.Goal.Slug("anatolia")))).goal, Some("anatolia"))
    assertEquals(applyPatch(open, QueuePatch(text = Some(com.histmersion.api.automation.Queue.QueueItem.Text("x")))).goal, Some("collapse"))
    assertEquals(applyPatch(open, QueuePatch(goal = Some(com.histmersion.api.automation.Goal.Slug("  ")))).goal, None)

  test("an item's goals: its own first, then the goals that link it, each once, by item"):
    val got = itemGoals(
      List(7 -> GoalRef(com.histmersion.api.automation.Goal.Slug("collapse"), com.histmersion.api.automation.Goal.Title("Collapse"))),
      List(
        9 -> GoalRef(com.histmersion.api.automation.Goal.Slug("anatolia"), com.histmersion.api.automation.Goal.Title("Anatolia")),
        7 -> GoalRef(com.histmersion.api.automation.Goal.Slug("anatolia"), com.histmersion.api.automation.Goal.Title("Anatolia")),
        7 -> GoalRef(com.histmersion.api.automation.Goal.Slug("collapse"), com.histmersion.api.automation.Goal.Title("Collapse"))
      )
    )
    assertEquals(
      got,
      List(
        ItemGoals(com.histmersion.api.automation.Queue.QueueItem.Id(7), List(GoalRef(com.histmersion.api.automation.Goal.Slug("collapse"), com.histmersion.api.automation.Goal.Title("Collapse")), GoalRef(com.histmersion.api.automation.Goal.Slug("anatolia"), com.histmersion.api.automation.Goal.Title("Anatolia")))),
        ItemGoals(com.histmersion.api.automation.Queue.QueueItem.Id(9), List(GoalRef(com.histmersion.api.automation.Goal.Slug("anatolia"), com.histmersion.api.automation.Goal.Title("Anatolia"))))
      )
    )

  test("an item from a client may name its goal and the run writing it; an unknown key is still refused"):
    val in = io.circe.parser.decode[QueueAdd]("""{"text":"a line","goal":"collapse","job":"run-1"}""")
    assertEquals(in.map(a => (a.goal, a.job)), Right((Some("collapse"), Some("run-1"))))
    assert(io.circe.parser.decode[QueueAdd]("""{"text":"a line","run":"x"}""").isLeft)
