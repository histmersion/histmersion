package com.histmersion.core

import io.circe.parser.parse

/** The pure half of a source's picture (#1446): which figure illustrates a paper ([[FigurePick]]) —
  * on the fixture the converter produced from a generated journal paper (converter/tests/test_figures.py)
  * and on hand-made cases — where a book's cover comes from ([[CoverChoice]], [[OpenLibrary]]), and
  * the reports' arithmetic and Markdown. */
class SourcePicturesSuite extends munit.FunSuite:

  private val rules = FigurePick.Rules()
  private def fig(name: String, w: Int, h: Int, refs: Int = 1, at: Option[Double] = None) =
    FigurePick.Figure(name, Some(w), Some(h), refs, at)

  test("the paper the converter cut up: the map on page 2, not the logo, the masthead or the badge"):
    val json = parse(scala.io.Source.fromResource("figures/paper.json")(using scala.io.Codec.UTF8).mkString).toOption.get
    val figs = json.hcursor.downField("figures").values.get.toList.map { j =>
      val c = j.hcursor
      FigurePick.Figure(c.get[String]("name").toOption.get, c.get[Int]("width").toOption, c.get[Int]("height").toOption,
        c.get[Int]("refs").getOrElse(0), c.get[Double]("firstAt").toOption)
    }
    assertEquals(figs.size, 4)
    assertEquals(FigurePick.choose(figs, rules).map(_.name), Some("fig-0002-03.png"))
    val why = figs.map(f => f.name -> FigurePick.reject(f, rules)).toMap
    assertEquals(why("fig-0001-01.png"), Some("too small"), "the logo")
    assertEquals(why("fig-0001-02.png"), Some("a strip"), "the masthead")
    assertEquals(why("fig-0003-02.png"), Some("too small"), "the badge")

  test("names: the page of pymupdf4llm's figures, the order of docling's"):
    assertEquals(FigurePick.page("fig-0004-03.png"), Some(4))
    assertEquals(FigurePick.page("fig-0004-03-2.png"), Some(4))
    assertEquals(FigurePick.page("image_000003_ab.png"), None)
    assertEquals(FigurePick.order("image_000003_ab.png"), Some(3))
    assertEquals(FigurePick.order("cover.jpg"), None)

  test("a figure linked again and again is a running header; a page-sized series is page scans"):
    assertEquals(FigurePick.reject(fig("fig-0001-01.png", 600, 400, refs = 9), rules), Some("repeated in the text"))
    val scans = (1 to 6).map(i => fig(f"fig-$i%04d-01.png", 1275 + i % 2, 1650)).toList
    val real  = fig("fig-0003-02.png", 700, 500)
    assertEquals(FigurePick.choose(scans :+ real, rules).map(_.name), Some("fig-0003-02.png"))
    assertEquals(FigurePick.choose(scans, rules), None, "nothing but page scans: no illustration")
    assertEquals(FigurePick.choose(scans.take(3) :+ real, rules.copy(pageScanMin = 5)).map(_.width), Some(Some(1276)),
      "three big portrait pictures are figures, not a scanned book")

  test("early pages win over a larger figure late in the paper; a size-less figure never"):
    val early = fig("fig-0002-01.png", 600, 400)
    val late  = fig("fig-0019-01.png", 900, 500)
    assertEquals(FigurePick.choose(List(late, early), rules).map(_.name), Some("fig-0002-01.png"))
    val huge = fig("fig-0019-02.png", 1600, 1000)
    assertEquals(FigurePick.choose(List(huge, early), rules).map(_.name), Some("fig-0019-02.png"), "unless far larger")
    assertEquals(FigurePick.reject(FigurePick.Figure("x.png", None, None), rules), Some("no size"))
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

  test("a book's cover: Calibre's own file first, then Open Library by ISBN, then by OCLC"):
    val exists = Set("/lib/A/cover.jpg")
    assertEquals(CoverChoice.choose(Some("/lib/A/cover.jpg"), exists, List("978-0-691-16838-8"), Some("123")),
      Some(CoverChoice.Pick.Calibre("/lib/A/cover.jpg")))
    assertEquals(CoverChoice.choose(Some("/lib/B/cover.jpg"), exists, List("bad", "978-0-691-16838-8"), Some("123")),
      Some(CoverChoice.Pick.OpenLibrary("isbn", "9780691168388")), "the file is gone: the first usable ISBN")
    assertEquals(CoverChoice.choose(None, exists, Nil, Some("(OCoLC)00012345")), Some(CoverChoice.Pick.OpenLibrary("oclc", "12345")))
    assertEquals(CoverChoice.choose(None, exists, List("12"), None), None)

  test("Open Library: identifiers normalised, URLs as the API documents them"):
    assertEquals(OpenLibrary.isbn("0-306-40615-x"), Some("030640615X"))
    assertEquals(OpenLibrary.isbn("97803064061"), None)
    assertEquals(OpenLibrary.oclc("ocm00012345"), Some("12345"))
    assertEquals(OpenLibrary.key(Some("x"), Some("ocm9")), Some("oclc" -> "9"))
    assertEquals(OpenLibrary.coverUrl("https://covers.openlibrary.org/", "isbn", "9780691168388", "l"),
      "https://covers.openlibrary.org/b/isbn/9780691168388-L.jpg?default=false")
    assertEquals(OpenLibrary.pageUrl("https://openlibrary.org", "isbn", "9780691168388"),
      "https://openlibrary.org/isbn/9780691168388")

  test("reports: a job's batches add up; the covers report in Markdown"):
    val a = MediaImportReport(MediaProvider.Commons, dryRun = false, targets = 50, files = 10, stored = 4,
      byLicence = List(MediaCount("CC0-1.0", 2)), failed = List("x"), pending = 900, batches = 1)
    val b = a.copy(targets = 20, stored = 1, byLicence = List(MediaCount("CC0-1.0", 1), MediaCount("CC-BY-4.0", 1)),
      failed = Nil, pending = 880)
    val c = MediaImportReport.combine(a, b)
    assertEquals((c.targets, c.stored, c.batches, c.pending, c.failed), (70, 5, 2, 880, List("x")))
    assertEquals(c.byLicence, List(MediaCount("CC0-1.0", 3), MediaCount("CC-BY-4.0", 1)))
    assert(Markdown.mediaImportReport(c).contains("**Not asked yet:** 880 places/events · 2 batches run"))
    val r = MediaCoversReport(dryRun = true, sources = 3, covers = 1, illustrations = 1,
      lines = List(MediaSourceLine("anthony2007", "cover", MediaProvider.Calibre, "Calibre id 12")),
      skipped = List(MediaCount("illustration: no figures", 1)))
    val md = Markdown.mediaCoversReport(r)
    assert(md.contains("3 sources looked at · would link 1 cover and 1 illustration"), md)
    assert(md.contains("[[anthony2007]] — cover from Calibre library: `Calibre id 12`"), md)
    assert(md.contains("- illustration: no figures: 1"), md)
    assertEquals(MediaCoversReport.combine(r, r.copy(dryRun = false)).covers, 2)

  test("roles: a place's config may not name a source's role; a cover shows before an illustration"):
    assertEquals(MediaRoles.parse("P18:image,P18:cover,P1:illustration"), List("P18" -> "image"))
    assert(MediaRole.order(MediaRole.Cover) < MediaRole.order(MediaRole.Illustration))
    assert(MediaProvider.covers(MediaProvider.Calibre) && !MediaProvider.covers(MediaProvider.Commons))
