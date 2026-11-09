package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import com.histmersion.api.ListPatch
import com.histmersion.config.*
import com.histmersion.api.research.CritiqueReason
import com.histmersion.api.catalogue.Period
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.Write
import com.histmersion.api.catalogue.Year
import com.histmersion.api.Api
import com.histmersion.api.library.Filter
import com.histmersion.api.library.Citekey

/** `histmersion article patch` flag → [[ArticlePatch]]. Pure: the semantics of "only what you pass changes" is asserted
  * without a server.
  */
class ArticlePatchesSuite extends munit.FunSuite:

  private def build(
      json: Option[ArticlePatches.PatchJson] = None,
      title: Option[Article.Title] = None,
      summary: Option[Article.SummaryText] = None,
      kind: Option[Article.Kind.Wire] = None,
      bodyFile: Option[ArticlePatches.TextFile] = None,
      appendFile: Option[ArticlePatches.TextFile] = None,
      tagsAdd: Option[ArticlePatches.CsvText] = None,
      tagsRemove: Option[ArticlePatches.CsvText] = None,
      tagsReplace: Option[ArticlePatches.CsvText] = None,
      regions: Option[ArticlePatches.CsvText] = None,
      period: Option[ArticlePatches.FilterFlags.Period] = None,
      ifUpdatedAt: Option[Article.UpdatedAt] = None
  ) =
    ArticlePatches.build(
      json,
      title,
      summary,
      kind,
      bodyFile,
      appendFile,
      tagsAdd,
      tagsRemove,
      tagsReplace,
      regions,
      period,
      ifUpdatedAt
    )

  test("no flags at all is an empty patch — nothing is touched"):
    assertEquals(build(), Right(Article.Patch()))

  test("tag flags become one ListPatch; untouched parts stay absent"):
    val p = build(
      tagsAdd = Some(ArticlePatches.CsvText("sea-peoples, collapse")),
      tagsRemove = Some(ArticlePatches.CsvText("old"))
    ).toOption.get
    assertEquals(
      p.tags,
      Some(
        ListPatch(List(Write.ListItem("sea-peoples"), Write.ListItem("collapse")), List(Write.ListItem("old")), None)
      )
    )
    assertEquals(p.body, None)
    assertEquals(p.summary, None)

  test("--tags-replace sets the baseline"):
    assertEquals(
      build(tagsReplace = Some(ArticlePatches.CsvText("a,b"))).toOption.get.tags,
      Some(ListPatch(Nil, Nil, Some(List(Write.ListItem("a"), Write.ListItem("b")))))
    )

  test("--regions replaces the list, --title/--summary/--kind replace themselves"):
    val p =
      build(
        regions = Some(ArticlePatches.CsvText("Levant, Egypt")),
        title = Some(Article.Title("T")),
        summary = Some(Article.SummaryText("S")),
        kind = Some(Article.Kind.Wire("report"))
      ).toOption.get
    assertEquals(p.regions, Some(List("Levant", "Egypt")))
    assertEquals((p.title, p.summary, p.kind), (Some("T"), Some("S"), Some("report")))

  test("--period parses signed years, and refuses anything else"):
    assertEquals(
      build(period = Some(ArticlePatches.FilterFlags.Period("-1300,-1100"))).toOption.get.period,
      Some(Period.Input(Year(-1300), Year(-1100)))
    )
    assert(build(period = Some(ArticlePatches.FilterFlags.Period("-1300"))).isLeft)
    assert(build(period = Some(ArticlePatches.FilterFlags.Period("a,b"))).isLeft)

  test("--json is the baseline and the flags override the parts they name"):
    val p =
      build(
        json = Some(ArticlePatches.PatchJson("""{"summary":"from json","title":"json title"}""")),
        title = Some(Article.Title("flag title"))
      ).toOption.get
    assertEquals(p.title, Some("flag title"))
    assertEquals(p.summary, Some("from json"))

  test("bad --json is a usage error, not a crash"):
    val e = build(json = Some(ArticlePatches.PatchJson("{oops"))).left.toOption.get
    assertEquals(e.exitCode, ExitCode.Usage)

  test("a missing --body-file is a usage error"):
    assertEquals(
      build(bodyFile = Some(ArticlePatches.TextFile("/nope/nothing.md"))).left.toOption.get.exitCode,
      ExitCode.Usage
    )

  test("--append-file becomes `append`, --body-file becomes `body`"):
    val dir = java.nio.file.Files.createTempDirectory("histmersion-article-patch")
    val body = dir.resolve("b.md"); java.nio.file.Files.writeString(body, "# New body")
    val app = dir.resolve("a.md"); java.nio.file.Files.writeString(app, "## Added")
    val p = build(
      bodyFile = Some(ArticlePatches.TextFile(body.toString)),
      appendFile = Some(ArticlePatches.TextFile(app.toString))
    ).toOption.get
    assertEquals(p.body, Some("# New body"))
    assertEquals(p.append, Some("## Added"))

  test("#958: --filter-* flags replace the filter, --clear-filter sends the empty one, --reason sets or removes one"):
    val base = build(json = Some(ArticlePatches.PatchJson("""{"filter":{"query":"from json"}}"""))).toOption.get
    val ff = ArticlePatches.FilterFlags(
      query = Some(Api.ArticleQuery.Text(" Theseus ")),
      topics = Some(ArticlePatches.CsvText("greek_myth, attica")),
      period = Some(ArticlePatches.FilterFlags.Period("-1500,-400")),
      extraQueries = List(ArticlePatches.FilterFlags.ExtraQueries("synoikism"))
    )
    val p = ArticlePatches
      .withCoverage(
        base,
        ff,
        List(
          ArticlePatches.ReasonText("graves2017_greek=retells the myths"),
          ArticlePatches.ReasonText("nilsson1927_minoan=")
        )
      )
      .toOption
      .get
    assertEquals(
      p.filter,
      Some(
        Article.Filter(
          query = Some(Filter.Q("Theseus")),
          topics = List(Filter.Topics("greek_myth"), Filter.Topics("attica")),
          from = Some(Year(-1500)),
          to = Some(Year(-400)),
          extraQueries = List(Article.Filter.ExtraQueries("synoikism"))
        )
      )
    )
    assertEquals(
      p.reasons,
      List(
        Coverage.Reason(Citekey("graves2017_greek"), Problem.Unclassified(Legacy.Text("retells the myths"))),
        Coverage.Reason(Citekey("nilsson1927_minoan"), Problem.Unclassified(Legacy.Text("")))
      )
    )
    // no flag: the --json filter stands
    assertEquals(
      ArticlePatches.withCoverage(base, ArticlePatches.FilterFlags(), Nil).toOption.get.filter,
      Some(Article.Filter(query = Some(Filter.Q("from json"))))
    )
    assertEquals(
      ArticlePatches
        .withCoverage(Article.Patch(), ArticlePatches.FilterFlags(clear = ArticlePatches.FilterFlags.Clear(true)), Nil)
        .toOption
        .get
        .filter,
      Some(Article.Filter())
    )
    assert(
      ArticlePatches
        .withCoverage(
          Article.Patch(),
          ArticlePatches
            .FilterFlags(query = Some(Api.ArticleQuery.Text("x")), clear = ArticlePatches.FilterFlags.Clear(true)),
          Nil
        )
        .isLeft
    )
    assert(
      ArticlePatches
        .withCoverage(Article.Patch(), ArticlePatches.FilterFlags(), List(ArticlePatches.ReasonText("no-equals-sign")))
        .isLeft
    )
    assert(
      ArticlePatches
        .withCoverage(
          Article.Patch(),
          ArticlePatches.FilterFlags(period = Some(ArticlePatches.FilterFlags.Period("-1500"))),
          Nil
        )
        .isLeft
    )

  test("§6.8: --critique-reason sets or removes one checklist pair's reason, after those of --json"):
    val base = build(json =
      Some(ArticlePatches.PatchJson("""{"critiqueReasons":[{"src":"a2000_x","dst":"b2001_y","reason":"from json"}]}"""))
    ).toOption.get
    val p = ArticlePatches
      .withCritiques(
        base,
        List(
          Problem.Unclassified(Legacy.Text("smith1987_dying>frazer1922_golden=cited for the calendar only")),
          Problem.Unclassified(Legacy.Text("[[jones2001_myth]] -> lee1990_ritual="))
        )
      )
      .toOption
      .get
    assertEquals(
      p.critiqueReasons,
      List(
        CritiqueReason(Citekey("a2000_x"), Citekey("b2001_y"), Problem.Unclassified(Legacy.Text("from json"))),
        CritiqueReason(
          Citekey("smith1987_dying"),
          Citekey("frazer1922_golden"),
          Problem.Unclassified(Legacy.Text("cited for the calendar only"))
        ),
        CritiqueReason(Citekey("jones2001_myth"), Citekey("lee1990_ritual"), Problem.Unclassified(Legacy.Text("")))
      )
    )
    assertEquals(ArticlePatches.withCritiques(base, Nil).toOption.get, base)
    val bad =
      ArticlePatches.withCritiques(Article.Patch(), List(Problem.Unclassified(Legacy.Text("smith1987_dying=no pair"))))
    assert(
      bad.left.exists(e => e.message.sentence.startsWith("--critique-reason:") && e.exitCode == ExitCode.Usage),
      bad
    )
