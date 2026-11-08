package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import io.circe.Json
import io.circe.syntax.*

import java.util.UUID
import com.histmersion.api.exchange.{IssueField, IssuePlace, IssueSide, IssueSpan}
import com.histmersion.api.library.{AuthorName, Doi, Source}
import com.histmersion.api.automation.Goal
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.exchange.MergeIssue
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year

class MergeIssueDetailSuite extends munit.FunSuite:

  private val U1 = UUID.fromString("01920000-0000-7000-8000-000000000001")
  private val U2 = UUID.fromString("01920000-0000-7000-8000-000000000002")
  private val I = UUID.fromString("01910000-0000-7000-8000-0000000000aa")

  private def source(authors: List[AuthorName], doi: Option[Doi] = None) =
    Bundle.Source(
      U1,
      I,
      Citekey("ck2001_a"),
      Title("Tin"),
      authors,
      doi,
      Some(Year(2001)),
      None,
      Source.Status.Current,
      Nil,
      Nil,
      List(Bundle.Source.Tags("b"), Bundle.Source.Tags("a")),
      Some(Bundle.Range(Some(Year(-1650)), None)),
      Some(Bundle.Source.Summary("  a summary  ")),
      None,
      None,
      None,
      None,
      Bundle.Source.AddedDate("2026-01-01"),
      Some(Bundle.Source.BodySha256("f" * 64)),
      None,
      None,
      Bundle.Source.UpdatedAt("2026-10-02T00:00:00.000000Z")
    )

  private def event(uid: UUID, geom: Option[Bundle.Event.Geom]) = Bundle.Event(
    uid,
    I,
    Bundle.Event.Id("e1"),
    Bundle.Event.Label("Eruption"),
    Bundle.Event.Kind("eruption"),
    Some(Bundle.Event.NotBefore(-1600)),
    None,
    None,
    Some(Bundle.Event.NotAfter(-1500)),
    Bundle.Event.Historicity("attested"),
    geom,
    None,
    None,
    None,
    None,
    None,
    Nil,
    Nil,
    Nil,
    Bundle.Event.UpdatedAt("2026-10-03T00:00:00.000000Z")
  )

  private def place(uid: UUID, period: Option[Bundle.Range], names: List[Bundle.PlaceName.Name]) =
    Bundle.Place(
      uid,
      I,
      Bundle.Place.Id("pl1"),
      Bundle.Place.Label("Thera"),
      Bundle.Place.Kind("island"),
      Some(Bundle.Place.Geom("01")),
      period,
      None,
      None,
      names.map(Bundle.PlaceName(_, None, None)),
      Nil,
      Nil,
      Nil,
      Bundle.Place.UpdatedAt("2026-10-03T00:00:00.000000Z")
    )

  test("a source is described by its title, first two authors, year, DOI and status"):
    assertEquals(
      MergeIssue.Detail.describe(source(List(AuthorName("A"), AuthorName("B"), AuthorName("C")), Some(Doi("10.1/x")))),
      List("\"Tin\"", "A; B et al. · 2001 · DOI 10.1/x · current")
    )
    assertEquals(MergeIssue.Detail.describe(source(Nil)), List("\"Tin\"", "2001 · no DOI · current"))
    assertEquals(
      MergeIssue.Detail.side(source(List(AuthorName("A")))),
      IssueSide(
        IssueSide.Label("ck2001_a"),
        U1,
        List(IssueSide.Lines("\"Tin\""), IssueSide.Lines("A · 2001 · no DOI · current"))
      )
    )

  test("a range reads none, always, or its two ends with … for an open one"):
    assertEquals(MergeIssue.Detail.rangeText(None), "none")
    assertEquals(MergeIssue.Detail.rangeText(Some(Bundle.Range(None, None))), "always")
    assertEquals(MergeIssue.Detail.rangeText(Some(Bundle.Range(Some(Year(-1650)), None))), "-1650..…")

  test("preview folds whitespace and cuts to n characters; clip trims, drops blanks and cuts"):
    assertEquals(MergeIssue.Detail.preview("a \n  b"), "a b")
    assertEquals(MergeIssue.Detail.preview("abcdef", MergeIssue.Detail.ClipLength(4)), "abc…")
    assertEquals(MergeIssue.Detail.clip(Some("  x  ")), Some("x"))
    assertEquals(MergeIssue.Detail.clip(Some("   ")), None)
    assertEquals(MergeIssue.Detail.clip(Some("abcdef"), MergeIssue.Detail.ClipLength(4)), Some("abc…"))

  test("a source pair, field by field: the OCR body by its hash prefix"):
    val fs = MergeIssue.Detail.sourceFields(
      source(List(AuthorName("A"))),
      source(List(AuthorName("A"), AuthorName("B"))).modify(_.bodySha256).setTo(None)
    )
    assertEquals(
      fs.map(_.field),
      List(
        "citekey",
        "title",
        "authors",
        "year",
        "DOI",
        "venue",
        "status",
        "period",
        "tags",
        "summary",
        "notes",
        "OCR body"
      )
    )
    assertEquals(
      fs.find(_.field.value == "authors").get,
      IssueField(IssueField.Field("authors"), Some(IssueField.Left("A")), Some(IssueField.Right("A; B")))
    )
    assertEquals(fs.find(_.field.value == "period").get.left, Some("-1650..…"))
    assertEquals(fs.find(_.field.value == "summary").get.left, Some("a summary"))
    assertEquals(
      fs.find(_.field.value == "OCR body").get,
      IssueField(IssueField.Field("OCR body"), Some(IssueField.Left(s"sha256 ${"f" * 12}…")), None)
    )

  test("an event pair: its dates, geometries and the span of its outer dates"):
    val d = MergeIssue.Detail.eventDetail(event(U1, Some(Bundle.Event.Geom("01"))), event(U2, None))
    assertEquals(d.fields.find(_.field.value.startsWith("dates")).get.left, Some("-1600 · … · … · -1500"))
    assertEquals(
      d.place,
      Some(
        IssuePlace(
          Some(IssuePlace.LeftGeometry("01")),
          None,
          Some(IssueSpan(Some(Year(-1600)), Some(Year(-1500)))),
          Some(IssueSpan(Some(Year(-1600)), Some(Year(-1500))))
        )
      )
    )
    assertEquals(
      MergeIssue.Detail.eventSpan(event(U1, None).modify(_.notBefore).setTo(None).modify(_.notAfter).setTo(None)),
      None
    )

  test("a place pair: its span and names, and both periods as spans"):
    val d = MergeIssue.Detail.placeDetail(
      place(
        U1,
        Some(Bundle.Range(Some(Year(-2000)), Some(Year(-1600)))),
        List(Bundle.PlaceName.Name("Thera"), Bundle.PlaceName.Name("Santorini"))
      ),
      place(U2, None, Nil)
    )
    assertEquals(
      d.fields.find(_.field.value == "span").get,
      IssueField(IssueField.Field("span"), Some(IssueField.Left("-2000..-1600")), Some(IssueField.Right("none")))
    )
    assertEquals(
      d.fields.find(_.field.value == "names").get,
      IssueField(IssueField.Field("names"), Some(IssueField.Left("Thera; Santorini")), None)
    )
    assertEquals(d.place.flatMap(_.leftSpan), Some(IssueSpan(Some(Year(-2000)), Some(Year(-1600)))))

  test("an approved plan as one line; anything else as a JSON preview"):
    val p = Goal.Plans.Plan(
      Goal.Slug("g"),
      Goal.Plans.Plan.Rationale("why so"),
      List(Goal.Plans.PlannedGoal(Goal.Slug("sub"), Goal.Title("Sub"))),
      List(Goal.Plans.Plan.Queries("q1"), Goal.Plans.Plan.Queries("q2")),
      List(Goal.Plans.PlannedWork(Doi("10.1/x")))
    )
    assertEquals(MergeIssue.Detail.planText(p.asJson), "why so · sub-goals: sub · queries: q1; q2 · 1 work")
    assertEquals(MergeIssue.Detail.planText(Json.obj("x" -> Json.fromInt(1))), """{"x":1}""")
