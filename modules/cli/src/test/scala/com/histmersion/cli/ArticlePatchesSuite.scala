package com.histmersion.cli

import com.histmersion.api.*
import com.histmersion.config.*

/** `histmersion article patch` flag → [[ArticlePatch]]. Pure: the semantics of "only what you pass changes" is asserted
  * without a server.
  */
class ArticlePatchesSuite extends munit.FunSuite:

  private def build(
      json: Option[String] = None,
      title: Option[String] = None,
      summary: Option[String] = None,
      kind: Option[String] = None,
      bodyFile: Option[String] = None,
      appendFile: Option[String] = None,
      tagsAdd: Option[String] = None,
      tagsRemove: Option[String] = None,
      tagsReplace: Option[String] = None,
      regions: Option[String] = None,
      period: Option[String] = None,
      ifUpdatedAt: Option[String] = None
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
    assertEquals(build(), Right(ArticlePatch()))

  test("tag flags become one ListPatch; untouched parts stay absent"):
    val p = build(tagsAdd = Some("sea-peoples, collapse"), tagsRemove = Some("old")).toOption.get
    assertEquals(p.tags, Some(ListPatch(List("sea-peoples", "collapse"), List("old"), None)))
    assertEquals(p.body, None)
    assertEquals(p.summary, None)

  test("--tags-replace sets the baseline"):
    assertEquals(build(tagsReplace = Some("a,b")).toOption.get.tags, Some(ListPatch(Nil, Nil, Some(List("a", "b")))))

  test("--regions replaces the list, --title/--summary/--kind replace themselves"):
    val p =
      build(regions = Some("Levant, Egypt"), title = Some("T"), summary = Some("S"), kind = Some("report")).toOption.get
    assertEquals(p.regions, Some(List("Levant", "Egypt")))
    assertEquals((p.title, p.summary, p.kind), (Some("T"), Some("S"), Some("report")))

  test("--period parses signed years, and refuses anything else"):
    assertEquals(build(period = Some("-1300,-1100")).toOption.get.period, Some(PeriodInput(-1300, -1100)))
    assert(build(period = Some("-1300")).isLeft)
    assert(build(period = Some("a,b")).isLeft)

  test("--json is the baseline and the flags override the parts they name"):
    val p =
      build(json = Some("""{"summary":"from json","title":"json title"}"""), title = Some("flag title")).toOption.get
    assertEquals(p.title, Some("flag title"))
    assertEquals(p.summary, Some("from json"))

  test("bad --json is a usage error, not a crash"):
    val e = build(json = Some("{oops")).left.toOption.get
    assertEquals(e.exitCode, 1)

  test("a missing --body-file is a usage error"):
    assertEquals(build(bodyFile = Some("/nope/nothing.md")).left.toOption.get.exitCode, 1)

  test("--append-file becomes `append`, --body-file becomes `body`"):
    val dir = java.nio.file.Files.createTempDirectory("histmersion-article-patch")
    val body = dir.resolve("b.md"); java.nio.file.Files.writeString(body, "# New body")
    val app = dir.resolve("a.md"); java.nio.file.Files.writeString(app, "## Added")
    val p = build(bodyFile = Some(body.toString), appendFile = Some(app.toString)).toOption.get
    assertEquals(p.body, Some("# New body"))
    assertEquals(p.append, Some("## Added"))

  test("#958: --filter-* flags replace the filter, --clear-filter sends the empty one, --reason sets or removes one"):
    val base = build(json = Some("""{"filter":{"query":"from json"}}""")).toOption.get
    val ff = ArticlePatches.FilterFlags(
      query = Some(" Theseus "),
      topics = Some("greek_myth, attica"),
      period = Some("-1500,-400"),
      extraQueries = List("synoikism")
    )
    val p = ArticlePatches
      .withCoverage(base, ff, List("graves2017_greek=retells the myths", "nilsson1927_minoan="))
      .toOption
      .get
    assertEquals(
      p.filter,
      Some(
        ArticleFilter(
          query = Some("Theseus"),
          topics = List("greek_myth", "attica"),
          from = Some(-1500),
          to = Some(-400),
          extraQueries = List("synoikism")
        )
      )
    )
    assertEquals(
      p.reasons,
      List(CoverageReason("graves2017_greek", "retells the myths"), CoverageReason("nilsson1927_minoan", ""))
    )
    // no flag: the --json filter stands
    assertEquals(
      ArticlePatches.withCoverage(base, ArticlePatches.FilterFlags(), Nil).toOption.get.filter,
      Some(ArticleFilter(query = Some("from json")))
    )
    assertEquals(
      ArticlePatches.withCoverage(ArticlePatch(), ArticlePatches.FilterFlags(clear = true), Nil).toOption.get.filter,
      Some(ArticleFilter())
    )
    assert(
      ArticlePatches
        .withCoverage(ArticlePatch(), ArticlePatches.FilterFlags(query = Some("x"), clear = true), Nil)
        .isLeft
    )
    assert(ArticlePatches.withCoverage(ArticlePatch(), ArticlePatches.FilterFlags(), List("no-equals-sign")).isLeft)
    assert(ArticlePatches.withCoverage(ArticlePatch(), ArticlePatches.FilterFlags(period = Some("-1500")), Nil).isLeft)

  test("§6.8: --critique-reason sets or removes one checklist pair's reason, after those of --json"):
    val base = build(json =
      Some("""{"critiqueReasons":[{"src":"a2000_x","dst":"b2001_y","reason":"from json"}]}""")
    ).toOption.get
    val p = ArticlePatches
      .withCritiques(
        base,
        List("smith1987_dying>frazer1922_golden=cited for the calendar only", "[[jones2001_myth]] -> lee1990_ritual=")
      )
      .toOption
      .get
    assertEquals(
      p.critiqueReasons,
      List(
        CritiqueReason("a2000_x", "b2001_y", "from json"),
        CritiqueReason("smith1987_dying", "frazer1922_golden", "cited for the calendar only"),
        CritiqueReason("jones2001_myth", "lee1990_ritual", "")
      )
    )
    assertEquals(ArticlePatches.withCritiques(base, Nil).toOption.get, base)
    val bad = ArticlePatches.withCritiques(ArticlePatch(), List("smith1987_dying=no pair"))
    assert(bad.left.exists(e => e.message.startsWith("--critique-reason:") && e.exitCode == ExitCode.Usage), bad)
