package com.histmersion
package domain.research.articles

import com.histmersion.api.checks.Lint.*

import com.histmersion.util.data.checks.{CheckInput, CheckRun}

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import com.histmersion.api.checks.{FormatField, Lint}
import com.histmersion.api.research.CritiquePairing
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Edge
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** The SQL half of the articles' lint rules (the library's are its sources' `SourceCheckRepo`).
  *
  * Every rule is either a **projection** (one SQL statement yielding `(slug, detail)`, in [[projections]]) or a **fetch
  * + pure classifier** (`ArticleLint.articleFiller`, `ArticleLint.articleCritiquePairing`, …, pure and unit-tested
  * without a database). No rule takes a parameter; the statements are constant `sql"..."` fragments.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the article check tables and their row codecs")
private[articles] object ArticleCheckRepo:

  /** Rules that are one SQL statement. Ordered so the report is stable. */
  type ReportMaxItems = ReportMaxItems.T
  object ReportMaxItems extends Newtype[Int, Pure]

  val projections: Map[String, Frag] = Map(
    // The article-side twin of `dangling-edges`, resolved exactly the way the RENDERER resolves a
    // link (`ArticleLinks.hrefFor`): an exact slug, an exact citekey, or a UNIQUE `<target>_%` alias
    // — the short form the wiki writes (`[[knapp2016]]`). Whatever is left is a name with no page
    // behind it, and is drawn as plain text rather than as a link to a 404.
    DanglingArticleLinks ->
      sql"""SELECT l.src_slug, '[[' || l.target || ']] resolves to nothing'
         FROM article_links l
         WHERE l.target_kind = 'article'
           AND NOT EXISTS (SELECT 1 FROM articles a WHERE a.slug = l.target)
           AND NOT EXISTS (SELECT 1 FROM sources s WHERE s.citekey = l.target)
           AND (SELECT count(*) FROM sources s
                WHERE starts_with(s.citekey, l.target || '_')) <> 1
         ORDER BY l.src_slug, l.target"""
  )

  /** The raw fetches behind the pure classifiers, kept here so every statement this stream runs is visible in one
    * place.
    */
  val citekeysSql = sql"SELECT citekey FROM sources"
  // ── article-critique-pairing: the disagreement edges, the sources' names, the aliases ─────────
  @tolerated(LintRule.TotalOrder, "columns 1, 2, 3 are src, kind, dst: the edges' key")
  val disagreementEdgesSql =
    sql"SELECT src, kind, dst FROM edges WHERE kind IN ('challenges', 'refutes', 'updates', 'superseded_by') ORDER BY 1, 2, 3"
  val sourceNamesSql = sql"SELECT citekey, authors, year FROM sources ORDER BY citekey"
  val sourceAliasesSql = sql"SELECT alias, citekey FROM source_aliases"
  val articlesSql = sql"SELECT slug, body FROM articles ORDER BY slug"
  val articleSummariesSql = sql"SELECT slug, coalesce(summary, '') FROM articles ORDER BY slug"
  val articleTextsSql = sql"SELECT slug, body, coalesce(summary, '') FROM articles ORDER BY slug"

  /** Every rule must be runnable — the report would otherwise lie by omission. */
  def sqlOf(rule: String): List[Frag] = rule match
    case ArticleFiller | ArticleUncitedParagraph => List(articlesSql)
    case ArticleCritiquePairing => List(articlesSql, disagreementEdgesSql, sourceNamesSql, sourceAliasesSql)
    case ArticleThinSummary     => List(articleSummariesSql)
    case ArticleFormat          => List(articleTextsSql)
    case r                      => projections.get(r).toList

  private def pairs(q: Frag)(using DbCon): List[(String, String)] =
    q.query[(String, String)].run().toList

  private def triples(q: Frag)(using DbCon): List[(String, String, String)] =
    q.query[(String, String, String)].run().toList

  private def articles(q: Frag)(using DbCon): List[(Article.Slug, Article.Body)] =
    pairs(q).map((slug, body) => (Article.Slug(slug), Article.Body(body)))

  // the stored citekeys and the articles several rules classify, read at most once per run
  private val StoredCitekeys = CheckInput(citekeysSql.query[String].run().toSet)
  private val ArticleRows = CheckInput(articles(articlesSql))

  /** Whether a citekey is stored — what the formatting rules check a bare citekey against, read once per run. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def storedCitekey(using run: CheckRun)(using DbCon): Citekey => Boolean = ck => run(StoredCitekeys).contains(ck.value)

  /** One rule's report, on the run's connection; `format` is the formatting check. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def report(r: Rule, format: FormatChecker, maxItems: ReportMaxItems)(using
      run: CheckRun
  )(using DbCon): LintRuleReport =
    val rule: String = r.name.value
    val items = rule match
      case ArticleFiller           => Article.Lint.articleFiller(run(ArticleRows))
      case ArticleUncitedParagraph => Article.Lint.articleUncitedParagraphs(run(ArticleRows))
      case ArticleCritiquePairing  =>
        val p = pairingCorpusIn()
        Article.Lint.articleCritiquePairing(run(ArticleRows), p.resolve, p.edges, p.work)
      case ArticleThinSummary =>
        Article.Lint.articleThinSummary(
          pairs(articleSummariesSql).map((slug, summary) => (Article.Slug(slug), Article.SummaryText(summary)))
        )
      case ArticleFormat =>
        Lint.formatItems(
          triples(articleTextsSql).flatMap((slug, body, summary) =>
            List(
              (com.histmersion.api.library.Citekey(slug), FormatField.Body, Lint.FormatItemDetail(body)),
              (com.histmersion.api.library.Citekey(slug), FormatField.Summary, Lint.FormatItemDetail(summary))
            )
          ),
          format
        )
      case name =>
        projections
          .get(name)
          .toList
          .flatMap(pairs)
          .map((ck, detail) => LintItem(com.histmersion.api.library.Citekey(ck), LintItem.Detail(detail)))
    Lint.report(r, items, Lint.ItemLimit(maxItems.value))

  // ── article-critique-pairing ─────────────────────────────────────────────────────────────────

  /** What the pairing rule reads from the corpus: how a `[[target]]` resolves, the disagreement edges, and each
    * source's names and year.
    */
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class PairingCorpus(
      resolve: CritiquePairing.Mention.Key => Option[com.histmersion.api.library.Citekey],
      edges: List[Coverage.Edge],
      work: com.histmersion.api.library.Citekey => Option[CritiquePairing.Work]
  )

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def pairingCorpusIn()(using DbCon): PairingCorpus =
    val names = sourceNamesSql
      .query[(String, List[String], Option[Int])]
      .run()
      .map { (ck, authors, year) =>
        val key = com.histmersion.api.library.Citekey(ck)
        key -> CritiquePairing.Work(
          key,
          CritiquePairing.names(key, Option(authors).getOrElse(Nil).map(AuthorName(_))),
          year.map(y => Year(y.toLong))
        )
      }
      .toMap
    val aliases = pairs(sourceAliasesSql)
      .map((alias, ck) => com.histmersion.api.library.Citekey(alias) -> com.histmersion.api.library.Citekey(ck))
      .toMap
    val edges = triples(disagreementEdgesSql).map((src, kind, dst) =>
      Coverage.Edge(
        com.histmersion.api.library.Citekey(src),
        Edge.Kind.Wire(kind),
        com.histmersion.api.library.Citekey(dst)
      )
    )
    PairingCorpus(CritiquePairing.resolver(names.keySet, aliases), edges, names.get)

  /** One article the pairing repair would change: its token, the repaired body, the hits applied. */
  final case class PairingRepair(
      slug: PairingRepair.Slug,
      updatedAt: Option[PairingRepair.UpdatedAt],
      body: Article.Body,
      applied: List[CritiquePairing.Hit]
  )

  object PairingRepair:
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Slug = domain.research.Slug
    val Slug: domain.research.Slug.type = domain.research.Slug
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]

  /** The mechanical pairing repairs of every article ([[CritiquePairing.repair]]) — computed, not written:
    * `histmersion lint --rule article-critique-pairing --fix` writes each through the article patch, guarded by its
    * token.
    */
  def pairingRepairs(using DbCon): List[PairingRepair] =
    val c = pairingCorpusIn()
    sql"""SELECT slug, body, to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') FROM articles ORDER BY slug"""
      .query[(PairingRepair.Slug, String, Option[String])]
      .run()
      .toList
      .flatMap { (slug, text, at) =>
        val body = Article.Body(text)
        val (fixed, done) = CritiquePairing.repair(body, CritiquePairing.hits(body, c.resolve, c.edges, c.work))
        Option.when(done.nonEmpty)(PairingRepair(slug, at.map(PairingRepair.UpdatedAt(_)), fixed, done))
      }
