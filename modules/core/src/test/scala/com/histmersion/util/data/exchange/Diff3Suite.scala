package com.histmersion
package util.data.exchange

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given
import com.histmersion.lint.{LintRule, tolerated}

/** The three-way merge of article bodies (docs/research-merge-import.md §5.4): clean when the two sides changed
  * different places, a conflict when they changed one place differently.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class Diff3Suite extends munit.FunSuite:

  private val baseText = "Intro.\n\nMiddle.\n\nEnd."
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val base = Diff3.Text(baseText)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val baseLeft = Diff3.Text(baseText)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val baseRight = Diff3.Text(baseText)

  test("changes in different places merge cleanly, both kept"):
    val r = Diff3.merge(
      base,
      Diff3.Text("Intro (left).\n\nMiddle.\n\nEnd."),
      Diff3.Text("Intro.\n\nMiddle.\n\nEnd (right).")
    )
    assert(r.clean)
    assertEquals(r.text, "Intro (left).\n\nMiddle.\n\nEnd (right).")

  test("one side unchanged takes the other; the same change on both sides is taken once"):
    assertEquals(Diff3.merge(base, baseLeft, Diff3.Text("x")).text, "x")
    assertEquals(Diff3.merge(base, Diff3.Text("y"), baseRight).text, "y")
    val same = "Intro.\n\nMiddle, rewritten.\n\nEnd."
    assertEquals(Diff3.merge(base, Diff3.Text(same), Diff3.Text(same)).text, same)
    val both =
      Diff3.merge(
        base,
        Diff3.Text("Intro.\n\nMiddle, rewritten.\n\nEnd (left)."),
        Diff3.Text("Intro (right).\n\nMiddle, rewritten.\n\nEnd.")
      )
    assert(both.clean)
    assertEquals(both.text, "Intro (right).\n\nMiddle, rewritten.\n\nEnd (left).")

  test("one place changed differently on each side is a conflict, marked like git's"):
    val r = Diff3.merge(
      base,
      Diff3.Text("Intro.\n\nMiddle (left).\n\nEnd."),
      Diff3.Text("Intro.\n\nMiddle (right).\n\nEnd.")
    )
    assert(!r.clean)
    assertEquals(r.conflicts, 1)
    val m = r.marked(Diff3.Result.MarkedLeftName("here"), Diff3.Result.MarkedRightName("bundle"))
    assert(
      m.value.contains("<<<<<<< here\nMiddle (left).\n||||||| base\nMiddle.\n=======\nMiddle (right).\n>>>>>>> bundle"),
      m.value
    )
    assert(m.value.startsWith("Intro.\n"))

  test("inserted and deleted lines on either side merge; an insertion at one spot from both conflicts"):
    val ins = Diff3.merge(Diff3.Text("a\nb\nc"), Diff3.Text("a\nx\nb\nc"), Diff3.Text("a\nb\nc\ny"))
    assertEquals(ins.text, "a\nx\nb\nc\ny")
    val del = Diff3.merge(Diff3.Text("a\nb\nc\nd"), Diff3.Text("a\nc\nd"), Diff3.Text("a\nb\nc"))
    assertEquals(del.text, "a\nc")
    assert(!Diff3.merge(Diff3.Text("a\nb"), Diff3.Text("a\nx\nb"), Diff3.Text("a\ny\nb")).clean)

  test("property: merging with an unchanged side is the other side, whatever the texts"):
    val rnd = scala.util.Random(3L)
    def text = (1 to rnd.nextInt(12)).map(_ => rnd.nextInt(5).toString).mkString("\n")
    (1 to 200).foreach { _ =>
      val (o, a) = (text, text)
      val (ob, ol, or) = (Diff3.Text(o), Diff3.Text(o), Diff3.Text(o))
      val (al, ar) = (Diff3.Text(a), Diff3.Text(a))
      assertEquals(Diff3.merge(ob, al, or).text, a)
      assertEquals(Diff3.merge(ob, ol, ar).text, a)
      assertEquals(Diff3.merge(ob, al, ar).text, a)
    }
