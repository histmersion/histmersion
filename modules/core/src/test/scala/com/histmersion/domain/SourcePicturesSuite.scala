package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.media.MediaMarkdown

import com.histmersion.domain.media.figures.FigurePick

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import io.circe.parser.parse
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{MediaCount, MediaCoversReport, MediaImportReport, MediaProvider, MediaSourceLine}

/** The pure half of a source's picture (#1446): which figure illustrates a paper ([[FigurePick]]) — on the fixture the
  * converter produced from a generated journal paper (converter/tests/test_figures.py) and on hand-made cases — and the
  * reports' arithmetic and Markdown.
  */
class SourcePicturesSuite extends munit.FunSuite:

  private val rules = FigurePick.Rules()
  private def fig(name: String, w: Int, h: Int, refs: Int = 1, at: Option[Double] = None) =
    FigurePick.Figure(name, Some(w), Some(h), refs, at)

  test("the paper the converter cut up: the map on page 2, not the logo, the masthead or the badge"):
    val json =
      parse(scala.io.Source.fromResource("figures/paper.json")(using scala.io.Codec.UTF8).mkString).toOption.get
    val figs = json.hcursor.downField("figures").values.get.toList.map { j =>
      val c = j.hcursor
      FigurePick.Figure(
        c.get[String]("name").toOption.get,
        c.get[Int]("width").toOption,
        c.get[Int]("height").toOption,
        c.get[Int]("refs").getOrElse(0),
        c.get[Double]("firstAt").toOption
      )
    }
    assertEquals(figs.size, 4)
    assertEquals(FigurePick.choose(figs, rules).map(_.name), Some("fig-0002-03.png"))
    val why = figs.map(f => f.name -> FigurePick.reject(f, rules)).toMap
    assertEquals(why(com.histmersion.domain.media.figures.FigurePick.Figure.Name("fig-0001-01.png")), Some("too small"), "the logo")
    assertEquals(why(com.histmersion.domain.media.figures.FigurePick.Figure.Name("fig-0001-02.png")), Some("a strip"), "the masthead")
    assertEquals(why(com.histmersion.domain.media.figures.FigurePick.Figure.Name("fig-0003-02.png")), Some("too small"), "the badge")

  test("names: the page of pymupdf4llm's figures, the order of docling's"):
    assertEquals(FigurePick.page(com.histmersion.domain.media.figures.FigurePick.Figure.Name("fig-0004-03.png")), Some(4))
    assertEquals(FigurePick.page(com.histmersion.domain.media.figures.FigurePick.Figure.Name("fig-0004-03-2.png")), Some(4))
    assertEquals(FigurePick.page(com.histmersion.domain.media.figures.FigurePick.Figure.Name("image_000003_ab.png")), None)
    assertEquals(FigurePick.order(com.histmersion.domain.media.figures.FigurePick.Figure.Name("image_000003_ab.png")), Some(3))
    assertEquals(FigurePick.order(com.histmersion.domain.media.figures.FigurePick.Figure.Name("cover.jpg")), None)

  test("a figure linked again and again is a running header; a page-sized series is page scans"):
    assertEquals(FigurePick.reject(fig("fig-0001-01.png", 600, 400, refs = 9), rules), Some("repeated in the text"))
    val scans = (1 to 6).map(i => fig(f"fig-$i%04d-01.png", 1275 + i % 2, 1650)).toList
    val real = fig("fig-0003-02.png", 700, 500)
    assertEquals(FigurePick.choose(scans :+ real, rules).map(_.name), Some("fig-0003-02.png"))
    assertEquals(FigurePick.choose(scans, rules), None, "nothing but page scans: no illustration")
    assertEquals(
      FigurePick.choose(scans.take(3) :+ real, rules.modify(_.pageScanMin).setTo(5)).map(_.width),
      Some(Some(1276)),
      "three big portrait pictures are figures, not a scanned book"
    )

  test("early pages win over a larger figure late in the paper; a size-less figure never"):
    val early = fig("fig-0002-01.png", 600, 400)
    val late = fig("fig-0019-01.png", 900, 500)
    assertEquals(FigurePick.choose(List(late, early), rules).map(_.name), Some("fig-0002-01.png"))
    val huge = fig("fig-0019-02.png", 1600, 1000)
    assertEquals(FigurePick.choose(List(huge, early), rules).map(_.name), Some("fig-0019-02.png"), "unless far larger")
    assertEquals(FigurePick.reject(FigurePick.Figure(com.histmersion.domain.media.figures.FigurePick.Figure.Name("x.png"), None, None), rules), Some("no size"))
    // docling names: the position in the text decides what is early
    val a = fig("image_000009_x.png", 600, 400, at = Some(0.1))
    val b = fig("image_000001_y.png", 800, 500, at = Some(0.9))
    assertEquals(FigurePick.choose(List(a, b), rules).map(_.name), Some("image_000009_x.png"))

  test("links in the converted text: counted per figure, the first one's position"):
    val body = "start ![](assets/a.png) mid ![](assets/b.png) ![](assets/a.png) end"
    val r = FigurePick.refs(body)
    assertEquals(r("a.png")._1, 2)
    assertEquals(r("b.png")._1, 1)
    assert(r("a.png")._2 < r("b.png")._2)
    val w = FigurePick.withRefs(List(fig("a.png", 1, 1), fig("c.png", 1, 1)), body)
    assertEquals(w.map(f => (f.name, f.refs, f.firstAt.isDefined)), List(("a.png", 2, true), ("c.png", 0, false)))

  test("reports: a job's batches add up; the covers report in Markdown"):
    val a = MediaImportReport(
      MediaProvider.Commons,
      dryRun = com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun(false),
      targets = com.histmersion.api.media.MediaImportReport.Targets(50),
      files = 10,
      stored = 4,
      byLicence = List(MediaCount(com.histmersion.api.media.MediaCount.Key("CC0-1.0"), com.histmersion.api.media.MediaCount.N(2))),
      failed = List("x"),
      pending = 900,
      batches = 1
    )
    val b = a.modify(
      _.targets).setTo(20).modify(_.stored).setTo(1).modify(_.byLicence).setTo(List(MediaCount("CC0-1.0", 1), MediaCount("CC-BY-4.0", 1))).modify(_.failed).setTo(Nil).modify(_.pending).setTo(880
    )
    val c = MediaImportReport.combine(a, b)
    assertEquals((c.targets, c.stored, c.batches, c.pending, c.failed), (70, 5, 2, 880, List("x")))
    assertEquals(c.byLicence, List(MediaCount(com.histmersion.api.media.MediaCount.Key("CC0-1.0"), com.histmersion.api.media.MediaCount.N(3)), MediaCount(com.histmersion.api.media.MediaCount.Key("CC-BY-4.0"), com.histmersion.api.media.MediaCount.N(1))))
    assert(MediaMarkdown.mediaImportReport(c).contains("**Not asked yet:** 880 places/events · 2 batches run"))
    val r = MediaCoversReport(
      dryRun = com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun(true),
      sources = com.histmersion.api.media.MediaCoversReport.Sources(3),
      covers = com.histmersion.api.media.MediaCoversReport.Covers(1),
      illustrations = com.histmersion.api.media.MediaCoversReport.Illustrations(1),
      lines = List(MediaSourceLine(com.histmersion.api.library.Citekey("anthony2007"), com.histmersion.api.media.MediaSourceLine.Role("cover"), MediaProvider.Calibre, com.histmersion.api.media.MediaSourceLine.Detail("Calibre id 12"))),
      skipped = List(MediaCount(com.histmersion.api.media.MediaCount.Key("illustration: no figures"), com.histmersion.api.media.MediaCount.N(1)))
    )
    val md = MediaMarkdown.mediaCoversReport(r)
    assert(md.contains("3 sources looked at · would link 1 cover and 1 illustration"), md)
    assert(md.contains("[[anthony2007]] — cover from Calibre library: `Calibre id 12`"), md)
    assert(md.contains("- illustration: no figures: 1"), md)
    assertEquals(MediaCoversReport.combine(r, r.modify(_.dryRun).setTo(false)).covers, 2)

  test("roles: a cover shows before an illustration"):
    assert(MediaRole.order(MediaRole.Cover) < MediaRole.order(MediaRole.Illustration))
    assert(MediaProvider.covers(MediaProvider.Calibre) && !MediaProvider.covers(MediaProvider.Commons))
