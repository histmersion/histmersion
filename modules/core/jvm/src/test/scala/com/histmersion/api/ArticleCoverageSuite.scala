package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

/** #958 — an article's coverage is computed and rendered by code from its recorded filter: the
  * uncited remainder, the status breakdown, the counts sentence, the reasons (present or missing),
  * the Method note, the drift, and Markdown that passes the article lints. Pure. */
class ArticleCoverageSuite extends munit.FunSuite:

  private def src(ck: String, st: SourceStatus = SourceStatus.Current) =
    CoverageSource(ck, s"Title of $ck", Some(2000), st, List("Ann Author"))

  private val filter = ArticleFilter(query = Some("Theseus"), topics = List("greek_myth"))
  private val hits = List(src("walker1995_theseus", SourceStatus.Foundational), src("graves2017_greek", SourceStatus.Contested),
    src("gantz1993_earlymyth"), src("bremmer2008_religion"), src("graf1993_mythology"), src("nilsson1927_minoan", SourceStatus.Superseded))
  private val cited = List("walker1995_theseus", "gantz1993_earlymyth", "middleton2020_collapse")
  private val reasons = Map(
    "graves2017_greek" -> "retells the myths with no dated finding on their origin",
    "bremmer2008_religion" -> "a handbook; nothing on synoikism",
    "graf1993_mythology" -> "a handbook; nothing on synoikism",
    "gantz1993_earlymyth" -> "stale: it is cited now",
    "zz2000_gone" -> "the filter no longer returns it")
  private val edges = List(
    CoverageEdge("simpson1970_catalogue", "challenges", "gantz1993_earlymyth"),
    CoverageEdge("walker1995_theseus", "references", "gantz1993_earlymyth"),   // not a disagreement
    CoverageEdge("roller1999_search", "refutes", "graves2017_greek"))          // touches no cited source
  private val searches = List(CoverageSearch("synoikism", 4, List("middleton2020_collapse")))

  private def cov(total: Int = 6, hs: List[CoverageSource] = hits, rs: Map[String, String] = reasons) =
    ArticleCoverage.compute("theseus_origins", filter, total, hs, Set("walker1995_theseus", "gantz1993_earlymyth"), cited, rs,
      edges, searches)

  test("compute: cited, the uncited remainder with reasons, citations outside the filter"):
    val c = cov()
    assertEquals(c.cited.map(_.citekey), List("gantz1993_earlymyth", "walker1995_theseus"))
    assertEquals(c.notUsed.map(e => (e.source.citekey, e.reason.isDefined)), List(
      "bremmer2008_religion" -> true, "graf1993_mythology" -> true, "graves2017_greek" -> true, "nilsson1927_minoan" -> false))
    assertEquals(c.citedOutside, List("middleton2020_collapse"))
    assertEquals((c.total, c.checked, c.capped), (6, 6, false))
    assertEquals(c.filterLine, "search {query: \"Theseus\", topics: [\"greek_myth\"]}")

  test("compute: the status breakdown in taxonomy order; only disagreement edges touching a cited source"):
    val c = cov()
    assertEquals(c.statuses, List(StatusCount(SourceStatus.Foundational, 1), StatusCount(SourceStatus.Current, 3),
      StatusCount(SourceStatus.Contested, 1), StatusCount(SourceStatus.Superseded, 1)))
    assertEquals(c.edges, List(CoverageEdge("simpson1970_catalogue", "challenges", "gantz1993_earlymyth")))

  test("compute: drift — unplaced, a reason for a cited source, a reason outside the filter"):
    val c = cov()
    assertEquals(c.drift, List(
      CoverageDrift(DriftKind.Unplaced, List("nilsson1927_minoan")),
      CoverageDrift(DriftKind.ReasonForCited, List("gantz1993_earlymyth")),
      CoverageDrift(DriftKind.ReasonOutside, List("zz2000_gone"))))
    assertEquals(c.unplaced, List("nilsson1927_minoan"))
    // the reasons travel whole, drifted ones included
    assertEquals(c.reasons.map(_.citekey), reasons.keys.toList.sorted)

  test("compute: a capped read says nothing about reasons outside what it read"):
    val c = cov(total = 812)
    assert(c.capped)
    assertEquals(c.drift.map(_.kind), List(DriftKind.Unplaced, DriftKind.ReasonForCited))

  test("unplacedAfter: what a fixer's citations and reasons would leave unplaced"):
    val c = cov()
    assertEquals(ArticleCoverage.unplacedAfter(c, Set("walker1995_theseus"), reasons), List("nilsson1927_minoan"))
    assertEquals(ArticleCoverage.unplacedAfter(c, Set("nilsson1927_minoan"), reasons), Nil)
    assertEquals(ArticleCoverage.unplacedAfter(c, Set.empty, reasons + ("nilsson1927_minoan" -> "superseded")), Nil)

  test("render: the counts sentence, reasons grouped, a missing one is 'not yet assessed', statuses stated"):
    val text = ArticleCoverage.notUsed(cov())
    assertEquals(text,
      """## Not used
        |
        |The filter returns 6 sources. 2 of them are cited above; the other 4 are listed here with the reason each is left out:
        |
        |- [[bremmer2008_religion]], [[graf1993_mythology]] — a handbook; nothing on synoikism.
        |- [[graves2017_greek]] (contested) — retells the myths with no dated finding on their origin.
        |- [[nilsson1927_minoan]] (superseded) — not yet assessed""".stripMargin)

  test("render: the other counts sentences — all cited, none returned, one left out, a capped read, none cited"):
    val allCited = ArticleCoverage.compute("a", filter, 2, hits.take(2), Set.empty, List("walker1995_theseus", "graves2017_greek"),
      Map.empty, Nil, Nil)
    assert(ArticleCoverage.notUsed(allCited).endsWith("_All 2 sources the filter returns are cited above._"), ArticleCoverage.notUsed(allCited))
    val none = ArticleCoverage.compute("a", filter, 0, Nil, Set.empty, Nil, Map.empty, Nil, Nil)
    assert(ArticleCoverage.notUsed(none).endsWith("_The filter returns no source._"))
    val one = ArticleCoverage.compute("a", filter, 2, hits.take(2), Set.empty, List("walker1995_theseus"), Map.empty, Nil, Nil)
    assert(ArticleCoverage.notUsed(one).contains("1 of them is cited above; the other one is listed here with the reason it is left out:"),
      ArticleCoverage.notUsed(one))
    val capped = ArticleCoverage.notUsed(cov(total = 812))
    assert(capped.contains("The filter returns 812 sources; the first 6 (by citekey) were checked. 2 of them are cited above"), capped)
    val noneCited = ArticleCoverage.compute("a", filter, 2, hits.take(2), Set.empty, Nil, Map.empty, Nil, Nil)
    assert(ArticleCoverage.notUsed(noneCited).contains("None of them is cited above; the other 2 are listed"))

  test("render: the Method note — filter and total, statuses, further searches, outside citations, edges"):
    val m = ArticleCoverage.method(cov())
    assertEquals(m,
      "_Method: search {query: \"Theseus\", topics: [\"greek\\_myth\"]} → 6; statuses: 1 foundational, 3 current, 1 contested, " +
        "1 superseded; none debunked or retracted; further search \"synoikism\" → 4 (cited: [[middleton2020_collapse]]); " +
        "cited from outside the filter: [[middleton2020_collapse]]; disagreement edges touching the cited sources: " +
        "[[simpson1970_catalogue]] challenges [[gantz1993_earlymyth]]._")
    val quiet = ArticleCoverage.method(ArticleCoverage.compute("a", ArticleFilter(tags = List("tin")), 1, List(src("a2019_tin")),
      Set("a2019_tin"), List("a2019_tin"), Map.empty, Nil, Nil))
    assert(quiet.contains("statuses: 1 current; none contested, superseded, debunked or retracted"), quiet)
    assert(quiet.contains("no challenges, refutes, updates or superseded_by edge touches a cited source (not evidence of agreement)"), quiet)

  test("render: the generated sections pass the article lints — no uncited paragraph, no filler"):
    val variants = List(cov(), cov(total = 812), cov(rs = Map.empty),
      ArticleCoverage.compute("a", filter, 0, Nil, Set.empty, Nil, Map.empty, Nil, Nil),
      ArticleCoverage.compute("a", filter, 2, hits.take(2), Set.empty, List("walker1995_theseus", "graves2017_greek"), Map.empty, Nil, Nil))
    variants.foreach { c =>
      val md = ArticleCoverage.sections(c)
      assertEquals(Lint.articleUncitedParagraphs(List("a" -> md)), Nil, md)
      assertEquals(Lint.articleFiller(List("a" -> md)), Nil, md)
      assertEquals(Lint.extractParagraphs(md), Nil, md)
    }
    // the Method note is ONE line (a metadata line), whatever the filter's text holds
    val odd = ArticleCoverage.method(ArticleCoverage.compute("a", ArticleFilter(query = Some("a_b *c* \"d\"")), 0, Nil, Set.empty,
      Nil, Map.empty, Nil, Nil))
    assertEquals(odd.linesIterator.size, 1)
    assert(odd.contains("a\\_b \\*c\\* \\\"d\\\""), odd)

  test("filter: normalised, statuses checked, an empty one clears, extra queries need a filter"):
    assertEquals(ArticleFilter.normalise(ArticleFilter(query = Some(" tin "), topics = List("a, b", " ", "a"), status = List("Current"))),
      Right(Some(ArticleFilter(query = Some("tin"), topics = List("a", "b"), status = List("current")))))
    assert(ArticleFilter.normalise(ArticleFilter(tags = List("x"), status = List("famous"))).left.exists(_.contains("'famous'")))
    assert(ArticleFilter.normalise(ArticleFilter(tags = List("x"), from = Some(-100), to = Some(-200))).isLeft)
    assertEquals(ArticleFilter.normalise(ArticleFilter(query = Some("  "))), Right(None))
    assert(ArticleFilter.normalise(ArticleFilter(extraQueries = List("synoikism"))).isLeft)
    assertEquals(ArticleFilter(topics = List("tin"), from = Some(-1300), to = Some(-1100)).toFilter.period, Some(Period(-1300, -1100)))

  test("reasons: set, replaced, removed by an empty one, folded onto one line; a non-citekey or an essay refused"):
    val m = CoverageReason.merge(Map("a2019_tin" -> "old"), List(CoverageReason("[[A2019_tin]]", "new\n  reason"),
      CoverageReason("b2020_iso", "about Egypt"))).toOption.get
    assertEquals(m, Map("a2019_tin" -> "new reason", "b2020_iso" -> "about Egypt"))
    assertEquals(CoverageReason.merge(m, List(CoverageReason("b2020_iso", " "))), Right(Map("a2019_tin" -> "new reason")))
    assert(CoverageReason.merge(m, List(CoverageReason("Not A Key", "x"))).isLeft)
    assert(CoverageReason.merge(m, List(CoverageReason("a2019_tin", "x" * 501))).isLeft)
    assertEquals(CoverageReason.list(m).map(_.citekey), List("a2019_tin", "b2020_iso"))

  test("bodies: a hand-written Not used section is not citation; the export block is dropped on import"):
    val body = "Intro [[a2019_tin]].\n\n## Not used\n\n- [[b2020_iso]]: Egypt\n\n## Glossary\n\n- term"
    assertEquals(ArticleCoverage.withoutNotUsed(body), "Intro [[a2019_tin]].\n\n## Glossary\n\n- term")
    val c = cov()
    val exported = "Intro [[a2019_tin]].\n\n" + ArticleCoverage.exported(c)
    assertEquals(ArticleCoverage.stripGenerated(exported), "Intro [[a2019_tin]].")
    assertEquals(ArticleCoverage.stripGenerated("No block."), "No block.")
    assert(ArticleCoverage.hasMethodLine("x\n_Method: y._") && !ArticleCoverage.hasMethodLine("the _Method_ is"))

  test("the article model: put and patch carry the filter and reasons; an empty filter clears it"):
    val put = Articles.applyPut("tin", ArticlePut("Tin", "concept", "Body [[a2019_tin]].", filter = Some(ArticleFilter(tags = List("tin"))),
      reasons = List(CoverageReason("b2020_iso", "about Egypt")))).toOption.get
    assertEquals(put.filter, Some(ArticleFilter(tags = List("tin"))))
    assertEquals(put.reasons, Map("b2020_iso" -> "about Egypt"))
    val kept = Articles.applyPatch(put, ArticlePatch(summary = Some("A summary of where the tin of the Aegean came from, by sea."))).toOption.get
    assertEquals((kept.filter, kept.reasons), (put.filter, put.reasons), "a patch that names neither leaves them")
    val more = Articles.applyPatch(put, ArticlePatch(reasons = List(CoverageReason("c2001_old", "older"), CoverageReason("b2020_iso", ""))))
      .toOption.get
    assertEquals(more.reasons, Map("c2001_old" -> "older"))
    assertEquals(Articles.applyPatch(put, ArticlePatch(filter = Some(ArticleFilter()))).toOption.get.filter, None)
    assert(Articles.applyPatch(put, ArticlePatch(filter = Some(ArticleFilter(status = List("nope"))))).isLeft)
    assertEquals(ArticlePut.of(more).reasons, List(CoverageReason("c2001_old", "older")))

  test("an exported note sent back as a body: the generated block never enters the store"):
    val exported = "# T\n\nBody [[a2019_tin]].\n\n" + ArticleCoverage.exported(cov())
    assertEquals(Articles.applyPut("t", ArticlePut("T", "concept", exported)).toOption.get.body, "# T\n\nBody [[a2019_tin]].")
    val a = Articles.applyPut("t", ArticlePut("T", "concept", "# T")).toOption.get
    assertEquals(Articles.applyPatch(a, ArticlePatch(body = Some(exported))).toOption.get.body, "# T\n\nBody [[a2019_tin]].")

  test("JSON: a filter with an unknown key is refused (strict, #813)"):
    assert(io.circe.parser.decode[ArticleFilter]("""{"topic":["x"]}""").isLeft)
    assertEquals(io.circe.parser.decode[ArticleFilter]("""{"topics":["x"]}"""), Right(ArticleFilter(topics = List("x"))))
    assert(io.circe.parser.decode[ArticlePatch]("""{"filter":{"query":"tin"},"reasons":[{"citekey":"a2019_tin","reason":"x"}]}""").isRight)
