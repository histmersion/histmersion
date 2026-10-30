package com.histmersion.api

import com.histmersion.api.Queue.*

/** A list item's goal and the run or job that wrote it and that ticked it off. */
class QueueLinksSuite extends munit.FunSuite:

  private val open =
    QueueItem(7, "acquire", "Drake 2012", None, Open, goal = Some("collapse"), createdBy = Some("run-1"))

  test("ticking an item off records the run or job that did it; reopening forgets it"):
    val done = applyPatch(open, fulfil(open, "drake2012", Some("job-9")))
    assertEquals((done.status, done.closedBy, done.createdBy), (Done, Some("job-9"), Some("run-1")))
    assert(done.reason.exists(_.contains("[[drake2012]]")))
    assertEquals(
      applyPatch(done, QueuePatch(text = Some("Drake 2012, the book"))).closedBy,
      Some("job-9"),
      "an edit keeps it"
    )
    assertEquals(applyPatch(done, QueuePatch(status = Some(Open))).closedBy, None)
    assertEquals(applyPatch(open, QueuePatch(status = Some(Done))).closedBy, None, "ticked off by a person")

  test("a goal is set, kept, and cleared by a blank"):
    assertEquals(applyPatch(open, QueuePatch(goal = Some("anatolia"))).goal, Some("anatolia"))
    assertEquals(applyPatch(open, QueuePatch(text = Some("x"))).goal, Some("collapse"))
    assertEquals(applyPatch(open, QueuePatch(goal = Some("  "))).goal, None)

  test("an item's goals: its own first, then the goals that link it, each once, by item"):
    val got = itemGoals(
      List(7 -> GoalRef("collapse", "Collapse")),
      List(
        9 -> GoalRef("anatolia", "Anatolia"),
        7 -> GoalRef("anatolia", "Anatolia"),
        7 -> GoalRef("collapse", "Collapse")
      )
    )
    assertEquals(
      got,
      List(
        ItemGoals(7, List(GoalRef("collapse", "Collapse"), GoalRef("anatolia", "Anatolia"))),
        ItemGoals(9, List(GoalRef("anatolia", "Anatolia")))
      )
    )

  test("an item from a client may name its goal and the run writing it; an unknown key is still refused"):
    val in = io.circe.parser.decode[QueueAdd]("""{"text":"a line","goal":"collapse","job":"run-1"}""")
    assertEquals(in.map(a => (a.goal, a.job)), Right((Some("collapse"), Some("run-1"))))
    assert(io.circe.parser.decode[QueueAdd]("""{"text":"a line","run":"x"}""").isLeft)
