package histdb.core

/** The pure write rules for articles (plan §5.2 stream 2.7): validation, full replace, and the
  * selective merge that makes `patch_article` safe to call without re-uploading the prose. */
class ArticlesSuite extends munit.FunSuite:

  private val existing = Article(
    slug = "sea_peoples", title = "The Sea Peoples", kind = "concept",
    body = "# The Sea Peoples\n\nMedinet Habu [[cline2014_year]].",
    summary = Some("A loose label."), tags = List("collapse", "levant"),
    regions = List("Levant"), period = Some(Period(-1220, -1100)),
    updatedAt = Some("2026-08-25T10:00:00.000000Z"))

  // ── put ──────────────────────────────────────────────────────────────────────────────────────
  test("put normalises the slug and derives the summary from the lede when none is given"):
    val a = Articles.applyPut(" Sea_Peoples ", ArticlePut(
      title = " The Sea Peoples ", kind = "Concept", body = "# The Sea Peoples\n\n*A loose label.*")).toOption.get
    assertEquals(a.slug, "sea_peoples")
    assertEquals(a.kind, "concept")
    assertEquals(a.title, "The Sea Peoples")
    assertEquals(a.summary, Some("A loose label."))

  test("put rejects a slug that is not a filename, an empty title and an inverted period"):
    assert(Articles.applyPut("Sea Peoples!", ArticlePut("T", "concept", "b")).isLeft)
    assert(Articles.applyPut("x", ArticlePut("  ", "concept", "b")).isLeft)
    assertEquals(
      Articles.applyPut("x", ArticlePut("T", "concept", "b", periodStart = Some(-100), periodEnd = Some(-900)))
        .left.toOption.get.contains("must not be after"), true)

  test("put needs both period bounds or neither"):
    assert(Articles.applyPut("x", ArticlePut("T", "concept", "b", periodStart = Some(-100))).isLeft)
    assertEquals(Articles.applyPut("x", ArticlePut("T", "concept", "b")).toOption.get.period, None)

  // ── patch ────────────────────────────────────────────────────────────────────────────────────
  test("an empty patch changes nothing"):
    assertEquals(Articles.applyPatch(existing, ArticlePatch()), Right(existing))

  test("tags add/remove/replace leave the body and everything else alone"):
    val a = Articles.applyPatch(existing,
      ArticlePatch(tags = Some(ListPatch(add = List("sea-peoples"), remove = List("levant"))))).toOption.get
    assertEquals(a.tags, List("collapse", "sea-peoples"))
    assertEquals(a.body, existing.body)
    assertEquals(a.summary, existing.summary)
    assertEquals(a.period, existing.period)

  test("append extends the article instead of re-uploading it"):
    val a = Articles.applyPatch(existing, ArticlePatch(append = Some("## New evidence\n\n[[haak2015_massive]]")))
      .toOption.get
    assert(a.body.startsWith("# The Sea Peoples"), a.body)
    assert(a.body.endsWith("## New evidence\n\n[[haak2015_massive]]"), a.body)
    assert(a.body.contains("Medinet Habu"), "the original prose survives an append")

  test("body replaces, and body + append apply in that order"):
    val a = Articles.applyPatch(existing, ArticlePatch(body = Some("# New"), append = Some("tail"))).toOption.get
    assertEquals(a.body, "# New\n\ntail")

  test("period and kind are validated on the way in"):
    assert(Articles.applyPatch(existing, ArticlePatch(period = Some(PeriodInput(-100, -900)))).isLeft)
    assert(Articles.applyPatch(existing, ArticlePatch(kind = Some("not a kind"))).isLeft)
    assertEquals(
      Articles.applyPatch(existing, ArticlePatch(period = Some(PeriodInput(-1300, -1100)))).toOption.get.period,
      Some(Period(-1300, -1100)))

  test("ifUpdatedAt: matching passes, stale fails with a message telling you what to do"):
    assertEquals(Articles.precondition(existing, ArticlePatch(ifUpdatedAt = existing.updatedAt)), Right(()))
    val e = Articles.precondition(existing, ArticlePatch(ifUpdatedAt = Some("older"))).left.toOption.get
    assert(e.contains("has moved on"), e)
    assert(e.contains("Re-read"), e)

  // ── kinds ────────────────────────────────────────────────────────────────────────────────────
  test("a wiki directory maps to the singular kind it holds"):
    assertEquals(Articles.kindOfDir("concepts"), "concept")
    assertEquals(Articles.kindOfDir("myths"), "myth")
    assertEquals(Articles.kindOfDir("Reports"), "report")
    assertEquals(Articles.kindOfDir("timeline"), "timeline") // already singular, and never "timelin"
    assertEquals(Articles.kindOfDir("regions"), "region")

  test("ArticlePut.of round-trips a stored article back into writer form"):
    assertEquals(Articles.applyPut(existing.slug, ArticlePut.of(existing)).toOption.get.copy(updatedAt = None),
      existing.copy(updatedAt = None))

  // ── the Markdown surface (MCP, CLI and — through flexmark — the HTML page) ───────────────────
  private def view(body: String, backs: List[Backlink] = Nil, cited: List[CitedSource] = Nil) =
    ArticleView(
      ArticleRow("sea_peoples", "The Sea Peoples", "concept", body, Some("A lede."), "collapse",
        "Levant", Some(-1220), Some(-1100)),
      backs, cited)

  test("the metadata line goes UNDER the article's own title, never above it"):
    val md = ArticleMarkdown.article(view("# The Sea Peoples\n\nprose"))
    assertEquals(md, "# The Sea Peoples\n\n_concept · Levant · 1220 BCE–1100 BCE · collapse_\n\nprose")

  test("a body with no heading of its own gets one from the title"):
    val md = ArticleMarkdown.article(view("prose only"))
    assert(md.startsWith("# The Sea Peoples\n\n_concept"), md)
    assert(md.endsWith("prose only"), md)

  test("backlinks and the cited-sources footer render as wikilinks the page can resolve"):
    val md = ArticleMarkdown.article(
      view("# T\n\nprose",
        backs = List(Backlink("article", "bronze_age_collapse", Some("LBA Collapse"))),
        cited = List(CitedSource("cline2014_year", Some("1177 B.C."), Some(2014), Some("foundational")),
          CitedSource("zz_ghost", None, None, None))))
    assert(md.contains("## Backlinks (1)\n\n- [[bronze_age_collapse]] — LBA Collapse"), md)
    assert(md.contains("- [[cline2014_year]] — 1177 B.C. · 2014 · foundational"), md)
    assert(md.contains("_(not in the corpus)_"), "a dangling citation is shown, not dropped: " + md)

  test("no backlinks and no citations means no empty sections"):
    val md = ArticleMarkdown.article(view("# T\n\nprose"))
    assert(!md.contains("Backlinks") && !md.contains("Sources cited"), md)

  test("list renders slug, title, meta and the page size against the total"):
    val row = ArticleSummary("sea_peoples", "The Sea Peoples", "concept", Some("A lede."), "collapse",
      "Levant", Some(-1220), Some(-1100))
    assert(ArticleMarkdown.list(ArticleList(1, List(row))).startsWith("### 1 article(s)"))
    assert(ArticleMarkdown.list(ArticleList(9, List(row))).startsWith("### 1 of 9 article(s)"))
    assertEquals(ArticleMarkdown.list(ArticleList(0, Nil)), "_No matching articles._")
