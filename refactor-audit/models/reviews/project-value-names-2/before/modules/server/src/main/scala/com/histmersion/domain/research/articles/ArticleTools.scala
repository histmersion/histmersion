package com.histmersion.domain.research.articles

import com.histmersion.api.aliases.AliasKinds

import chimp.server.*
import com.histmersion.api.{Api, StrictJson}
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.library.Filter
import com.histmersion.api.research.ArticleMarkdown
import com.histmersion.api.research.Article

/** The articles' MCP tools, in their place among `/mcp`'s: the list, one article read, its writes, a draft checked, its
  * coverage and review, its note and its versions. The delete is deliberately not a tool: dropping compiled knowledge
  * is a person's decision, made with `histmersion article delete`.
  */
object ArticleTools:
  type ArticleCoverageDoc = ArticleCoverageDoc.T
  object ArticleCoverageDoc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCoverageMarkdownResult = ArticleCoverageMarkdownResult.T
  object ArticleCoverageMarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleReviewDoc = ArticleReviewDoc.T
  object ArticleReviewDoc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleReviewMarkdownResult = ArticleReviewMarkdownResult.T
  object ArticleReviewMarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckEditsDoc = CheckEditsDoc.T
  object CheckEditsDoc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckEditsResult = CheckEditsResult.T
  object CheckEditsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import com.histmersion.util.mcp.ToolInputs.given

  def apply(articles: ArticlesControllers) =
    // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────
    // Shared preamble: the ONE place the writing rules are named, so all four tools point an agent at
    // the same document instead of paraphrasing it differently.
    val ArticleRules =
      "Articles are the COMPILED KNOWLEDGE of this corpus — the concept/myth/culture/region/timeline/" +
        "report pages a reader reads instead of the papers. Write them by the rules in " +
        "docs/writing-guide.md: exhaust the filter (every source it returned is used or explicitly " +
        "excluded), surface disagreements with years, EVERY PARAGRAPH CITES at least one source as " +
        "[[citekey]] (a paragraph with no citekey is deleted, not rewritten), plain technical language " +
        "with no filler, specialist terms defined in one clause on first use, and the sections " +
        "question / what the sources say / disagreements / what is unresolved. Record the article's FILTER " +
        "(`filter`) and a REASON for each of its sources the body does not cite (`reasons`): the page then " +
        "generates the Not used list, its counts and the Method note — never write those by hand."
    val LinkRules =
      "Links are Obsidian wikilinks in the body: [[citekey]] cites a source (it renders as a link to " +
        "that source and appears in the article's 'Sources cited' footer), [[other_slug]] points at " +
        "another article (and shows up in its backlinks). Nothing else maintains the link graph."
    val FormatRules =
      "The Markdown is CommonMark plus pipe tables (a blank line before a table, a `|---|` row with one " +
        "cell per column); every write answers with a 'Formatting' list of what will not display as " +
        "written (line, column, excerpt) — fix the errors; `strict: true` refuses a write that has one."
    val PatchFirstArticle =
      "To extend or correct an article that already exists, use patch_article — with `append` to add a " +
        "section, or `body` to rewrite it. NEVER re-upload a whole article with put_article: that " +
        "erases anything you did not resend."

    val listArticles =
      tool("list_articles")
        .description(
          "List/search the compiled articles. `kind` filters to concept|myth|culture|region|timeline|" +
            "report, `tag` to the articles citing a source with that tag, `title` to a title " +
            "substring, `query` is full text over the article's title, summary and body (websearch " +
            "syntax: \"phrase\", -not, or) plus a title substring. Start here before compiling " +
            "anything: an article on the topic may already exist, in which case you extend it with " +
            s"patch_article rather than starting a new one. $ArticleRules"
        )
        .input[ListArticlesInput]
        .handle(in => ToolResult.text(ArticleMarkdown.list(articles.list(in.toQuery))))

    val getArticle =
      tool("get_article")
        .description(
          "Fetch one compiled article by slug. `parts` (default: all) selects which sections to " +
            "return: body (the Markdown prose), summary (the one-paragraph lede), cited (sources it " +
            "cites with title/year/status — teasers truncated to 200 chars, markdown stripped), " +
            "backlinks (other articles and source notes linking TO it — same teaser format). " +
            "Pass a comma-separated list to get several parts (e.g. `parts: \"body,cited\"`). `coverage` is the " +
            "generated Not used list and Method note (from the article's recorded filter; see article_coverage). " +
            "Kinds: concept, myth, culture, region, timeline, report, index (the wiki's MOC pages). " +
            s"$LinkRules Read the article before patching it. $PatchFirstArticle"
        )
        .input[GetArticleInput]
        .handle { in =>
          articles.get(in.slug) match
            case Right(v) =>
              val parts = in.parts.map(_.split(',').map(_.trim.toLowerCase).toList).getOrElse(Nil)
              ToolResult.text(ArticleMarkdown.article(v, parts))
            case Left(e) => ToolResult.text(s"_No article '${in.slug}' — find a slug with list_articles._")
        }

    val putArticle =
      tool("put_article")
        .description(
          "Create or REPLACE a whole article under one slug (lower-case, underscores: sea_peoples). " +
            "Idempotent and destructive — everything you omit is erased — so use it to CREATE an " +
            "article or to deliberately rewrite one from scratch. `kind` is concept|myth|culture|" +
            "region|timeline|report, `body` is the Markdown (open it with a '# Title' heading), " +
            "`summary` is the one-paragraph lede (omit it and the first paragraph is used), `period` " +
            "is signed years, BCE negative. " +
            s"$ArticleRules $LinkRules $FormatRules $PatchFirstArticle"
        )
        .input[PutArticleInput]
        .handle { in =>
          articles.put(in.slug, in.article, in.strict) match
            case Right(v) =>
              ToolResult.text(
                Aliases.note(AliasKinds.ArticleRecords, in.slug.toLowerCase, v.article.slug)(
                  ArticleMarkdown.article(v)
                )
              )
            case Left(e) => McpOut.error(e)
        }

    val patchArticle =
      tool("patch_article")
        .description(
          "Extend or correct an existing article: send ONLY what changes, everything else is left " +
            "exactly as it is. `append` adds Markdown — at the end by default, or under a named " +
            "`section` heading (e.g. `section:\"## Disagreements\"`) so the new content lands in the " +
            "right place without a full body rewrite. `body` rewrites the prose, `title`, `summary`, " +
            "`kind`, `regions`, `period{start,end}` and `tags{add,remove,replace}` touch just " +
            "themselves. `filter` replaces the filter the article is compiled from ({} clears it); `reasons` " +
            "[{citekey, reason}] sets why each named source of that filter is left out (an empty reason removes " +
            "one) — the page generates the Not used list and the Method note from the two. `critiqueReasons` " +
            "[{src, dst, reason}] sets why one disagreement of the critique checklist (an edge src→dst of kind " +
            "challenges/refutes/updates/superseded_by touching a cited source) does not bear on the article (empty removes; " +
            "stored beside the article, it leaves updatedAt as it is). Pass ifUpdatedAt with the updatedAt you last read to be told — instead of " +
            s"silently clobbering — when someone else changed the article meanwhile. $ArticleRules " +
            s"$LinkRules $FormatRules"
        )
        .input[PatchArticleInput]
        .handle { in =>
          articles.patch(in.slug, in.patch, in.strict) match
            case Right(v) =>
              ToolResult.text(
                Aliases.note(AliasKinds.ArticleRecords, in.slug.toLowerCase, v.article.slug)(
                  ArticleMarkdown.article(v)
                )
              )
            case Left(e) => McpOut.error(e)
        }

    val checkEdits =
      tool("check_edits")
        .description(CheckEditsDoc)
        .input[CheckEditsInput]
        .handle(in => ToolResult.text(ArticleTools.checkEdits(articles, in)))

    val articleCoverage =
      tool("article_coverage")
        .description(ArticleCoverageDoc)
        .input[ArticleCoverageInput]
        .handle(in => ToolResult.text(articleCoverageMarkdown(articles, in)))

    val articleReview =
      tool("article_review")
        .description(ArticleReviewDoc)
        .input[ArticleReviewInput]
        .handle(in => ToolResult.text(articleReviewMarkdown(articles, in)))

    val exportArticle =
      tool("export_article")
        .description(
          "Render an article as the Markdown note it would be on disk at wiki/<kind>/<slug>.md: YAML " +
            "frontmatter (type, slug, title, summary, regions, tags, period_start/period_end, and the filter " +
            "and reasons it is compiled from) plus the body, then the generated Not used and Method sections " +
            "between markers an import drops. This is the git-friendly mirror of the database and it round-trips back in through " +
            "seed."
        )
        .input[ExportArticleInput]
        .handle { in =>
          articles.exportNote(in.slug) match
            case Right(text) => ToolResult.text(text)
            case Left(e)     => McpOut.error(e)
        }

    val articleVersions =
      tool("article_versions")
        .description(
          "List the edit history of one article (newest first): version id, date, word count and the " +
            "first 200 chars of the summary at that point. Use it to see how an article changed over " +
            "time, or to find the version id of a body you want to recover with `article_version`."
        )
        .input[ArticleVersionsInput]
        .handle { in =>
          articles.versions(in.slug) match
            case Right(vs) => ToolResult.text(ArticleMarkdown.versions(in.slug, vs))
            case Left(e)   => McpOut.error(e)
        }

    val articleVersion =
      tool("article_version")
        .description(
          "Fetch the full body of one historical version of an article. Use it to read what the article " +
            "said before a rewrite, or to recover text that a bad patch overwrote."
        )
        .input[ArticleVersionInput]
        .handle { in =>
          articles.version(in.slug, in.versionId) match
            case Right(v) => ToolResult.text(ArticleMarkdown.version(v))
            case Left(e)  => McpOut.error(e)
        }

    List(
      listArticles,
      getArticle,
      putArticle,
      patchArticle,
      checkEdits,
      articleCoverage,
      articleReview,
      exportArticle,
      articleVersions,
      articleVersion
    )

  // The write tools reuse the REST DTOs verbatim, so the MCP schema and the HTTP body are the same shape — one
  // contract, two transports.
  final case class ListArticlesInput(
      kind: Option[ListArticlesInput.Kind] = None,
      query: Option[ListArticlesInput.Query] = None,
      tag: Option[ListArticlesInput.Tag] = None,
      title: Option[ListArticlesInput.Title] = None,
      limit: Option[ListArticlesInput.Limit] = None,
      offset: Option[ListArticlesInput.Offset] = None
  ) derives KindlingsCodecAsObject:
    /** `tag` is the shared Filter's (a source tag: articles CITING such a source); `query` is the article's own full
      * text — see [[Api.ArticleQuery]].
      */
    def toQuery: Api.ArticleQuery =
      Api.ArticleQuery(Filter.of(tags = tag.toList, limit = limit, offset = offset, title = title), kind, query)
  object ListArticlesInput:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tag = Tag.T
    object Tag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[ListArticlesInput] = KindlingsSchema
      .derived[ListArticlesInput]
      .schema
      .modify(_.kind)(_.description("Article kind: concept|myth|culture|region|timeline|report|index"))
      .modify(_.query)(_.description("Full-text search over title, summary and body"))
      .modify(_.tag)(_.description("Only articles citing a source that carries this tag"))
      .modify(_.title)(_.description("Case-insensitive substring of the article's title"))

  final case class GetArticleInput(slug: GetArticleInput.Slug, parts: Option[GetArticleInput.Parts] = None) derives KindlingsCodecAsObject
  object GetArticleInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Parts = Parts.T
    object Parts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[GetArticleInput] = KindlingsSchema
      .derived[GetArticleInput]
      .schema
      .modify(_.slug)(_.description("The article's slug (lowercase, e.g. steppe_hypothesis)"))
      .modify(_.parts)(
        _.description("Comma-separated parts to return: body,summary,coverage,cited,backlinks (default: all)")
      )

  /** `article_coverage` as `/mcp` and an agent step both describe it. */
  val ArticleCoverageDoc: com.histmersion.domain.research.articles.ArticleTools.ArticleCoverageDoc =
    "The coverage of one article, computed by code from the filter it records: how many sources the filter " +
      "returns, which of them the body cites, every other one with its stored reason (or 'not yet assessed'), " +
      "the status breakdown, the disagreement edges (challenges, refutes, updates, superseded_by) touching the " +
      "cited sources, the further searches, and the DRIFT — sources neither cited nor given a reason, reasons " +
      "that went stale; the CRITIQUE CHECKLIST (Disagreements checked: each edge touching a cited source, addressed " +
      "when both sources are cited in one paragraph, reasoned when a critique reason is stored, else open). Then " +
      "the Not used list and Method note exactly as the page generates them. An article " +
      "that records no filter has none (set it with patch_article `filter`)."

  /** `article_coverage`: the one handler, rendered. */
  def articleCoverageMarkdown(articles: ArticlesControllers, in: ArticleCoverageInput): com.histmersion.domain.research.articles.ArticleTools.ArticleCoverageMarkdownResult =
    articles.coverage(in.slug).fold(McpOut.refusal, Article.Coverage.markdown)

  /** `article_review` as `/mcp` describes it. */
  val ArticleReviewDoc: com.histmersion.domain.research.articles.ArticleTools.ArticleReviewDoc =
    "Where one article stands with the review panel (rules reviewer, cold reader, skeptic, mechanical checks): " +
      "its state (unreviewed, queued, in-review with the round, passed, majors-open, stopped) with what it means, " +
      "the review manager's last outcome, the research goals that link the article with their stage, the approvals " +
      "about it that wait for a person, and its review history from the review ledger — every finding by run and " +
      "round, with reviewer, kind, place, problem and what became of it (fixed, rejected with the record's sentence, " +
      "won't fix, still open). CLI: histmersion article review."

  /** `article_review`: the one handler, rendered. */
  def articleReviewMarkdown(articles: ArticlesControllers, in: ArticleReviewInput): com.histmersion.domain.research.articles.ArticleTools.ArticleReviewMarkdownResult =
    articles.review(in.slug).fold(McpOut.refusal, Article.ReviewView.markdown)

  final case class ArticleReviewInput(slug: ArticleReviewInput.Slug) derives KindlingsCodecAsObject
  object ArticleReviewInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[ArticleReviewInput] = KindlingsSchema
      .derived[ArticleReviewInput]
      .schema
      .modify(_.slug)(_.description("The article's slug"))

  final case class ArticleCoverageInput(slug: ArticleCoverageInput.Slug) derives KindlingsCodecAsObject
  object ArticleCoverageInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[ArticleCoverageInput] = KindlingsSchema
      .derived[ArticleCoverageInput]
      .schema
      .modify(_.slug)(_.description("The article's slug"))

  /** `check_edits` as `/mcp` and an agent step both describe it. */
  val CheckEditsDoc: com.histmersion.domain.research.articles.ArticleTools.CheckEditsDoc =
    "Dry-run a change to an article and see what the review checks would say — nothing is written. `edits` " +
      "are applied in order to the CURRENT stored body: each is {op: replace|insertAfter|insertBefore|delete, " +
      "anchor: text copied exactly from the body (it must occur once; whitespace is normalised), text}. Or pass " +
      "`body`, a whole draft, instead (the article need not exist yet). The answer: each edit's fate (applies, " +
      "or why not), then the problems of the result — links to nothing, uncited paragraphs, a Sources cited " +
      "heading, banned filler, formatting errors it introduces — and whether one is fatal. These are exactly the " +
      "checks code runs on a review fixer's answer."

  /** `check_edits`: the one handler, rendered. */
  def checkEdits(articles: ArticlesControllers, in: CheckEditsInput): com.histmersion.domain.research.articles.ArticleTools.CheckEditsResult =
    articles
      .check(in.slug, Article.Edits.ArticleCheckRequest(in.edits, in.body, in.kind))
      .fold(McpOut.refusal, Article.Edits.markdown)

  final case class CheckEditsInput(
      slug: CheckEditsInput.Slug,
      edits: List[Article.Edits.EditAnswer] = Nil,
      body: Option[CheckEditsInput.Body] = None,
      kind: Option[CheckEditsInput.Kind] = None
  ) derives KindlingsCodecAsObject
  object CheckEditsInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Body = Body.T
    object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[CheckEditsInput] = KindlingsSchema
      .derived[CheckEditsInput]
      .schema
      .modify(_.slug)(_.description("The article's slug"))
      .modify(_.edits)(_.description("Edits to the current stored body, applied in order"))
      .modify(_.body)(_.description("Instead of edits: a whole draft body"))
      .modify(_.kind)(
        _.description(
          "Check the section set of this kind instead of the article's (index = none, for a fragment to be appended)"
        )
      )

  final case class PutArticleInput(slug: PutArticleInput.Slug, article: Article.Put, strict: PutArticleInput.Strict = false)
  object PutArticleInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Strict = Strict.T
    object Strict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[PutArticleInput] = KindlingsSchema
      .derived[PutArticleInput]
      .schema
      .modify(_.strict)(_.description(com.histmersion.domain.library.LibraryTools.StrictDoc))
    given Codec.AsObject[PutArticleInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PutArticleInput]) // #813

  final case class PatchArticleInput(slug: PatchArticleInput.Slug, patch: Article.Patch, strict: PatchArticleInput.Strict = false)
  object PatchArticleInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Strict = Strict.T
    object Strict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[PatchArticleInput] = KindlingsSchema
      .derived[PatchArticleInput]
      .schema
      .modify(_.strict)(_.description(com.histmersion.domain.library.LibraryTools.StrictDoc))
    given Codec.AsObject[PatchArticleInput] =
      StrictJson.codec(KindlingsCodecAsObject.derived[PatchArticleInput]) // #813

  final case class ExportArticleInput(slug: ExportArticleInput.Slug) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object ExportArticleInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ArticleVersionsInput(slug: ArticleVersionsInput.Slug) derives KindlingsCodecAsObject
  object ArticleVersionsInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[ArticleVersionsInput] = KindlingsSchema
      .derived[ArticleVersionsInput]
      .schema
      .modify(_.slug)(_.description("The article's slug (lowercase, e.g. steppe_hypothesis)"))

  final case class ArticleVersionInput(slug: ArticleVersionInput.Slug, versionId: ArticleVersionInput.VersionId) derives KindlingsCodecAsObject
  object ArticleVersionInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VersionId = VersionId.T
    object VersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[ArticleVersionInput] = KindlingsSchema
      .derived[ArticleVersionInput]
      .schema
      .modify(_.slug)(_.description("The article's slug"))
      .modify(_.versionId)(_.description("The version id from article_versions"))
