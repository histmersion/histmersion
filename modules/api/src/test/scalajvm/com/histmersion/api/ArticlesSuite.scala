package com.histmersion.api

import com.histmersion.api.catalogue.Period
import com.histmersion.api.research.{Article, ArticleMarkdown, Articles, Backlink, CitedSource}
import com.histmersion.api.library.Source

/** The pure write rules for articles (plan §5.2 stream 2.7): validation, full replace, and the selective merge that
  * makes `patch_article` safe to call without re-uploading the prose.
  */
class ArticlesSuite extends munit.FunSuite:

  private val existing = Article(
    slug = "sea_peoples",
    title = "The Sea Peoples",
    kind = "concept",
    body = "# The Sea Peoples\n\nMedinet Habu [[cline2014_year]].",
    summary = Some("A loose label."),
    tags = List("collapse", "levant"),
    regions = List("Levant"),
    period = Some(Period(-1220, -1100)),
    updatedAt = Some("2026-08-25T10:00:00.000000Z")
  )

  // ── put ──────────────────────────────────────────────────────────────────────────────────────
  test("put normalises the slug and derives the summary from the lede when none is given"):
    val a = Articles
      .applyPut(
        " Sea_Peoples ",
        Article.Put(title = " The Sea Peoples ", kind = "Concept", body = "# The Sea Peoples\n\n*A loose label.*")
      )
      .toOption
      .get
    assertEquals(a.slug, "sea_peoples")
    assertEquals(a.kind, "concept")
    assertEquals(a.title, "The Sea Peoples")
    assertEquals(a.summary, Some("A loose label."))

  test("put rejects a slug that is not a filename, an empty title and an inverted period"):
    assert(Articles.applyPut("Sea Peoples!", Article.Put("T", "concept", "b")).isLeft)
    assert(Articles.applyPut("x", Article.Put("  ", "concept", "b")).isLeft)
    assertEquals(
      Articles
        .applyPut("x", Article.Put("T", "concept", "b", periodStart = Some(-100), periodEnd = Some(-900)))
        .left
        .toOption
        .get
        .contains("must not be after"),
      true
    )

  test("put needs both period bounds or neither"):
    assert(Articles.applyPut("x", Article.Put("T", "concept", "b", periodStart = Some(-100))).isLeft)
    assertEquals(Articles.applyPut("x", Article.Put("T", "concept", "b")).toOption.get.period, None)

  // ── patch ────────────────────────────────────────────────────────────────────────────────────
  test("an empty patch changes nothing"):
    assertEquals(Articles.applyPatch(existing, Article.Patch()), Right(existing))

  test("tags add/remove/replace leave the body and everything else alone"):
    val a = Articles
      .applyPatch(existing, Article.Patch(tags = Some(ListPatch(add = List("sea-peoples"), remove = List("levant")))))
      .toOption
      .get
    assertEquals(a.tags, List("collapse", "sea-peoples"))
    assertEquals(a.body, existing.body)
    assertEquals(a.summary, existing.summary)
    assertEquals(a.period, existing.period)

  test("append extends the article instead of re-uploading it"):
    val a =
      Articles
        .applyPatch(existing, Article.Patch(append = Some("## New evidence\n\n[[haak2015_massive]]")))
        .toOption
        .get
    assert(a.body.startsWith("# The Sea Peoples"), a.body)
    assert(a.body.endsWith("## New evidence\n\n[[haak2015_massive]]"), a.body)
    assert(a.body.contains("Medinet Habu"), "the original prose survives an append")

  test("body replaces, and body + append apply in that order"):
    val a = Articles.applyPatch(existing, Article.Patch(body = Some("# New"), append = Some("tail"))).toOption.get
    assertEquals(a.body, "# New\n\ntail")

  test("period and kind are validated on the way in"):
    assert(Articles.applyPatch(existing, Article.Patch(period = Some(Period.Input(-100, -900)))).isLeft)
    assert(Articles.applyPatch(existing, Article.Patch(kind = Some("not a kind"))).isLeft)
    assertEquals(
      Articles.applyPatch(existing, Article.Patch(period = Some(Period.Input(-1300, -1100)))).toOption.get.period,
      Some(Period(-1300, -1100))
    )

  test("ifUpdatedAt: matching passes, stale fails with a message telling you what to do"):
    assertEquals(Articles.precondition(existing, Article.Patch(ifUpdatedAt = existing.updatedAt)), Right(()))
    val e = Articles.precondition(existing, Article.Patch(ifUpdatedAt = Some("older"))).left.toOption.get
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
    assertEquals(
      Articles.applyPut(existing.slug, Article.Put.of(existing)).toOption.get.copy(updatedAt = None),
      existing.copy(updatedAt = None)
    )

  // ── the Markdown surface (MCP, CLI and — through flexmark — the HTML page) ───────────────────
  private def view(body: String, backs: List[Backlink] = Nil, cited: List[CitedSource] = Nil) =
    Article.View(
      Article.Row(
        "sea_peoples",
        "The Sea Peoples",
        "concept",
        body,
        Some("A lede."),
        List("collapse"),
        List("Levant"),
        Some(-1220),
        Some(-1100)
      ),
      backs,
      cited
    )

  test("the metadata line goes UNDER the article's own title, never above it"):
    val md = ArticleMarkdown.article(view("# The Sea Peoples\n\nprose"))
    assertEquals(
      md,
      "# The Sea Peoples\n\n_concept · [Levant](/#place=Levant) · 1220 BCE–1100 BCE · " +
        "[collapse](/#tag=collapse)_\n\nprose"
    )

  test("a body with no heading of its own gets one from the title"):
    val md = ArticleMarkdown.article(view("prose only"))
    assert(md.startsWith("# The Sea Peoples\n\n_concept"), md)
    assert(md.endsWith("prose only"), md)

  test("backlinks and the cited-sources footer render as wikilinks the page can resolve"):
    val md = ArticleMarkdown.article(
      view(
        "# T\n\nprose",
        backs = List(Backlink("article", "bronze_age_collapse", Some("LBA Collapse"))),
        cited = List(
          CitedSource("cline2014_year", Some("1177 B.C."), Some(2014), Some(Source.Status.Foundational)),
          CitedSource("zz_ghost", None, None, None)
        )
      )
    )
    assert(md.contains("## Backlinks (1)\n\n- [[bronze_age_collapse]] — LBA Collapse"), md)
    assert(md.contains("- [[cline2014_year]] — 2014 · _1177 B.C._ · **foundational**"), md)
    assert(md.contains("_(not in the corpus)_"), "a dangling citation is shown, not dropped: " + md)

  test("no backlinks and no citations means no empty sections"):
    val md = ArticleMarkdown.article(view("# T\n\nprose"))
    assert(!md.contains("Backlinks") && !md.contains("Sources cited"), md)

  test("list renders slug, title, meta and the page size against the total"):
    val row = Article.Summary(
      "sea_peoples",
      "The Sea Peoples",
      "concept",
      Some("A lede."),
      List("collapse"),
      List("Levant"),
      Some(-1220),
      Some(-1100)
    )
    assert(ArticleMarkdown.list(Article.List(1, List(row))).startsWith("### 1 article(s)"))
    assert(ArticleMarkdown.list(Article.List(9, List(row))).startsWith("### 1 of 9 article(s)"))
    assertEquals(ArticleMarkdown.list(Article.List(0, Nil)), "_No matching articles._")

  test("a cut article list carries the offset page fields and says where the next page starts"):
    val row = Article.Summary("sea_peoples", "The Sea Peoples", "concept", None, Nil, Nil, None, None)
    val first = Article.List.of(9, List(row, row), 0)
    assertEquals((first.offset, first.next), (0, Some(2)))
    assert(
      ArticleMarkdown.list(first).endsWith("_showing 1–2 of 9 — next: offset=2 — full set: limit=9_"),
      ArticleMarkdown.list(first)
    )
    val last = Article.List.of(9, List(row), 8)
    assertEquals(last.next, None)
    assert(ArticleMarkdown.list(Article.List.of(9, Nil, 20)).contains("offset=20 is past the end"))

  // ── section-targeted append (A16) ───────────────────────────────────────────────────────────

  test("appendUnderSection inserts text under the matching heading"):
    val body = "# Title\n\n## Sources\n\nSource A.\n\n## Disagreements\n\nOld disagreement.\n\n## Conclusion\n\nDone."
    val result = Articles.appendUnderSection(body, "## Disagreements", "New disagreement content.").toOption.get
    assert(result.contains("Old disagreement."), result)
    assert(result.contains("New disagreement content."), result)
    // the new content should be between Disagreements and Conclusion
    val disIdx = result.indexOf("## Disagreements")
    val newIdx = result.indexOf("New disagreement content.")
    val conIdx = result.indexOf("## Conclusion")
    assert(disIdx < newIdx && newIdx < conIdx, result)

  test("appendUnderSection matches case-insensitively and ignores leading #"):
    val body = "# Title\n\n## Disagreements\n\nOld.\n\n## End"
    assert(Articles.appendUnderSection(body, "disagreements", "New.").isRight)
    assert(Articles.appendUnderSection(body, "## DISAGREEMENTS", "New.").isRight)

  test("appendUnderSection returns an error when the heading is not found"):
    val body = "# Title\n\n## Sources\n\nData."
    val result = Articles.appendUnderSection(body, "## Missing", "New content.")
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("no heading matching"))

  test("appendUnderSection appends at the end when the section is the last heading"):
    val body = "# Title\n\n## Last Section\n\nSome content."
    val result = Articles.appendUnderSection(body, "## Last Section", "Added text.").toOption.get
    assert(result.endsWith("Added text."), result)

  test("patch_article with section targets the append under the right heading"):
    val body = "# Sea Peoples\n\n## Evidence\n\nData.\n\n## Disagreements\n\nOld."
    val ex = existing.copy(body = body)
    val p = Article.Patch(append = Some("Fresh dispute."), section = Some("## Disagreements"))
    val a = Articles.applyPatch(ex, p).toOption.get
    val disIdx = a.body.indexOf("## Disagreements")
    val newIdx = a.body.indexOf("Fresh dispute.")
    assert(disIdx < newIdx, a.body)
    assert(a.body.contains("Old."), a.body)

  // ── stand-in summaries (three articles shipped "placeholder" as their card text) ─────────────

  test("a stand-in summary is refused on put and on patch, with what to do instead"):
    for s <- List("placeholder", "TODO", " tbd ", "FIXME.", "Lorem ipsum dolor sit amet", "[placeholder]") do
      val put = Articles.applyPut("x", Article.Put("T", "concept", "b", summary = Some(s)))
      assert(put.left.exists(_.contains("stand-in")), s"put accepted '$s': $put")
      val patch = Articles.applyPatch(existing, Article.Patch(summary = Some(s)))
      assert(patch.isLeft, s"patch accepted '$s'")

  test("a real summary that merely mentions a stand-in word is kept"):
    val s = "The placeholder names in Linear B are discussed; TODO lists are not."
    assertEquals(
      Articles.applyPut("x", Article.Put("T", "concept", "b", summary = Some(s))).map(_.summary),
      Right(Some(s))
    )

  test("without a summary, a body that opens with `## Summary` supplies it"):
    val body = "## Summary\n\n*Hephaestus is the divine craftsman.*\n\n## Evidence\n\nMore."
    assertEquals(Articles.lede(body), Some("Hephaestus is the divine craftsman."))
    assertEquals(
      Articles.applyPut("x", Article.Put("T", "concept", body)).map(_.summary),
      Right(Some("Hephaestus is the divine craftsman."))
    )

  // #366: the page showed "mystery_cults, greek_religion, …" verbatim under the title
  test("the line under an article's title shows readable tag and region links, not raw snake_case"):
    val line = ArticleMarkdown.metaLine(
      "concept",
      List("Greece", "Near East"),
      "1500 BCE – 400",
      List("mystery_cults", "ergot_hypothesis")
    )
    assertEquals(
      line,
      "_concept · [Greece](/#place=Greece), [Near East](/#place=Near%20East) · 1500 BCE – 400 · " +
        "[mystery cults](/#tag=mystery_cults), [ergot hypothesis](/#tag=ergot_hypothesis)_"
    )
    assertEquals(ArticleMarkdown.metaLine("", Nil, "", Nil), "")
