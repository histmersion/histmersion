package com.histmersion.web

import com.histmersion.api.ListPatch
import SourceEditor.{Draft, Field}
import com.histmersion.api.library.MetadataPatch
import com.histmersion.domain.library.Source
import com.histmersion.api.catalogue.Period

/** The metadata editor's pure half: the form ↔ patch mapping (only what changed, with the precondition), BCE/CE years,
  * and where a server refusal is shown.
  */
class SourceEditorSuite extends munit.FunSuite:

  private val orig = com.histmersion.api.library.Source.Detail(
    citekey = "anthony2007_horse",
    title = "The Horse, the Wheel, and Language",
    authors = List("David W. Anthony"),
    year = Some(2007),
    venue = Some("Princeton UP"),
    status = Source.Status.Current,
    topics = List("pie_origins"),
    regions = List("pontic_caspian_steppe"),
    tags = List("steppe", "horses"),
    periodStart = Some(-4500L),
    periodEnd = Some(-2500L),
    summary = Some("s"),
    doi = None,
    ocrMarkdown = None,
    updatedAt = Some("t1")
  )

  private val d0 = Draft.of(orig)

  test("the form shows the stored values, periods as BCE/CE") {
    assertEquals(d0.periodStart, "4500 BCE")
    assertEquals(d0.periodEnd, "2500 BCE")
    assertEquals(d0.status, Source.Status.Current)
    assertEquals(d0.year, "2007")
  }

  test("an unchanged form is no patch at all") {
    assertEquals(SourceEditor.toPatch(orig, d0), Right(None))
    // whitespace alone is not a change
    assertEquals(SourceEditor.toPatch(orig, d0.copy(title = orig.title + "  ", periodStart = "-4500")), Right(None))
  }

  test("only the changed parts are sent, with the loaded updatedAt as the precondition") {
    val d = d0.copy(
      title = "The Horse",
      status = Source.Status.Contested,
      tags = List("horses", "wheel"),
      periodStart = "4200 BCE",
      periodEnd = "3000 BC"
    )
    val p = SourceEditor.toPatch(orig, d).toOption.flatten.get
    assertEquals(p.metadata, Some(MetadataPatch(title = Some("The Horse"), status = Some(Source.Status.Contested))))
    assertEquals(p.tags, Some(ListPatch(add = List("wheel"), remove = List("steppe"))))
    assertEquals(p.period, Some(Period.Input(-4200, -3000)))
    assertEquals(p.topics, None)
    assertEquals(p.regions, None)
    assertEquals(p.summary, None)
    assertEquals(p.ifUpdatedAt, Some("t1"))
  }

  test("list fields replace; a DOI and venue are trimmed; a CE period is positive") {
    val d = d0.copy(
      topics = List("pie_origins", "domestication"),
      regions = Nil,
      doi = " 10.1515/9781400831104 ",
      periodStart = "-100",
      periodEnd = "200 CE"
    )
    val p = SourceEditor.toPatch(orig, d).toOption.flatten.get
    assertEquals(p.topics, Some(List("pie_origins", "domestication")))
    assertEquals(p.regions, Some(Nil))
    assertEquals(p.metadata.flatMap(_.doi), Some("10.1515/9781400831104"))
    assertEquals(p.period, Some(Period.Input(-100, 200)))
  }

  test("what the patch cannot express, or the server would refuse, is caught per field") {
    val d =
      d0.copy(title = " ", year = "", venue = "", periodStart = "1200 BCE", periodEnd = "1300 BCE", doi = "not-a-doi")
    val fields = SourceEditor.toPatch(orig, d).left.toOption.get.map(_._1)
    assertEquals(fields.toSet, Set(Field.Title, Field.Year, Field.Venue, Field.Doi, Field.Period))
    assertEquals(SourceEditor.toPatch(orig, d0.copy(periodEnd = "")).left.toOption.get.map(_._1), List(Field.Period))
    assertEquals(
      SourceEditor.toPatch(orig, d0.copy(periodStart = "soon")).left.toOption.get.map(_._1),
      List(Field.Period)
    )
    assertEquals(SourceEditor.toPatch(orig, d0.copy(year = "MMVII")).left.toOption.get.map(_._1), List(Field.Year))
  }

  test("typed years: BCE negative, blank is none") {
    assertEquals(SourceEditor.year("1300 BCE"), Right(Some(-1300L)))
    assertEquals(SourceEditor.year("-1300"), Right(Some(-1300L)))
    assertEquals(SourceEditor.year("530 ce"), Right(Some(530L)))
    assertEquals(SourceEditor.year("  "), Right(None))
    assert(SourceEditor.year("later").isLeft)
  }

  test("a server refusal is shown next to the field its message names") {
    assertEquals(
      SourceEditor.fieldOf("anthony2007_horse: period_start (-1200) must not be after period_end"),
      Field.Period
    )
    assertEquals(SourceEditor.fieldOf("status=superseded requires a superseded_by edge"), Field.Status)
    assertEquals(SourceEditor.fieldOf("something else went wrong"), Field.Form)
  }
