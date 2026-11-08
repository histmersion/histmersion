package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.instance.UiNotices.{Board, Counter}
import com.histmersion.api.instance.UiNotices

/** The pure parts of the toolbar's notices: which window watches which board, which open windows are marked read and at
  * what, and what a button says.
  */
class NoticesSuite extends munit.FunSuite:

  private val t1Latest = Counter.Latest("2026-10-11T08:00:00.000000Z")
  private val t2Latest = Counter.Latest("2026-10-11T09:00:00.000000Z")
  private val t1Seen = Counter.Seen("2026-10-11T08:00:00.000000Z")
  private val t2Seen = Counter.Seen("2026-10-11T09:00:00.000000Z")

  private def c(key: Board.Wire, latest: Option[Counter.Latest], seen: Option[Counter.Seen], count: Option[Counter.Count] = None) =
    Counter(key, count, latest, seen, Counter.Fresh(UiNotices.fresh(latest, seen)))

  test("each watched window is its own board; the others watch none"):
    assertEquals(Notices.boardOf(WindowKey.Goals), Some(Board.Goals))
    assertEquals(Notices.boardOf(WindowKey.Tasks), Some(Board.Tasks))
    assertEquals(Notices.boardOf(WindowKey.Approvals), Some(Board.Approvals))
    assertEquals(Notices.boardOf(WindowKey.Acquire), Some(Board.Acquire))
    assertEquals(Notices.boardOf(WindowKey.Log), Some(Board.Log))
    assertEquals(Notices.boardOf(WindowKey.Goal(WindowKey.Goal.Slug("bronze-age"))), None, "one goal's window is not the goals list")
    assertEquals(Notices.boardOf(WindowKey.Jobs), None)
    // every board is a page of Research's, which marks it read while it is shown
    val pages = List(
      Route.Research(ResearchSection.Goals),
      Route.Research(ResearchSection.Decisions),
      Route.Research(ResearchSection.Backlog),
      Route.Research(ResearchSection.Acquire),
      Route.Activity(ActivityTab.Worklog)
    )
    Board.values.foreach(b =>
      assert(pages.exists(r => Route.board(r).flatMap(Notices.boardOf).contains(b)), b.toString)
    )
    assertEquals(Route.board(Route.Activity(ActivityTab.Jobs)), None)

  test("an open window with news is marked read at the latest change it showed"):
    val cs = Map(
      Board.Wire("tasks") -> c(Board.Wire("tasks"), Some(t2Latest), Some(t1Seen)),
      Board.Wire("goals") -> c(Board.Wire("goals"), Some(t2Latest), Some(t1Seen)),
      Board.Wire("log") -> c(Board.Wire("log"), Some(t1Latest), Some(t1Seen))
    )
    assertEquals(Notices.toMark(List(WindowKey.Tasks, WindowKey.Jobs), cs), List(Board.Tasks -> t2Latest))
    assertEquals(Notices.toMark(List(WindowKey.Log), cs), Nil, "nothing new: nothing to send")
    assertEquals(Notices.toMark(Nil, cs), Nil, "a closed window stays bold")
    assertEquals(Notices.toMark(List(WindowKey.Goal(WindowKey.Goal.Slug("x"))), cs), Nil)
    assertEquals(Notices.toMark(List(WindowKey.Tasks, WindowKey.Tasks), cs), List(Board.Tasks -> t2Latest))

  test("once marked, a board is not new, and an older mark never moves it back"):
    val marked = Notices.seenAt(c(Board.Wire("tasks"), Some(t2Latest), Some(t1Seen)), t2Seen)
    assertEquals(marked.seen, Some(t2Seen))
    assert(!marked.fresh.value)
    val kept = Notices.seenAt(c(Board.Wire("tasks"), Some(t2Latest), Some(t2Seen)), t1Seen)
    assertEquals(kept.seen, Some(t2Seen))
    assert(!kept.fresh.value)
    // a change after the mark is news again
    assert(Notices.seenAt(c(Board.Wire("tasks"), Some(t2Latest), None), t1Seen).fresh.value)

  test("the phone's tools button marks news among the boards it shows"):
    val cs = Map(Board.Wire("approvals") -> c(Board.Wire("approvals"), Some(t2Latest), Some(t1Seen)), Board.Wire("log") -> c(Board.Wire("log"), Some(t1Latest), Some(t1Seen)))
    assert(Notices.anyFresh(cs, _ => true))
    assert(!Notices.anyFresh(cs, _ != Board.Approvals), "approvals hidden (no agents): no mark")

  test("what a screen reader hears beside the label"):
    assertEquals(Notices.spoken(None), "")
    assertEquals(Notices.spoken(Some(c(Board.Wire("tasks"), Some(t1Latest), Some(t1Seen), Some(Counter.Count(0))))), "")
    assertEquals(Notices.spoken(Some(c(Board.Wire("tasks"), Some(t2Latest), Some(t1Seen), Some(Counter.Count(12))))), "12 open, something new")
    assertEquals(Notices.spoken(Some(c(Board.Wire("goals"), Some(t2Latest), Some(t1Seen)))), "something new")
