package com.histmersion.web

import com.histmersion.core.UiNotices.{Board, Counter}

/** The pure parts of the toolbar's notices: which window watches which board, which open windows are
  * marked read and at what, and what a button says. */
class NoticesSuite extends munit.FunSuite:

  private val t1 = "2026-10-11T08:00:00.000000Z"
  private val t2 = "2026-10-11T09:00:00.000000Z"

  private def c(key: String, latest: Option[String], seen: Option[String], count: Option[Int] = None) =
    Counter(key, count, latest, seen, com.histmersion.core.UiNotices.fresh(latest, seen))

  test("each watched window is its own board; the others watch none"):
    assertEquals(Notices.boardOf(WindowKey.Goals), Some(Board.Goals))
    assertEquals(Notices.boardOf(WindowKey.Tasks), Some(Board.Tasks))
    assertEquals(Notices.boardOf(WindowKey.Approvals), Some(Board.Approvals))
    assertEquals(Notices.boardOf(WindowKey.Acquire), Some(Board.Acquire))
    assertEquals(Notices.boardOf(WindowKey.Log), Some(Board.Log))
    assertEquals(Notices.boardOf(WindowKey.Goal("bronze-age")), None, "one goal's window is not the goals list")
    assertEquals(Notices.boardOf(WindowKey.Jobs), None)
    // every board has a button in the toolbar
    val tools = Toolbar.groups.flatMap(_._2).map(_.key)
    Board.values.foreach(b => assert(tools.exists(k => Notices.boardOf(k).contains(b)), b.toString))

  test("an open window with news is marked read at the latest change it showed"):
    val cs = Map("tasks" -> c("tasks", Some(t2), Some(t1)), "goals" -> c("goals", Some(t2), Some(t1)),
      "log" -> c("log", Some(t1), Some(t1)))
    assertEquals(Notices.toMark(List(WindowKey.Tasks, WindowKey.Jobs), cs), List(Board.Tasks -> t2))
    assertEquals(Notices.toMark(List(WindowKey.Log), cs), Nil, "nothing new: nothing to send")
    assertEquals(Notices.toMark(Nil, cs), Nil, "a closed window stays bold")
    assertEquals(Notices.toMark(List(WindowKey.Goal("x")), cs), Nil)
    assertEquals(Notices.toMark(List(WindowKey.Tasks, WindowKey.Tasks), cs), List(Board.Tasks -> t2))

  test("once marked, a board is not new, and an older mark never moves it back"):
    val marked = Notices.seenAt(c("tasks", Some(t2), Some(t1)), t2)
    assertEquals(marked.seen, Some(t2))
    assert(!marked.fresh)
    val kept = Notices.seenAt(c("tasks", Some(t2), Some(t2)), t1)
    assertEquals(kept.seen, Some(t2))
    assert(!kept.fresh)
    // a change after the mark is news again
    assert(Notices.seenAt(c("tasks", Some(t2), None), t1).fresh)

  test("the phone's tools button marks news among the boards it shows"):
    val cs = Map("approvals" -> c("approvals", Some(t2), Some(t1)), "log" -> c("log", Some(t1), Some(t1)))
    assert(Notices.anyFresh(cs, _ => true))
    assert(!Notices.anyFresh(cs, _ != Board.Approvals), "approvals hidden (no agents): no mark")

  test("what a screen reader hears beside the label"):
    assertEquals(Notices.spoken(None), "")
    assertEquals(Notices.spoken(Some(c("tasks", Some(t1), Some(t1), Some(0)))), "")
    assertEquals(Notices.spoken(Some(c("tasks", Some(t2), Some(t1), Some(12)))), "12 open, something new")
    assertEquals(Notices.spoken(Some(c("goals", Some(t2), Some(t1)))), "something new")
