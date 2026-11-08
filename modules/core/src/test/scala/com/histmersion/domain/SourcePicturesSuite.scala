package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.media.MediaMarkdown

import com.histmersion.domain.media.figures.FigurePick
import com.histmersion.domain.media.figures.FigurePick.Figure

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import io.circe.parser.parse
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{MediaCount, MediaCoversReport, MediaImportReport, MediaProvider, MediaSourceLine}
import com.histmersion.api.media.MediaItem
import com.histmersion.api.library.RepairAcquire
import com.histmersion.api.library.Citekey

/** The pure half of a source's picture (#1446): which figure illustrates a paper ([[FigurePick]]) — on the fixture the
  * converter produced from a generated journal paper (converter/tests/test_figures.py) and on hand-made cases — and the
  * reports' arithmetic and Markdown.
  */
class SourcePicturesSuite extends munit.FunSuite:

  private val rules = FigurePick.Rules()
  private def fig(
      name: Figure.Name,
      w: Figure.Width,
      h: Figure.Height,
      refs: Figure.Refs = Figure.Refs(1),
      at: Option[Figure.FirstAt] = None
  ) =
    FigurePick.Figure(name, Some(w), Some(h), refs, at)

  test("the paper the converter cut up: the map on page 2, not the logo, the masthead or the badge"):
    val json =
      parse(scala.io.Source.fromResource("figures/paper.json")(using scala.io.Codec.UTF8).mkString).toOption.get
    val figs = json.hcursor.downField("figures").values.get.toList.map { j =>
      val c = j.hcursor
      FigurePick.Figure(
        Figure.Name(c.get[String]("name").toOption.get),
        c.get[Int]("width").toOption.map(Figure.Width(_)),
        c.get[Int]("height").toOption.map(Figure.Height(_)),
        Figure.Refs(c.get[Int]("refs").getOrElse(0)),
        c.get[Double]("firstAt").toOption.map(Figure.FirstAt(_))
      )
    }
    assertEquals(figs.size, 4)
    assertEquals(FigurePick.choose(figs, rules).map(_.name), Some("fig-0002-03.png"))
    val why = figs.map(f => f.name -> FigurePick.reject(f, rules)).toMap
    assertEquals(why(Figure.Name("fig-0001-01.png")), Some("too small"), "the logo")
    assertEquals(why(Figure.Name("fig-0001-02.png")), Some("a strip"), "the masthead")
    assertEquals(why(Figure.Name("fig-0003-02.png")), Some("too small"), "the badge")

  test("names: the page of pymupdf4llm's figures, the order of docling's"):
    assertEquals(FigurePick.page(Figure.Name("fig-0004-03.png")), Some(4))
    assertEquals(FigurePick.page(Figure.Name("fig-0004-03-2.png")), Some(4))
    assertEquals(FigurePick.page(Figure.Name("image_000003_ab.png")), None)
    assertEquals(FigurePick.order(Figure.Name("image_000003_ab.png")), Some(3))
    assertEquals(FigurePick.order(Figure.Name("cover.jpg")), None)

  test("a figure linked again and again is a running header; a page-sized series is page scans"):
    assertEquals(FigurePick.reject(fig(Figure.Name("fig-0001-01.png"), Figure.Width(600), Figure.Height(400), refs = Figure.Refs(9)), rules), Some("repeated in the text"))
    val scans = (1 to 6).map(i => fig(Figure.Name(f"fig-$i%04d-01.png"), Figure.Width(1275 + i % 2), Figure.Height(1650))).toList
    val real = fig(Figure.Name("fig-0003-02.png"), Figure.Width(700), Figure.Height(500))
    assertEquals(FigurePick.choose(scans :+ real, rules).map(_.name), Some("fig-0003-02.png"))
    assertEquals(FigurePick.choose(scans, rules), None, "nothing but page scans: no illustration")
    assertEquals(
      FigurePick.choose(scans.take(3) :+ real, rules.modify(_.pageScanMin).setTo(FigurePick.Rules.PageScanMin(5))).map(_.width),
      Some(Some(1276)),
      "three big portrait pictures are figures, not a scanned book"
    )

  test("early pages win over a larger figure late in the paper; a size-less figure never"):
    val early = fig(Figure.Name("fig-0002-01.png"), Figure.Width(600), Figure.Height(400))
    val late = fig(Figure.Name("fig-0019-01.png"), Figure.Width(900), Figure.Height(500))
    assertEquals(FigurePick.choose(List(late, early), rules).map(_.name), Some("fig-0002-01.png"))
    val huge = fig(Figure.Name("fig-0019-02.png"), Figure.Width(1600), Figure.Height(1000))
    assertEquals(FigurePick.choose(List(huge, early), rules).map(_.name), Some("fig-0019-02.png"), "unless far larger")
    assertEquals(FigurePick.reject(FigurePick.Figure(Figure.Name("x.png"), None, None), rules), Some("no size"))
    // docling names: the position in the text decides what is early
    val a = fig(Figure.Name("image_000009_x.png"), Figure.Width(600), Figure.Height(400), at = Some(Figure.FirstAt(0.1)))
    val b = fig(Figure.Name("image_000001_y.png"), Figure.Width(800), Figure.Height(500), at = Some(Figure.FirstAt(0.9)))
    assertEquals(FigurePick.choose(List(a, b), rules).map(_.name), Some("image_000009_x.png"))

  test("links in the converted text: counted per figure, the first one's position"):
    val body = "start ![](assets/a.png) mid ![](assets/b.png) ![](assets/a.png) end"
    val r = FigurePick.refs(FigurePick.ConvertedText(body))
    assertEquals(r(Figure.Name("a.png"))._1, 2)
    assertEquals(r(Figure.Name("b.png"))._1, 1)
    assert(r(Figure.Name("a.png"))._2.value < r(Figure.Name("b.png"))._2.value)
    val w = FigurePick.withRefs(List(fig(Figure.Name("a.png"), Figure.Width(1), Figure.Height(1)), fig(Figure.Name("c.png"), Figure.Width(1), Figure.Height(1))), FigurePick.ConvertedText(body))
    assertEquals(w.map(f => (f.name, f.refs, f.firstAt.isDefined)), List(("a.png", 2, true), ("c.png", 0, false)))

  test("reports: a job's batches add up; the covers report in Markdown"):
    val a = MediaImportReport(
      MediaItem.Provider(MediaProvider.Commons),
      dryRun = RepairAcquire.RepairRequest.DryRun(false),
      targets = MediaImportReport.Targets(50),
      files = MediaImportReport.Files(10),
      stored = MediaImportReport.Stored(4),
      byLicence = List(MediaCount(MediaCount.Key("CC0-1.0"), MediaCount.N(2))),
      failed = List(MediaImportReport.Failed("x")),
      pending = MediaImportReport.Pending(900),
      batches = MediaImportReport.Batches(1)
    )
    val b = a.modify(
      _.targets).setTo(MediaImportReport.Targets(20)).modify(_.stored).setTo(MediaImportReport.Stored(1)).modify(_.byLicence).setTo(List(MediaCount(MediaCount.Key("CC0-1.0"), MediaCount.N(1)), MediaCount(MediaCount.Key("CC-BY-4.0"), MediaCount.N(1)))).modify(_.failed).setTo(Nil).modify(_.pending).setTo(MediaImportReport.Pending(880)
    )
    val c = MediaImportReport.combine(a, b)
    assertEquals((c.targets, c.stored, c.batches, c.pending, c.failed), (70, 5, 2, 880, List("x")))
    assertEquals(c.byLicence, List(MediaCount(MediaCount.Key("CC0-1.0"), MediaCount.N(3)), MediaCount(MediaCount.Key("CC-BY-4.0"), MediaCount.N(1))))
    assert(MediaMarkdown.mediaImportReport(c).contains("**Not asked yet:** 880 places/events · 2 batches run"))
    val r = MediaCoversReport(
      dryRun = RepairAcquire.RepairRequest.DryRun(true),
      sources = MediaCoversReport.Sources(3),
      covers = MediaCoversReport.Covers(1),
      illustrations = MediaCoversReport.Illustrations(1),
      lines = List(MediaSourceLine(Citekey("anthony2007"), MediaSourceLine.Role("cover"), MediaItem.Provider(MediaProvider.Calibre), MediaSourceLine.Detail("Calibre id 12"))),
      skipped = List(MediaCount(MediaCount.Key("illustration: no figures"), MediaCount.N(1)))
    )
    val md = MediaMarkdown.mediaCoversReport(r)
    assert(md.contains("3 sources looked at · would link 1 cover and 1 illustration"), md)
    assert(md.contains("[[anthony2007]] — cover from Calibre library: `Calibre id 12`"), md)
    assert(md.contains("- illustration: no figures: 1"), md)
    assertEquals(MediaCoversReport.combine(r, r.modify(_.dryRun).setTo(RepairAcquire.RepairRequest.DryRun(false))).covers, 2)

  test("roles: a cover shows before an illustration"):
    assert(MediaRole.order(MediaRole.Wire(MediaRole.Cover)) < MediaRole.order(MediaRole.Wire(MediaRole.Illustration)))
    assert(MediaProvider.covers(MediaProvider.Id(MediaProvider.Calibre)) && !MediaProvider.covers(MediaProvider.Id(MediaProvider.Commons)))
