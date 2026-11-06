package com.histmersion.util.data.exchange

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

/** The three-way merge of article bodies (docs/research-merge-import.md §5.4): clean when the two sides changed
  * different places, a conflict when they changed one place differently.
  */
class Diff3Suite extends munit.FunSuite:

  private val base = "Intro.\n\nMiddle.\n\nEnd."

  test("changes in different places merge cleanly, both kept"):
    val r = Diff3.merge(base, com.histmersion.util.data.exchange.Diff3.MergeLeft("Intro (left).\n\nMiddle.\n\nEnd."), com.histmersion.util.data.exchange.Diff3.MergeRight("Intro.\n\nMiddle.\n\nEnd (right)."))
    assert(r.clean)
    assertEquals(r.text, "Intro (left).\n\nMiddle.\n\nEnd (right).")

  test("one side unchanged takes the other; the same change on both sides is taken once"):
    assertEquals(Diff3.merge(base, base, com.histmersion.util.data.exchange.Diff3.MergeRight("x")).text, "x")
    assertEquals(Diff3.merge(base, com.histmersion.util.data.exchange.Diff3.MergeLeft("y"), base).text, "y")
    val same = "Intro.\n\nMiddle, rewritten.\n\nEnd."
    assertEquals(Diff3.merge(base, same, same).text, same)
    val both =
      Diff3.merge(base, com.histmersion.util.data.exchange.Diff3.MergeLeft("Intro.\n\nMiddle, rewritten.\n\nEnd (left)."), com.histmersion.util.data.exchange.Diff3.MergeRight("Intro (right).\n\nMiddle, rewritten.\n\nEnd."))
    assert(both.clean)
    assertEquals(both.text, "Intro (right).\n\nMiddle, rewritten.\n\nEnd (left).")

  test("one place changed differently on each side is a conflict, marked like git's"):
    val r = Diff3.merge(base, com.histmersion.util.data.exchange.Diff3.MergeLeft("Intro.\n\nMiddle (left).\n\nEnd."), com.histmersion.util.data.exchange.Diff3.MergeRight("Intro.\n\nMiddle (right).\n\nEnd."))
    assert(!r.clean)
    assertEquals(r.conflicts, 1)
    val m = r.marked("here", "bundle")
    assert(
      m.contains("<<<<<<< here\nMiddle (left).\n||||||| base\nMiddle.\n=======\nMiddle (right).\n>>>>>>> bundle"),
      m
    )
    assert(m.startsWith("Intro.\n"))

  test("inserted and deleted lines on either side merge; an insertion at one spot from both conflicts"):
    val ins = Diff3.merge(com.histmersion.util.data.exchange.Diff3.MergeBase("a\nb\nc"), com.histmersion.util.data.exchange.Diff3.MergeLeft("a\nx\nb\nc"), com.histmersion.util.data.exchange.Diff3.MergeRight("a\nb\nc\ny"))
    assertEquals(ins.text, "a\nx\nb\nc\ny")
    val del = Diff3.merge(com.histmersion.util.data.exchange.Diff3.MergeBase("a\nb\nc\nd"), com.histmersion.util.data.exchange.Diff3.MergeLeft("a\nc\nd"), com.histmersion.util.data.exchange.Diff3.MergeRight("a\nb\nc"))
    assertEquals(del.text, "a\nc")
    assert(!Diff3.merge(com.histmersion.util.data.exchange.Diff3.MergeBase("a\nb"), com.histmersion.util.data.exchange.Diff3.MergeLeft("a\nx\nb"), com.histmersion.util.data.exchange.Diff3.MergeRight("a\ny\nb")).clean)

  test("property: merging with an unchanged side is the other side, whatever the texts"):
    val rnd = scala.util.Random(3L)
    def text = (1 to rnd.nextInt(12)).map(_ => rnd.nextInt(5).toString).mkString("\n")
    (1 to 200).foreach { _ =>
      val (o, a) = (text, text)
      assertEquals(Diff3.merge(o, a, o).text, a)
      assertEquals(Diff3.merge(o, o, a).text, a)
      assertEquals(Diff3.merge(o, a, a).text, a)
    }
