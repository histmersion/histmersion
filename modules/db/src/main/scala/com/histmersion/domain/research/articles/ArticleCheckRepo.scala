package com.histmersion.domain.research.articles

import com.histmersion.api.checks.Lint.*
import com.histmersion.util.database.Sql

import com.histmersion.util.data.checks.{CheckInput, CheckRun}

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.checks.{FormatField, Lint}
import com.histmersion.api.research.CritiquePairing
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage

/** The SQL half of the articles' lint rules (the library's are its sources' `SourceCheckRepo`).
  *
  * Every rule is either a **projection** (one SQL statement yielding `(slug, detail)`, in [[projections]]) or a **fetch
  * + pure classifier** (`ArticleLint.articleFiller`, `ArticleLint.articleCritiquePairing`, …, pure and unit-tested
  * without a database). No rule takes a parameter; the statements are constants, spliced through [[Sql.raw]].
  */
private[articles] object ArticleCheckRepo:

  /** Rules that are one SQL statement. Ordered so the report is stable. */
  type ProjectionsPart1 = ProjectionsPart1.T
  object ProjectionsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProjectionsPart2 = ProjectionsPart2.T
  object ProjectionsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SqlOfResult = SqlOfResult.T
  object SqlOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SqlOfRule = SqlOfRule.T
  object SqlOfRule extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PairsResultPart1 = PairsResultPart1.T
  object PairsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PairsResultPart2 = PairsResultPart2.T
  object PairsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PairsSql = PairsSql.T
  object PairsSql extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TriplesResultPart1 = TriplesResultPart1.T
  object TriplesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TriplesResultPart2 = TriplesResultPart2.T
  object TriplesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TriplesResultPart3 = TriplesResultPart3.T
  object TriplesResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TriplesSql = TriplesSql.T
  object TriplesSql extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoredCitekeyResultArg1 = StoredCitekeyResultArg1.T
  object StoredCitekeyResultArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoredCitekeyResultResult = StoredCitekeyResultResult.T
  object StoredCitekeyResultResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReportMaxItems = ReportMaxItems.T
  object ReportMaxItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val projections: Map[com.histmersion.domain.research.articles.ArticleCheckRepo.ProjectionsPart1, com.histmersion.domain.research.articles.ArticleCheckRepo.ProjectionsPart2] = Map(
    // The article-side twin of `dangling-edges`, resolved exactly the way the RENDERER resolves a
    // link (`ArticleLinks.hrefFor`): an exact slug, an exact citekey, or a UNIQUE `<target>_%` alias
    // — the short form the wiki writes (`[[knapp2016]]`). Whatever is left is a name with no page
    // behind it, and is drawn as plain text rather than as a link to a 404.
    DanglingArticleLinks ->
      """SELECT l.src_slug, '[[' || l.target || ']] resolves to nothing'
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
  val citekeysSql = "SELECT citekey FROM sources"
  // ── article-critique-pairing: the disagreement edges, the sources' names, the aliases ─────────
  val disagreementEdgesSql =
    "SELECT src, kind, dst FROM edges WHERE kind IN ('challenges', 'refutes', 'updates', 'superseded_by') ORDER BY 1, 2, 3"
  val sourceNamesSql = "SELECT citekey, authors, year FROM sources ORDER BY citekey"
  val sourceAliasesSql = "SELECT alias, citekey FROM source_aliases"
  val articlesSql = "SELECT slug, body FROM articles ORDER BY slug"
  val articleSummariesSql = "SELECT slug, coalesce(summary, '') FROM articles ORDER BY slug"
  val articleTextsSql = "SELECT slug, body, coalesce(summary, '') FROM articles ORDER BY slug"

  /** Every rule must be runnable — the report would otherwise lie by omission. */
  def sqlOf(rule: com.histmersion.domain.research.articles.ArticleCheckRepo.SqlOfRule): List[com.histmersion.domain.research.articles.ArticleCheckRepo.SqlOfResult] = rule match
    case ArticleFiller | ArticleUncitedParagraph => List(articlesSql)
    case ArticleCritiquePairing => List(articlesSql, disagreementEdgesSql, sourceNamesSql, sourceAliasesSql)
    case ArticleThinSummary     => List(articleSummariesSql)
    case ArticleFormat          => List(articleTextsSql)
    case r                      => projections.get(r).toList

  private def pairs(sql: com.histmersion.domain.research.articles.ArticleCheckRepo.PairsSql)(using DbCon): List[(com.histmersion.domain.research.articles.ArticleCheckRepo.PairsResultPart1, com.histmersion.domain.research.articles.ArticleCheckRepo.PairsResultPart2)] =
    Sql.raw(sql).frag.query[(String, String)].run().toList

  private def triples(sql: com.histmersion.domain.research.articles.ArticleCheckRepo.TriplesSql)(using DbCon): List[(com.histmersion.domain.research.articles.ArticleCheckRepo.TriplesResultPart1, com.histmersion.domain.research.articles.ArticleCheckRepo.TriplesResultPart2, com.histmersion.domain.research.articles.ArticleCheckRepo.TriplesResultPart3)] =
    Sql.raw(sql).frag.query[(String, String, String)].run().toList

  // the stored citekeys and the articles several rules classify, read at most once per run
  private val StoredCitekeys = CheckInput(Sql.raw(citekeysSql).frag.query[String].run().toSet)
  private val ArticleRows = CheckInput(pairs(articlesSql))

  /** Whether a citekey is stored — what the formatting rules check a bare citekey against, read once per run. */
  def storedCitekey(using run: CheckRun)(using DbCon): com.histmersion.domain.research.articles.ArticleCheckRepo.StoredCitekeyResultArg1 => com.histmersion.domain.research.articles.ArticleCheckRepo.StoredCitekeyResultResult = ck => run(StoredCitekeys).contains(ck)

  /** One rule's report, on the run's connection; `format` is the formatting check. */
  def report(r: Rule, format: FormatChecker, maxItems: com.histmersion.domain.research.articles.ArticleCheckRepo.ReportMaxItems)(using run: CheckRun)(using DbCon): LintRuleReport =
    val items = r.name match
      case ArticleFiller           => Article.Lint.articleFiller(run(ArticleRows))
      case ArticleUncitedParagraph => Article.Lint.articleUncitedParagraphs(run(ArticleRows))
      case ArticleCritiquePairing  =>
        val p = pairingCorpusIn()
        Article.Lint.articleCritiquePairing(run(ArticleRows), p.resolve, p.edges, p.work)
      case ArticleThinSummary => Article.Lint.articleThinSummary(pairs(articleSummariesSql))
      case ArticleFormat      =>
        Lint.formatItems(
          triples(articleTextsSql).flatMap((slug, body, summary) =>
            List((slug, FormatField.Body, body), (slug, FormatField.Summary, summary))
          ),
          format
        )
      case name => projections.get(name).toList.flatMap(pairs).map((ck, detail) => LintItem(ck, detail))
    Lint.report(r, items, maxItems)

  // ── article-critique-pairing ─────────────────────────────────────────────────────────────────

  /** What the pairing rule reads from the corpus: how a `[[target]]` resolves, the disagreement edges, and each
    * source's names and year.
    */
  final case class PairingCorpus(
      resolve: PairingCorpus.ResolveArg1 => Option[PairingCorpus.ResolveResult],
      edges: List[Coverage.Edge],
      work: PairingCorpus.WorkArg1 => Option[CritiquePairing.Work]
  )

  object PairingCorpus:
    type ResolveArg1 = ResolveArg1.T
    object ResolveArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveResult = ResolveResult.T
    object ResolveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WorkArg1 = WorkArg1.T
    object WorkArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def pairingCorpusIn()(using DbCon): PairingCorpus =
    val names = Sql
      .raw(sourceNamesSql)
      .frag
      .query[(String, List[String], Option[Int])]
      .run()
      .map((ck, authors, year) =>
        ck -> CritiquePairing.Work(ck, CritiquePairing.names(ck, Option(authors).getOrElse(Nil)), year)
      )
      .toMap
    val aliases = pairs(sourceAliasesSql).toMap
    val edges = Sql
      .raw(disagreementEdgesSql)
      .frag
      .query[(String, String, String)]
      .run()
      .map((src, kind, dst) => Coverage.Edge(src, kind, dst))
      .toList
    PairingCorpus(CritiquePairing.resolver(names.keySet, aliases), edges, names.get)

  /** One article the pairing repair would change: its token, the repaired body, the hits applied. */
  final case class PairingRepair(
      slug: PairingRepair.Slug,
      updatedAt: Option[PairingRepair.UpdatedAt],
      body: PairingRepair.Body,
      applied: List[CritiquePairing.Hit]
  )

  object PairingRepair:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Body = Body.T
    object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The mechanical pairing repairs of every article ([[CritiquePairing.repair]]) — computed, not written:
    * `histmersion lint --rule article-critique-pairing --fix` writes each through the article patch, guarded by its
    * token.
    */
  def pairingRepairs(using DbCon): List[PairingRepair] =
    val c = pairingCorpusIn()
    sql"""SELECT slug, body, to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') FROM articles ORDER BY slug"""
      .query[(String, String, Option[String])]
      .run()
      .toList
      .flatMap { (slug, body, at) =>
        val (fixed, done) = CritiquePairing.repair(body, CritiquePairing.hits(body, c.resolve, c.edges, c.work))
        Option.when(done.nonEmpty)(PairingRepair(slug, at, fixed, done))
      }
