package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Lint.*

import com.histmersion.util.data.checks.{CheckInput, CheckRun}

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The SQL half of the articles' lint rules, until research registers its own (the library's are its sources'
  * `SourceCheckRepo`).
  *
  * Every rule is either a **projection** (one SQL statement yielding `(slug, detail)`, in [[projections]]) or a **fetch
  * + pure classifier** (`Lint.articleFiller`, `Lint.articleCritiquePairing`, …, pure and unit-tested without a
  * database). No rule takes a parameter; the statements are constants, spliced through [[Sql.raw]].
  */
object LintRepo:

  /** Rules that are one SQL statement. Ordered so the report is stable. */
  val projections: Map[String, String] = Map(
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
  def sqlOf(rule: String): List[String] = rule match
    case ArticleFiller | ArticleUncitedParagraph => List(articlesSql)
    case ArticleCritiquePairing => List(articlesSql, disagreementEdgesSql, sourceNamesSql, sourceAliasesSql)
    case ArticleThinSummary     => List(articleSummariesSql)
    case ArticleFormat          => List(articleTextsSql)
    case r                      => projections.get(r).toList

  private def pairs(sql: String)(using DbCon): List[(String, String)] =
    Sql.raw(sql).frag.query[(String, String)].run().toList

  private def triples(sql: String)(using DbCon): List[(String, String, String)] =
    Sql.raw(sql).frag.query[(String, String, String)].run().toList

  // the stored citekeys and the articles several rules classify, read at most once per run
  private val StoredCitekeys = CheckInput(Sql.raw(citekeysSql).frag.query[String].run().toSet)
  private val ArticleRows = CheckInput(pairs(articlesSql))

  /** Whether a citekey is stored — what the formatting rules check a bare citekey against, read once per run. */
  def storedCitekey(using run: CheckRun)(using DbCon): String => Boolean = ck => run(StoredCitekeys).contains(ck)

  /** One rule's report, on the run's connection; `format` is the formatting check. */
  def report(r: Rule, format: FormatChecker, maxItems: Int)(using run: CheckRun)(using DbCon): LintRuleReport =
    val items = r.name match
      case ArticleFiller           => Lint.articleFiller(run(ArticleRows))
      case ArticleUncitedParagraph => Lint.articleUncitedParagraphs(run(ArticleRows))
      case ArticleCritiquePairing  =>
        val p = pairingCorpusIn()
        Lint.articleCritiquePairing(run(ArticleRows), p.resolve, p.edges, p.work)
      case ArticleThinSummary => Lint.articleThinSummary(pairs(articleSummariesSql))
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
      resolve: String => Option[String],
      edges: List[CoverageEdge],
      work: String => Option[CritiquePairing.Work]
  )

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
      .map((src, kind, dst) => CoverageEdge(src, kind, dst))
      .toList
    PairingCorpus(CritiquePairing.resolver(names.keySet, aliases), edges, names.get)

  /** One article the pairing repair would change: its token, the repaired body, the hits applied. */
  final case class PairingRepair(
      slug: String,
      updatedAt: Option[String],
      body: String,
      applied: List[CritiquePairing.Hit]
  )

  /** The mechanical pairing repairs of every article ([[CritiquePairing.repair]]) — computed, not written:
    * `histmersion lint --rule article-critique-pairing --fix` writes each through the article patch, guarded by its
    * token.
    */
  def pairingRepairs(ds: DataSource): List[PairingRepair] =
    connect(ds):
      val c = pairingCorpusIn()
      sql"""SELECT slug, body, to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') FROM articles ORDER BY slug"""
        .query[(String, String, Option[String])]
        .run()
        .toList
        .flatMap { (slug, body, at) =>
          val (fixed, done) = CritiquePairing.repair(body, CritiquePairing.hits(body, c.resolve, c.edges, c.work))
          Option.when(done.nonEmpty)(PairingRepair(slug, at, fixed, done))
        }
