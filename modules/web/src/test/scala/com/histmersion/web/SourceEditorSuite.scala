package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.ListPatch
import SourceEditor.{Draft, Field}
import com.histmersion.api.library.MetadataPatch
import com.histmersion.api.library.Source
import com.histmersion.api.catalogue.Period

/** The metadata editor's pure half: the form ↔ patch mapping (only what changed, with the precondition), BCE/CE years,
  * and where a server refusal is shown.
  */
class SourceEditorSuite extends munit.FunSuite:

  private val orig = com.histmersion.api.library.Source.Detail(
    citekey = com.histmersion.api.library.Citekey("anthony2007_horse"),
    title = com.histmersion.api.library.Title("The Horse, the Wheel, and Language"),
    authors = (List("David W. Anthony")).map(com.histmersion.api.library.AuthorName.apply),
    year = (Some(2007)).map(com.histmersion.api.catalogue.Year.apply),
    venue = (Some("Princeton UP")).map(com.histmersion.api.library.Venue.apply),
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
    assertEquals(SourceEditor.toPatch(orig, d0.modify(_.title).setTo(orig.title + "  ").modify(_.periodStart).setTo("-4500")), Right(None))
  }

  test("only the changed parts are sent, with the loaded updatedAt as the precondition") {
    val d = d0.modify(
      _.title).setTo("The Horse").modify(_.status).setTo(Source.Status.Contested).modify(_.tags).setTo(List("horses", "wheel")).modify(_.periodStart).setTo("4200 BCE").modify(_.periodEnd).setTo("3000 BC"
    )
    val p = SourceEditor.toPatch(orig, d).toOption.flatten.get
    assertEquals(p.metadata, Some(MetadataPatch(title = (Some("The Horse")).map(com.histmersion.api.library.Title.apply), status = Some(Source.Status.Contested))))
    assertEquals(p.tags, Some(ListPatch(add = List("wheel"), remove = List("steppe"))))
    assertEquals(p.period, Some(Period.Input(-4200, -3000)))
    assertEquals(p.topics, None)
    assertEquals(p.regions, None)
    assertEquals(p.summary, None)
    assertEquals(p.ifUpdatedAt, Some("t1"))
  }

  test("list fields replace; a DOI and venue are trimmed; a CE period is positive") {
    val d = d0.modify(
      _.topics).setTo(List("pie_origins", "domestication")).modify(_.regions).setTo(Nil).modify(_.doi).setTo(" 10.1515/9781400831104 ").modify(_.periodStart).setTo("-100").modify(_.periodEnd).setTo("200 CE"
    )
    val p = SourceEditor.toPatch(orig, d).toOption.flatten.get
    assertEquals(p.topics, Some(List("pie_origins", "domestication")))
    assertEquals(p.regions, Some(Nil))
    assertEquals(p.metadata.flatMap(_.doi), Some("10.1515/9781400831104"))
    assertEquals(p.period, Some(Period.Input(-100, 200)))
  }

  test("what the patch cannot express, or the server would refuse, is caught per field") {
    val d =
      d0.modify(_.title).setTo(" ").modify(_.year).setTo("").modify(_.venue).setTo("").modify(_.periodStart).setTo("1200 BCE").modify(_.periodEnd).setTo("1300 BCE").modify(_.doi).setTo("not-a-doi")
    val fields = SourceEditor.toPatch(orig, d).left.toOption.get.map(_._1)
    assertEquals(fields.toSet, Set(Field.Title, Field.Year, Field.Venue, Field.Doi, Field.Period))
    assertEquals(SourceEditor.toPatch(orig, d0.modify(_.periodEnd).setTo("")).left.toOption.get.map(_._1), List(Field.Period))
    assertEquals(
      SourceEditor.toPatch(orig, d0.modify(_.periodStart).setTo("soon")).left.toOption.get.map(_._1),
      List(Field.Period)
    )
    assertEquals(SourceEditor.toPatch(orig, d0.modify(_.year).setTo("MMVII")).left.toOption.get.map(_._1), List(Field.Year))
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
