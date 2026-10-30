package com.histmersion.web

import com.histmersion.api.*

import java.util.UUID
import com.histmersion.values.{BundleKind, DecisionKind, IssueKind, IssueSeverity}

/** The pure parts of the merge review screen: grouping and counting the issues, the filter, keeping a decision, the
  * field table's result column, the diff3 editor's starting text, the span bars.
  */
class MergeViewSuite extends munit.FunSuite:

  private val U = UUID.fromString("0191a2f0-0000-7000-8000-000000000001")

  private def issue(
      id: String,
      sev: IssueSeverity = IssueSeverity.Blocking,
      decision: Option[String] = None,
      cond: Option[IssueCondition] = None,
      kind: IssueKind = IssueKind.StatusDiffers
  ): MergeIssue =
    MergeIssue(
      id,
      kind,
      sev,
      BundleKind.Source,
      s"title of $id",
      Some(IssueSide(s"left_$id", U)),
      Some(IssueSide(s"right_$id", U)),
      options = List(DecisionKind.KeepLeft, DecisionKind.KeepRight),
      condition = cond,
      decision = decision.map(d => MergeDecision.parse(d).toOption.get)
    )

  private def plan(is: List[MergeIssue], changes: Int = 3): MergePlan =
    MergePlan(
      U,
      BundleInstance(U, Some("work")),
      BundleInstance(U, Some("home")),
      1,
      "54",
      None,
      "cafe0123",
      MatchCounts(),
      Nil,
      is,
      Nil,
      Nil,
      Nil,
      0,
      changes
    )

  test("issues are grouped open, decided, conditional, defaults, info — empty groups left out"):
    val is = List(
      issue("S-1"),
      issue("S-2", decision = Some("keep-left")),
      issue("S-3", cond = Some(IssueCondition("S-9", DecisionKind.Map))),
      issue("O-4", IssueSeverity.Default, Some("keep-left"))
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
        issue("S-1"),
        issue("S-2", decision = Some("keep-left")),
        issue("S-3", cond = Some(IssueCondition("S-9", DecisionKind.Map))),
        issue("O-4", IssueSeverity.Default)
      )
    )
    assertEquals(MergeView.counter(p), (1, 2))
    assert(!MergeView.applicable(p))
    assert(MergeView.applicable(plan(List(issue("S-2", decision = Some("keep-left"))))))
    assert(!MergeView.applicable(plan(Nil, changes = 0)), "nothing to write is nothing to apply")
    assert(!MergeView.applicable(plan(Nil).copy(problems = List("a blob is missing"))))

  test("the filter matches the id, the kind, the title and the labels"):
    val is = List(issue("S-1"), issue("A-2", kind = IssueKind.ArticleDiverged))
    assertEquals(MergeView.filtered(is, "  ").map(_.id), List("S-1", "A-2"))
    assertEquals(MergeView.filtered(is, "article-diverged").map(_.id), List("A-2"))
    assertEquals(MergeView.filtered(is, "RIGHT_S-1").map(_.id), List("S-1"))

  test("a decision replaces the earlier one of its issue; taking it back removes it; a text goes with file: only"):
    val r0 = MergeResolutions(List(MergeResolution("S-2", "keep-left")))
    val r1 = MergeView.decide(r0, "S-1", Some(MergeDecision.of(DecisionKind.KeepRight)), Some("ignored"))
    assertEquals(r1.resolve, List(MergeResolution("S-1", "keep-right"), MergeResolution("S-2", "keep-left")))
    val r2 = MergeView.decide(r1, "S-2", Some(MergeDecision.withArg(DecisionKind.File, "merged/x.md")), Some("text"))
    assertEquals(r2.resolve.find(_.issue == "S-2"), Some(MergeResolution("S-2", "file:merged/x.md", Some("text"))))
    assertEquals(MergeView.decide(r2, "S-1", None).resolve.map(_.issue), List("S-2"))
    assertEquals(MergeView.chosen(r2, "S-2").map(_.kind), Some(DecisionKind.File))

  test(
    "the result column: the row the issue is about follows the decision (to decide until then); the others are the server's"
  ):
    val i = issue("S-1")
    val f = IssueField("status", Some("current"), Some("contested"))
    assertEquals(MergeView.resultOf(i, f, None), Some(Left("to decide")))
    assertEquals(MergeView.resultOf(i, f, Some(MergeDecision.of(DecisionKind.KeepRight))), Some(Right("contested")))
    assertEquals(
      MergeView.resultOf(i, IssueField("title", Some("a"), Some("b")), Some(MergeDecision.of(DecisionKind.KeepRight))),
      None
    )
    val notes = issue("S-2", kind = IssueKind.NotesDiffers)
    assertEquals(
      MergeView.resultOf(notes, IssueField("notes", Some("a"), Some("b")), Some(MergeDecision.of(DecisionKind.Concat))),
      Some(Right("a\n\nb"))
    )
    val gate = issue("S-3", kind = IssueKind.SameWork)
    assertEquals(
      MergeView.resultOf(
        gate,
        IssueField("citekey", Some("a"), Some("b")),
        Some(MergeDecision.withArg(DecisionKind.Map, "b"))
      ),
      Some(Right("b"))
    )
    // the rows that differ come first; empty rows go
    val rows = MergeView.rows(List(IssueField("year", Some("1"), Some("1")), f, IssueField("doi", None, None)))
    assertEquals(rows.map((x, d) => x.field -> d), List("status" -> true, "year" -> false))

  test("the editor starts from the agreed text with every conflict marked; markers left mean not merged"):
    val t = IssueText(
      Some("a\nb\nc"),
      "a\nB\nc",
      "a\nβ\nc",
      List(
        IssueHunk(false, left = List("a")),
        IssueHunk(true, List("b"), List("B"), List("β")),
        IssueHunk(false, left = List("c"))
      )
    )
    val m = MergeView.markedText(t)
    assertEquals(m, "a\n<<<<<<< here\nB\n||||||| common version\nb\n=======\nβ\n>>>>>>> bundle\nc")
    assert(MergeView.hasMarkers(m))
    assert(!MergeView.hasMarkers("a\nB and β\nc"))
    assertEquals(MergeView.fileName(issue("A-1")), "merged/left_A-1.md")

  test("spans: one axis for both, each a bar in per cent of it"):
    val (lo, hi) = MergeView.axis(List(IssueSpan(Some(-1200), Some(-1190)), IssueSpan(Some(-1210), None))).get
    assertEquals((lo, hi), (-1220L, -1180L))
    assertEquals(MergeView.bar(IssueSpan(Some(-1200), Some(-1190)), lo, hi), (50.0, 25.0))
    assertEquals(MergeView.axis(Nil), None)
    assertEquals(YearFmt.openPeriod(Some(-1274), None), "from 1274 BCE")

  test("the window key and the /merge address"):
    assertEquals(WindowKey.parse("merge"), Some(WindowKey.Merge))
    assertEquals(WindowKey.Merge.wire, "merge")
    assertEquals(Route.legacy("/merge", Nil), Some(Route.Landing(Route.Library(LibrarySection.Merge), Nil)))
