package com.histmersion
package api

import com.histmersion.util.problem.{NonEmptyVector, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.Period
import com.histmersion.api.research.{Article, ArticleMarkdown, Articles, Backlink, CitedSource}
import com.histmersion.api.library.Source
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.personal.UnreadPage

/** The pure write rules for articles (plan §5.2 stream 2.7): validation, full replace, and the selective merge that
  * makes `patch_article` safe to call without re-uploading the prose.
  */
class ArticlesSuite extends munit.FunSuite:

  private val existing = Article(
    slug = Article.Slug("sea_peoples"),
    title = Article.Title("The Sea Peoples"),
    kind = Article.KindValue("concept"),
    body = Article.Body("# The Sea Peoples\n\nMedinet Habu [[cline2014_year]]."),
    summary = Some(Article.SummaryText("A loose label.")),
    tags = List(Article.Tags("collapse"), Article.Tags("levant")),
    regions = List(Article.Regions("Levant")),
    period = Some(Period(Year(-1220), Year(-1100))),
    updatedAt = Some(Article.UpdatedAt("2026-08-25T10:00:00.000000Z"))
  )

  // ── put ──────────────────────────────────────────────────────────────────────────────────────
  test("put normalises the slug and derives the summary from the lede when none is given"):
    val a = Articles
      .applyPut(
        Article.Slug(" Sea_Peoples "),
        Article.Put(
          title = Article.Title(" The Sea Peoples "),
          kind = Article.Kind.Wire("Concept"),
          body = Article.Body("# The Sea Peoples\n\n*A loose label.*")
        )
      )
      .toOption
      .get
    assertEquals(a.slug, "sea_peoples")
    assertEquals(a.kind, "concept")
    assertEquals(a.title, "The Sea Peoples")
    assertEquals(a.summary, Some("A loose label."))

  test("put rejects a slug that is not a filename, an empty title and an inverted period"):
    assert(
      Articles
        .applyPut(
          Article.Slug("Sea Peoples!"),
          Article.Put(Article.Title("T"), Article.Kind.Wire("concept"), Article.Body("b"))
        )
        .isLeft
    )
    assert(
      Articles
        .applyPut(Article.Slug("x"), Article.Put(Article.Title("  "), Article.Kind.Wire("concept"), Article.Body("b")))
        .isLeft
    )
    assertEquals(
      Articles
        .applyPut(
          Article.Slug("x"),
          Article.Put(
            Article.Title("T"),
            Article.Kind.Wire("concept"),
            Article.Body("b"),
            periodStart = Some(Year(-100)),
            periodEnd = Some(Year(-900))
          )
        )
        .left
        .toOption,
      Some(
        Problem.Invalid.Inconsistent.here(
          NonEmptyVector.of(Problem.Field("periodStart"), Problem.Field("periodEnd")),
          Problem.Invalid.Exclusion.Ordered
        )
      )
    )

  test("put needs both period bounds or neither"):
    assert(
      Articles
        .applyPut(
          Article.Slug("x"),
          Article
            .Put(Article.Title("T"), Article.Kind.Wire("concept"), Article.Body("b"), periodStart = Some(Year(-100)))
        )
        .isLeft
    )
    assertEquals(
      Articles
        .applyPut(Article.Slug("x"), Article.Put(Article.Title("T"), Article.Kind.Wire("concept"), Article.Body("b")))
        .toOption
        .get
        .period,
      None
    )

  // ── patch ────────────────────────────────────────────────────────────────────────────────────
  test("an empty patch changes nothing"):
    assertEquals(Articles.applyPatch(existing, Article.Patch()), Right(existing))

  test("tags add/remove/replace leave the body and everything else alone"):
    val a = Articles
      .applyPatch(
        existing,
        Article.Patch(tags =
          Some(ListPatch(add = List(Write.ListItem("sea-peoples")), remove = List(Write.ListItem("levant"))))
        )
      )
      .toOption
      .get
    assertEquals(a.tags, List("collapse", "sea-peoples"))
    assertEquals(a.body, existing.body)
    assertEquals(a.summary, existing.summary)
    assertEquals(a.period, existing.period)

  test("append extends the article instead of re-uploading it"):
    val a =
      Articles
        .applyPatch(
          existing,
          Article.Patch(append = Some(Article.Patch.Append("## New evidence\n\n[[haak2015_massive]]")))
        )
        .toOption
        .get
    assert(a.body.value.startsWith("# The Sea Peoples"), a.body)
    assert(a.body.value.endsWith("## New evidence\n\n[[haak2015_massive]]"), a.body)
    assert(a.body.value.contains("Medinet Habu"), "the original prose survives an append")

  test("body replaces, and body + append apply in that order"):
    val a = Articles
      .applyPatch(
        existing,
        Article.Patch(body = Some(Article.Body("# New")), append = Some(Article.Patch.Append("tail")))
      )
      .toOption
      .get
    assertEquals(a.body, "# New\n\ntail")

  test("period and kind are validated on the way in"):
    assert(Articles.applyPatch(existing, Article.Patch(period = Some(Period.Input(Year(-100), Year(-900))))).isLeft)
    assert(Articles.applyPatch(existing, Article.Patch(kind = Some(Article.Kind.Wire("not a kind")))).isLeft)
    assertEquals(
      Articles
        .applyPatch(existing, Article.Patch(period = Some(Period.Input(Year(-1300), Year(-1100)))))
        .toOption
        .get
        .period,
      Some(Period(Year(-1300), Year(-1100)))
    )

  test("ifUpdatedAt: matching passes, stale fails with a message telling you what to do"):
    assertEquals(Articles.precondition(existing, Article.Patch(ifUpdatedAt = existing.updatedAt)), Right(()))
    val e =
      Articles.precondition(existing, Article.Patch(ifUpdatedAt = Some(Article.UpdatedAt("older")))).left.toOption.get
    assert(
      e match
        case Problem.Conflict.Stale(t, key, sent, _) =>
          t == Problem.TypeName.of(Article.Slug) && key == Problem.Raw("sea_peoples") && sent == Problem.Conflict.Stamp(
            "older"
          )
        case _ => false
      ,
      e
    )

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
      existing.modify(_.updatedAt).setTo(None)
    )

  // ── the Markdown surface (MCP, CLI and — through flexmark — the HTML page) ───────────────────
  private def view(body: Article.Body, backs: List[Backlink] = Nil, cited: List[CitedSource] = Nil) =
    Article.View(
      Article.Row(
        Article.Slug("sea_peoples"),
        Article.Title("The Sea Peoples"),
        Article.Kind.Wire("concept"),
        body,
        Some(Article.SummaryText("A lede.")),
        List(Article.Tags("collapse")),
        List(Article.Regions("Levant")),
        Some(Year(-1220)),
        Some(Year(-1100))
      ),
      backs,
      cited
    )

  test("the metadata line goes UNDER the article's own title, never above it"):
    val md = ArticleMarkdown.article(view(Article.Body("# The Sea Peoples\n\nprose")))
    assertEquals(
      md,
      "# The Sea Peoples\n\n_concept · [Levant](/#place=Levant) · 1220 BCE–1100 BCE · " +
        "[collapse](/#tag=collapse)_\n\nprose"
    )

  test("a body with no heading of its own gets one from the title"):
    val md = ArticleMarkdown.article(view(Article.Body("prose only")))
    assert(md.startsWith("# The Sea Peoples\n\n_concept"), md)
    assert(md.endsWith("prose only"), md)

  test("backlinks and the cited-sources footer render as wikilinks the page can resolve"):
    val md = ArticleMarkdown.article(
      view(
        Article.Body("# T\n\nprose"),
        backs = List(
          Backlink(Backlink.Kind("article"), Backlink.Id("bronze_age_collapse"), Some(Backlink.Title("LBA Collapse")))
        ),
        cited = List(
          CitedSource(
            Citekey("cline2014_year"),
            (Some("1177 B.C.")).map(Title.apply),
            (Some(2014)).map(Year.apply),
            Some(Source.Status.Foundational)
          ),
          CitedSource(Citekey("zz_ghost"), None, None, None)
        )
      )
    )
    assert(md.contains("## Backlinks (1)\n\n- [[bronze_age_collapse]] — LBA Collapse"), md)
    assert(md.contains("- [[cline2014_year]] — 2014 · _1177 B.C._ · **foundational**"), md)
    assert(md.contains("_(not in the corpus)_"), "a dangling citation is shown, not dropped: " + md)

  test("no backlinks and no citations means no empty sections"):
    val md = ArticleMarkdown.article(view(Article.Body("# T\n\nprose")))
    assert(!md.contains("Backlinks") && !md.contains("Sources cited"), md)

  test("list renders slug, title, meta and the page size against the total"):
    val row = Article.Summary(
      Article.Slug("sea_peoples"),
      Article.Title("The Sea Peoples"),
      Article.Kind.Wire("concept"),
      Some(Article.SummaryText("A lede.")),
      List(Article.Tags("collapse")),
      List(Article.Regions("Levant")),
      Some(Year(-1220)),
      Some(Year(-1100))
    )
    assert(ArticleMarkdown.list(Article.List(RowCount(1), List(row))).startsWith("### 1 article(s)"))
    assert(ArticleMarkdown.list(Article.List(RowCount(9), List(row))).startsWith("### 1 of 9 article(s)"))
    assertEquals(ArticleMarkdown.list(Article.List(RowCount(0), Nil)), "_No matching articles._")

  test("a cut article list carries the offset page fields and says where the next page starts"):
    val row = Article.Summary(
      Article.Slug("sea_peoples"),
      Article.Title("The Sea Peoples"),
      Article.Kind.Wire("concept"),
      None,
      Nil,
      Nil,
      None,
      None
    )
    val first = Article.List.of(Article.List.Total(9), List(row, row), UnreadPage.Offset(0))
    assertEquals(((first.offset.value: Int), first.next.map(n => (n.value: Int))), (0, Some(2)))
    assert(
      ArticleMarkdown.list(first).endsWith("_showing 1–2 of 9 — next: offset=2 — full set: limit=9_"),
      ArticleMarkdown.list(first)
    )
    val last = Article.List.of(Article.List.Total(9), List(row), UnreadPage.Offset(8))
    assertEquals(last.next, None)
    assert(
      ArticleMarkdown
        .list(Article.List.of(Article.List.Total(9), Nil, UnreadPage.Offset(20)))
        .contains("offset=20 is past the end")
    )

  // ── section-targeted append (A16) ───────────────────────────────────────────────────────────

  test("appendUnderSection inserts text under the matching heading"):
    val body = Article.Body(
      "# Title\n\n## Sources\n\nSource A.\n\n## Disagreements\n\nOld disagreement.\n\n## Conclusion\n\nDone."
    )
    val result = Articles
      .appendUnderSection(body, Article.Section("## Disagreements"), Article.Prose("New disagreement content."))
      .toOption
      .get
    assert(result.value.contains("Old disagreement."), result)
    assert(result.value.contains("New disagreement content."), result)
    // the new content should be between Disagreements and Conclusion
    val disIdx = result.value.indexOf("## Disagreements")
    val newIdx = result.value.indexOf("New disagreement content.")
    val conIdx = result.value.indexOf("## Conclusion")
    assert(disIdx < newIdx && newIdx < conIdx, result)

  test("appendUnderSection matches case-insensitively and ignores leading #"):
    val body = Article.Body("# Title\n\n## Disagreements\n\nOld.\n\n## End")
    assert(Articles.appendUnderSection(body, Article.Section("disagreements"), Article.Prose("New.")).isRight)
    assert(Articles.appendUnderSection(body, Article.Section("## DISAGREEMENTS"), Article.Prose("New.")).isRight)

  test("appendUnderSection returns an error when the heading is not found"):
    val body = Article.Body("# Title\n\n## Sources\n\nData.")
    val result = Articles.appendUnderSection(body, Article.Section("## Missing"), Article.Prose("New content."))
    assert(result.isLeft)
    assertEquals(
      result.left.toOption,
      Some(Problem.NotFound.Entity(Problem.TypeName.of(Article.Section), Problem.Raw("## Missing")))
    )

  test("appendUnderSection appends at the end when the section is the last heading"):
    val body = Article.Body("# Title\n\n## Last Section\n\nSome content.")
    val result =
      Articles.appendUnderSection(body, Article.Section("## Last Section"), Article.Prose("Added text.")).toOption.get
    assert(result.value.endsWith("Added text."), result)

  test("patch_article with section targets the append under the right heading"):
    val body = Article.Body("# Sea Peoples\n\n## Evidence\n\nData.\n\n## Disagreements\n\nOld.")
    val ex = existing.modify(_.body).setTo(body)
    val p = Article.Patch(
      append = Some(Article.Patch.Append("Fresh dispute.")),
      section = Some(Article.Patch.Section("## Disagreements"))
    )
    val a = Articles.applyPatch(ex, p).toOption.get
    val disIdx = a.body.value.indexOf("## Disagreements")
    val newIdx = a.body.value.indexOf("Fresh dispute.")
    assert(disIdx < newIdx, a.body)
    assert(a.body.value.contains("Old."), a.body)

  // ── stand-in summaries (three articles shipped "placeholder" as their card text) ─────────────

  test("a stand-in summary is refused on put and on patch, with what to do instead"):
    for s <- List("placeholder", "TODO", " tbd ", "FIXME.", "Lorem ipsum dolor sit amet", "[placeholder]") do
      val put = Articles.applyPut(
        Article.Slug("x"),
        Article.Put(
          Article.Title("T"),
          Article.Kind.Wire("concept"),
          Article.Body("b"),
          summary = Some(Article.SummaryText(s))
        )
      )
      assert(put.left.exists(_.sentence.contains("stand-in")), s"put accepted '$s': $put")
      val patch = Articles.applyPatch(existing, Article.Patch(summary = Some(Article.SummaryText(s))))
      assert(patch.isLeft, s"patch accepted '$s'")

  test("a real summary that merely mentions a stand-in word is kept"):
    val s = "The placeholder names in Linear B are discussed; TODO lists are not."
    assertEquals(
      Articles
        .applyPut(
          Article.Slug("x"),
          Article.Put(
            Article.Title("T"),
            Article.Kind.Wire("concept"),
            Article.Body("b"),
            summary = Some(Article.SummaryText(s))
          )
        )
        .map(_.summary),
      Right(Some(s))
    )

  test("without a summary, a body that opens with `## Summary` supplies it"):
    val body = Article.Body("## Summary\n\n*Hephaestus is the divine craftsman.*\n\n## Evidence\n\nMore.")
    assertEquals(Articles.lede(body), Some("Hephaestus is the divine craftsman."))
    assertEquals(
      Articles
        .applyPut(Article.Slug("x"), Article.Put(Article.Title("T"), Article.Kind.Wire("concept"), body))
        .map(_.summary),
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
