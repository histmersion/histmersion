package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.MergeIssue
import com.histmersion.api.exchange.MergeDecision

import com.histmersion.api.YearFmt

import java.util.UUID
import com.histmersion.api.exchange.{
  IssueCondition,
  IssueField,
  IssueHunk,
  IssueSide,
  IssueSpan,
  IssueText,
  MatchCounts,
  MergeDecision,
  MergeIssue,
  MergePlan,
  MergeResolution,
  MergeResolutions
}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.exchange.PlanMatches
import com.histmersion.api.catalogue.Year

/** The pure parts of the merge review screen: grouping and counting the issues, the filter, keeping a decision, the
  * field table's result column, the diff3 editor's starting text, the span bars.
  */
class MergeViewSuite extends munit.FunSuite:

  private val U = UUID.fromString("0191a2f0-0000-7000-8000-000000000001")

  private def issue(
      id: MergeIssue.Id,
      sev: MergeIssue.Severity = MergeIssue.Severity.Blocking,
      decision: Option[MergeDecision.Wire] = None,
      cond: Option[IssueCondition] = None,
      kind: MergeIssue.Kind = MergeIssue.Kind.StatusDiffers
  ): MergeIssue =
    MergeIssue(
      id,
      kind,
      sev,
      Bundle.Kind.Source,
      MergeIssue.Title(s"title of ${id.value}"),
      Some(IssueSide(IssueSide.Label(s"left_${id.value}"), U)),
      Some(IssueSide(IssueSide.Label(s"right_${id.value}"), U)),
      options = List(MergeDecision.Kind.KeepLeft, MergeDecision.Kind.KeepRight),
      condition = cond,
      decision = decision.map(d => MergeDecision.parse(d).toOption.get)
    )

  private def plan(is: List[MergeIssue], changes: MergePlan.Changes = MergePlan.Changes(3)): MergePlan =
    MergePlan(
      U,
      Bundle.Instance(U, Some(Bundle.Instance.Label("work"))),
      Bundle.Instance(U, Some(Bundle.Instance.Label("home"))),
      MergePlan.FormatVersion(1),
      MergePlan.SchemaVersion("54"),
      None,
      MergePlan.PlanHash("cafe0123"),
      MatchCounts(),
      PlanMatches.empty,
      is,
      Nil,
      Nil,
      Nil,
      MergePlan.Resolved(0),
      changes
    )

  test("issues are grouped open, decided, conditional, defaults, info — empty groups left out"):
    val is = List(
      issue(MergeIssue.Id("S-1")),
      issue(MergeIssue.Id("S-2"), decision = Some(MergeDecision.Wire("keep-left"))),
      issue(MergeIssue.Id("S-3"), cond = Some(IssueCondition(IssueCondition.Issue("S-9"), MergeDecision.Kind.Map))),
      issue(MergeIssue.Id("O-4"), MergeIssue.Severity.Default, Some(MergeDecision.Wire("keep-left")))
    )
    assertEquals(
      MergeView.grouped(is).map((g, xs) => g -> xs.map(_.id)),
      List(
        MergeView.Group.Open -> List("S-1"),
        MergeView.Group.Decided -> List("S-2"),
        MergeView.Group.Conditional -> List("S-3"),
        MergeView.Group.Defaults -> List("O-4")
      )
    )

  test("the counter: open blocking questions of all that apply; conditional ones do not count yet"):
    val p = plan(
      List(
        issue(MergeIssue.Id("S-1")),
        issue(MergeIssue.Id("S-2"), decision = Some(MergeDecision.Wire("keep-left"))),
        issue(MergeIssue.Id("S-3"), cond = Some(IssueCondition(IssueCondition.Issue("S-9"), MergeDecision.Kind.Map))),
        issue(MergeIssue.Id("O-4"), MergeIssue.Severity.Default)
      )
    )
    assertEquals(MergeView.counter(p), (1, 2))
    assert(!MergeView.applicable(p))
    assert(MergeView.applicable(plan(List(issue(MergeIssue.Id("S-2"), decision = Some(MergeDecision.Wire("keep-left")))))))
    assert(!MergeView.applicable(plan(Nil, changes = MergePlan.Changes(0))), "nothing to write is nothing to apply")
    assert(!MergeView.applicable(plan(Nil).modify(_.problems).setTo(List(MergePlan.Problems("a blob is missing")))))

  test("the filter matches the id, the kind, the title and the labels"):
    val is = List(issue(MergeIssue.Id("S-1")), issue(MergeIssue.Id("A-2"), kind = MergeIssue.Kind.ArticleDiverged))
    assertEquals(MergeView.filtered(is, "  ").map(_.id), List("S-1", "A-2"))
    assertEquals(MergeView.filtered(is, "article-diverged").map(_.id), List("A-2"))
    assertEquals(MergeView.filtered(is, "RIGHT_S-1").map(_.id), List("S-1"))

  test("a decision replaces the earlier one of its issue; taking it back removes it; a text goes with file: only"):
    val r0 = MergeResolutions(List(MergeResolution(MergeResolution.Issue("S-2"), MergeResolution.Decision("keep-left"))))
    val r1 = MergeView.decide(r0, MergeIssue.Id("S-1"), Some(MergeDecision.of(MergeDecision.Kind.KeepRight)), Some(MergeResolution.Text("ignored")))
    assertEquals(r1.resolve, List(MergeResolution(MergeResolution.Issue("S-1"), MergeResolution.Decision("keep-right")), MergeResolution(MergeResolution.Issue("S-2"), MergeResolution.Decision("keep-left"))))
    val r2 =
      MergeView.decide(r1, MergeIssue.Id("S-2"), Some(MergeDecision.withArg(MergeDecision.Kind.File, MergeDecision.Argument("merged/x.md"))), Some(MergeResolution.Text("text")))
    assertEquals(r2.resolve.find(_.issue == MergeResolution.Issue("S-2")), Some(MergeResolution(MergeResolution.Issue("S-2"), MergeResolution.Decision("file:merged/x.md"), Some(MergeResolution.Text("text")))))
    assertEquals(MergeView.decide(r2, MergeIssue.Id("S-1"), None).resolve.map(_.issue), List("S-2"))
    assertEquals(MergeView.chosen(r2, MergeIssue.Id("S-2")).map(_.kind), Some(MergeDecision.Kind.File))

  test(
    "the result column: the row the issue is about follows the decision (to decide until then); the others are the server's"
  ):
    val i = issue(MergeIssue.Id("S-1"))
    val f =IssueField(IssueField.Field("status"), Some(IssueField.Left("current")), Some(IssueField.Right("contested")))
    assertEquals(MergeView.resultOf(i, f, None), Some(Left("to decide")))
    assertEquals(
      MergeView.resultOf(i, f, Some(MergeDecision.of(MergeDecision.Kind.KeepRight))),
      Some(Right("contested"))
    )
    assertEquals(
      MergeView
        .resultOf(i, IssueField(IssueField.Field("title"), Some(IssueField.Left("a")), Some(IssueField.Right("b"))), Some(MergeDecision.of(MergeDecision.Kind.KeepRight))),
      None
    )
    val notes = issue(MergeIssue.Id("S-2"), kind = MergeIssue.Kind.NotesDiffers)
    assertEquals(
      MergeView
        .resultOf(notes, IssueField(IssueField.Field("notes"), Some(IssueField.Left("a")), Some(IssueField.Right("b"))), Some(MergeDecision.of(MergeDecision.Kind.Concat))),
      Some(Right("a\n\nb"))
    )
    val gate = issue(MergeIssue.Id("S-3"), kind = MergeIssue.Kind.SameWork)
    assertEquals(
      MergeView.resultOf(
        gate,
        IssueField(IssueField.Field("citekey"), Some(IssueField.Left("a")), Some(IssueField.Right("b"))),
        Some(MergeDecision.withArg(MergeDecision.Kind.Map, MergeDecision.Argument("b")))
      ),
      Some(Right("b"))
    )
    // the rows that differ come first; empty rows go
    val rows = MergeView.rows(List(IssueField(IssueField.Field("year"), Some(IssueField.Left("1")), Some(IssueField.Right("1"))), f, IssueField(IssueField.Field("doi"), None, None)))
    assertEquals(rows.map((x, d) => x.field -> d), List("status" -> true, "year" -> false))

  test("the editor starts from the agreed text with every conflict marked; markers left mean not merged"):
    val t = IssueText(
      Some(IssueText.Base("a\nb\nc")),
      IssueText.Left("a\nB\nc"),
      IssueText.Right("a\nβ\nc"),
      List(
        IssueHunk(IssueHunk.Conflict(false), left = List(IssueHunk.Left("a"))),
        IssueHunk(IssueHunk.Conflict(true), List(IssueHunk.Base("b")), List(IssueHunk.Left("B")), List(IssueHunk.Right("β"))),
        IssueHunk(IssueHunk.Conflict(false), left = List(IssueHunk.Left("c")))
      )
    )
    val m = MergeView.markedText(t)
    assertEquals(m, "a\n<<<<<<< here\nB\n||||||| common version\nb\n=======\nβ\n>>>>>>> bundle\nc")
    assert(MergeView.hasMarkers(m.value))
    assert(!MergeView.hasMarkers("a\nB and β\nc"))
    assertEquals(MergeView.fileName(issue(MergeIssue.Id("A-1"))), "merged/left_A-1.md")

  test("spans: one axis for both, each a bar in per cent of it"):
    val (lo, hi) = MergeView.axis(List(IssueSpan(Some(Year(-1200)), Some(Year(-1190))), IssueSpan(Some(Year(-1210)), None))).get
    assertEquals((lo, hi), (-1220L, -1180L))
    assertEquals(MergeView.bar(IssueSpan(Some(Year(-1200)), Some(Year(-1190))), lo, hi), (50.0, 25.0))
    assertEquals(MergeView.axis(Nil), None)
    assertEquals(YearFmt.openPeriod(Some(Year(-1274)), None), "from 1274 BCE")

  test("the window key and the /merge address"):
    assertEquals(WindowKey.parse(WindowKey.Wire("merge")), Some(WindowKey.Merge))
    assertEquals(WindowKey.Merge.wire, "merge")
    assertEquals(Route.legacy("/merge", Nil), Some(Route.Landing(Route.Library(LibrarySection.Merge), Nil)))
