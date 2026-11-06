package com.histmersion.api

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

class MergeIssueDetailSuite extends munit.FunSuite:

  private val U1 = UUID.fromString("01920000-0000-7000-8000-000000000001")
  private val U2 = UUID.fromString("01920000-0000-7000-8000-000000000002")
  private val I = UUID.fromString("01910000-0000-7000-8000-0000000000aa")

  private def source(authors: List[AuthorName], doi: Option[Doi] = None) =
    Bundle.Source(
      U1,
      I,
      com.histmersion.api.library.Citekey("ck2001_a"),
      com.histmersion.api.library.Title("Tin"),
      authors,
      doi,
      Some(com.histmersion.api.catalogue.Year(2001)),
      None,
      Source.Status.Current,
      Nil,
      Nil,
      List(com.histmersion.api.exchange.Bundle.Source.Tags("b"), com.histmersion.api.exchange.Bundle.Source.Tags("a")),
      Some(Bundle.Range(Some(com.histmersion.api.catalogue.Year(-1650)), None)),
      Some(com.histmersion.api.exchange.Bundle.Source.Summary("  a summary  ")),
      None,
      None,
      None,
      None,
      com.histmersion.api.exchange.Bundle.Source.AddedDate("2026-01-01"),
      Some(com.histmersion.api.exchange.Bundle.Source.BodySha256("f" * 64)),
      None,
      None,
      com.histmersion.api.exchange.Bundle.Source.UpdatedAt("2026-10-02T00:00:00.000000Z")
    )

  private def event(uid: UUID, geom: Option[Bundle.Event.Geom]) = Bundle.Event(
    uid,
    I,
    com.histmersion.api.exchange.Bundle.Event.Id("e1"),
    com.histmersion.api.exchange.Bundle.Event.Label("Eruption"),
    com.histmersion.api.exchange.Bundle.Event.Kind("eruption"),
    Some(com.histmersion.api.exchange.Bundle.Event.NotBefore(-1600)),
    None,
    None,
    Some(com.histmersion.api.exchange.Bundle.Event.NotAfter(-1500)),
    com.histmersion.api.exchange.Bundle.Event.Historicity("attested"),
    geom,
    None,
    None,
    None,
    None,
    None,
    Nil,
    Nil,
    Nil,
    com.histmersion.api.exchange.Bundle.Event.UpdatedAt("2026-10-03T00:00:00.000000Z")
  )

  private def place(uid: UUID, period: Option[Bundle.Range], names: List[Bundle.PlaceName.Name]) =
    Bundle.Place(
      uid,
      I,
      com.histmersion.api.exchange.Bundle.Place.Id("pl1"),
      com.histmersion.api.exchange.Bundle.Place.Label("Thera"),
      com.histmersion.api.exchange.Bundle.Place.Kind("island"),
      Some(com.histmersion.api.exchange.Bundle.Place.Geom("01")),
      period,
      None,
      None,
      names.map(Bundle.PlaceName(_, None, None)),
      Nil,
      Nil,
      Nil,
      com.histmersion.api.exchange.Bundle.Place.UpdatedAt("2026-10-03T00:00:00.000000Z")
    )

  test("a source is described by its title, first two authors, year, DOI and status"):
    assertEquals(
      MergeIssue.Detail.describe(source(List(AuthorName("A"), AuthorName("B"), AuthorName("C")), Some(Doi("10.1/x")))),
      List("\"Tin\"", "A; B et al. · 2001 · DOI 10.1/x · current")
    )
    assertEquals(MergeIssue.Detail.describe(source(Nil)), List("\"Tin\"", "2001 · no DOI · current"))
    assertEquals(
      MergeIssue.Detail.side(source(List(AuthorName("A")))),
      IssueSide(com.histmersion.api.exchange.IssueSide.Label("ck2001_a"), U1, List(com.histmersion.api.exchange.IssueSide.Lines("\"Tin\""), com.histmersion.api.exchange.IssueSide.Lines("A · 2001 · no DOI · current")))
    )

  test("a range reads none, always, or its two ends with … for an open one"):
    assertEquals(MergeIssue.Detail.rangeText(None), "none")
    assertEquals(MergeIssue.Detail.rangeText(Some(Bundle.Range(None, None))), "always")
    assertEquals(MergeIssue.Detail.rangeText(Some(Bundle.Range(Some(com.histmersion.api.catalogue.Year(-1650)), None))), "-1650..…")

  test("preview folds whitespace and cuts to n characters; clip trims, drops blanks and cuts"):
    assertEquals(MergeIssue.Detail.preview("a \n  b"), "a b")
    assertEquals(MergeIssue.Detail.preview("abcdef", com.histmersion.api.exchange.MergeIssue.Detail.ClipLength(4)), "abc…")
    assertEquals(MergeIssue.Detail.clip(Some("  x  ")), Some("x"))
    assertEquals(MergeIssue.Detail.clip(Some("   ")), None)
    assertEquals(MergeIssue.Detail.clip(Some("abcdef"), com.histmersion.api.exchange.MergeIssue.Detail.ClipLength(4)), Some("abc…"))

  test("a source pair, field by field: the OCR body by its hash prefix"):
    val fs = MergeIssue.Detail.sourceFields(source(List(AuthorName("A"))), source(List(AuthorName("A"), AuthorName("B"))).modify(_.bodySha256).setTo(None))
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
    assertEquals(fs.find(_.field.value == "authors").get, IssueField(com.histmersion.api.exchange.IssueField.Field("authors"), Some(com.histmersion.api.exchange.IssueField.Left("A")), Some(com.histmersion.api.exchange.IssueField.Right("A; B"))))
    assertEquals(fs.find(_.field.value == "period").get.left, Some("-1650..…"))
    assertEquals(fs.find(_.field.value == "summary").get.left, Some("a summary"))
    assertEquals(fs.find(_.field.value == "OCR body").get, IssueField(com.histmersion.api.exchange.IssueField.Field("OCR body"), Some(com.histmersion.api.exchange.IssueField.Left(s"sha256 ${"f" * 12}…")), None))

  test("an event pair: its dates, geometries and the span of its outer dates"):
    val d = MergeIssue.Detail.eventDetail(event(U1, Some(Bundle.Event.Geom("01"))), event(U2, None))
    assertEquals(d.fields.find(_.field.value.startsWith("dates")).get.left, Some("-1600 · … · … · -1500"))
    assertEquals(
      d.place,
      Some(
        IssuePlace(
          Some(com.histmersion.api.exchange.IssuePlace.LeftGeometry("01")),
          None,
          Some(IssueSpan(Some(com.histmersion.api.catalogue.Year(-1600)), Some(com.histmersion.api.catalogue.Year(-1500)))),
          Some(IssueSpan(Some(com.histmersion.api.catalogue.Year(-1600)), Some(com.histmersion.api.catalogue.Year(-1500))))
        )
      )
    )
    assertEquals(MergeIssue.Detail.eventSpan(event(U1, None).modify(_.notBefore).setTo(None).modify(_.notAfter).setTo(None)), None)

  test("a place pair: its span and names, and both periods as spans"):
    val d = MergeIssue.Detail.placeDetail(
      place(U1, Some(Bundle.Range(Some(com.histmersion.api.catalogue.Year(-2000)), Some(com.histmersion.api.catalogue.Year(-1600)))), List(Bundle.PlaceName.Name("Thera"), Bundle.PlaceName.Name("Santorini"))),
      place(U2, None, Nil)
    )
    assertEquals(d.fields.find(_.field.value == "span").get, IssueField(com.histmersion.api.exchange.IssueField.Field("span"), Some(com.histmersion.api.exchange.IssueField.Left("-2000..-1600")), Some(com.histmersion.api.exchange.IssueField.Right("none"))))
    assertEquals(d.fields.find(_.field.value == "names").get, IssueField(com.histmersion.api.exchange.IssueField.Field("names"), Some(com.histmersion.api.exchange.IssueField.Left("Thera; Santorini")), None))
    assertEquals(d.place.flatMap(_.leftSpan), Some(IssueSpan(Some(com.histmersion.api.catalogue.Year(-2000)), Some(com.histmersion.api.catalogue.Year(-1600)))))

  test("an approved plan as one line; anything else as a JSON preview"):
    val p = Goal.Plans.Plan(
      com.histmersion.api.automation.Goal.Slug("g"),
      com.histmersion.api.automation.Goal.Plans.Plan.Rationale("why so"),
      List(Goal.Plans.PlannedGoal(com.histmersion.api.automation.Goal.Slug("sub"), com.histmersion.api.automation.Goal.Title("Sub"))),
      List(com.histmersion.api.automation.Goal.Plans.Plan.Queries("q1"), com.histmersion.api.automation.Goal.Plans.Plan.Queries("q2")),
      List(Goal.Plans.PlannedWork(com.histmersion.api.library.Doi("10.1/x")))
    )
    assertEquals(MergeIssue.Detail.planText(p.asJson), "why so · sub-goals: sub · queries: q1; q2 · 1 work")
    assertEquals(MergeIssue.Detail.planText(Json.obj("x" -> Json.fromInt(1))), """{"x":1}""")
