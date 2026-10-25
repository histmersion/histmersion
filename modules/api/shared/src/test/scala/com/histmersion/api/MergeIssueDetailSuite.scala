package com.histmersion.api

import io.circe.Json
import io.circe.syntax.*

import java.util.UUID
import com.histmersion.values.SourceStatus

class MergeIssueDetailSuite extends munit.FunSuite:

  private val U1 = UUID.fromString("01920000-0000-7000-8000-000000000001")
  private val U2 = UUID.fromString("01920000-0000-7000-8000-000000000002")
  private val I  = UUID.fromString("01910000-0000-7000-8000-0000000000aa")

  private def source(authors: List[String], doi: Option[String] = None) =
    BundleSource(U1, I, "ck2001_a", "Tin", authors, doi, Some(2001), None, SourceStatus.Current, Nil, Nil, List("b", "a"),
      Some(BundleRange(Some(-1650), None)), Some("  a summary  "), None, None, None, None, "2026-01-01", Some("f" * 64),
      None, None, "2026-10-02T00:00:00.000000Z")

  private def event(uid: UUID, geom: Option[String]) = BundleEvent(uid, I, "e1", "Eruption", "eruption",
    Some(-1600), None, None, Some(-1500), "attested", geom, None, None, None, None, None, Nil, Nil, Nil,
    "2026-10-03T00:00:00.000000Z")

  private def place(uid: UUID, period: Option[BundleRange], names: List[String]) =
    BundlePlace(uid, I, "pl1", "Thera", "island", Some("01"), period, None, None,
      names.map(BundlePlaceName(_, None, None)), Nil, Nil, Nil, "2026-10-03T00:00:00.000000Z")

  test("a source is described by its title, first two authors, year, DOI and status"):
    assertEquals(MergeIssueDetail.describe(source(List("A", "B", "C"), Some("10.1/x"))),
      List("\"Tin\"", "A; B et al. · 2001 · DOI 10.1/x · current"))
    assertEquals(MergeIssueDetail.describe(source(Nil)), List("\"Tin\"", "2001 · no DOI · current"))
    assertEquals(MergeIssueDetail.side(source(List("A"))), IssueSide("ck2001_a", U1, List("\"Tin\"", "A · 2001 · no DOI · current")))

  test("a range reads none, always, or its two ends with … for an open one"):
    assertEquals(MergeIssueDetail.rangeText(None), "none")
    assertEquals(MergeIssueDetail.rangeText(Some(BundleRange(None, None))), "always")
    assertEquals(MergeIssueDetail.rangeText(Some(BundleRange(Some(-1650), None))), "-1650..…")

  test("preview folds whitespace and cuts to n characters; clip trims, drops blanks and cuts"):
    assertEquals(MergeIssueDetail.preview("a \n  b"), "a b")
    assertEquals(MergeIssueDetail.preview("abcdef", 4), "abc…")
    assertEquals(MergeIssueDetail.clip(Some("  x  ")), Some("x"))
    assertEquals(MergeIssueDetail.clip(Some("   ")), None)
    assertEquals(MergeIssueDetail.clip(Some("abcdef"), 4), Some("abc…"))

  test("a source pair, field by field: the OCR body by its hash prefix"):
    val fs = MergeIssueDetail.sourceFields(source(List("A")), source(List("A", "B")).copy(bodySha256 = None))
    assertEquals(fs.map(_.field), List("citekey", "title", "authors", "year", "DOI", "venue", "status", "period", "tags",
      "summary", "notes", "OCR body"))
    assertEquals(fs.find(_.field == "authors").get, IssueField("authors", Some("A"), Some("A; B")))
    assertEquals(fs.find(_.field == "period").get.left, Some("-1650..…"))
    assertEquals(fs.find(_.field == "summary").get.left, Some("a summary"))
    assertEquals(fs.find(_.field == "OCR body").get, IssueField("OCR body", Some(s"sha256 ${"f" * 12}…"), None))

  test("an event pair: its dates, geometries and the span of its outer dates"):
    val d = MergeIssueDetail.eventDetail(event(U1, Some("01")), event(U2, None))
    assertEquals(d.fields.find(_.field.startsWith("dates")).get.left, Some("-1600 · … · … · -1500"))
    assertEquals(d.place, Some(IssuePlace(Some("01"), None, Some(IssueSpan(Some(-1600), Some(-1500))),
      Some(IssueSpan(Some(-1600), Some(-1500))))))
    assertEquals(MergeIssueDetail.eventSpan(event(U1, None).copy(notBefore = None, notAfter = None)), None)

  test("a place pair: its span and names, and both periods as spans"):
    val d = MergeIssueDetail.placeDetail(place(U1, Some(BundleRange(Some(-2000), Some(-1600))), List("Thera", "Santorini")),
      place(U2, None, Nil))
    assertEquals(d.fields.find(_.field == "span").get, IssueField("span", Some("-2000..-1600"), Some("none")))
    assertEquals(d.fields.find(_.field == "names").get, IssueField("names", Some("Thera; Santorini"), None))
    assertEquals(d.place.flatMap(_.leftSpan), Some(IssueSpan(Some(-2000), Some(-1600))))

  test("an approved plan as one line; anything else as a JSON preview"):
    val p = GoalPlans.Plan("g", "why so", List(GoalPlans.PlannedGoal("sub", "Sub")), List("q1", "q2"),
      List(GoalPlans.PlannedWork("10.1/x")))
    assertEquals(MergeIssueDetail.planText(p.asJson), "why so · sub-goals: sub · queries: q1; q2 · 1 work")
    assertEquals(MergeIssueDetail.planText(Json.obj("x" -> Json.fromInt(1))), """{"x":1}""")
