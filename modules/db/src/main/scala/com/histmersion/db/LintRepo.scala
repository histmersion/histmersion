package com.histmersion.db

import com.histmersion.core.*
import com.histmersion.core.Lint.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The SQL half of `histmersion lint` (plan §5.2 stream 2.8).
  *
  * Every rule is either
  *   - a **projection**: one SQL statement yielding `(citekey, detail)` — the statement is a plain
  *     `String` in [[projections]], so a test can read it, and the classifier is the WHERE clause; or
  *   - a **fetch + pure classifier**: the graph and duplicate rules pull raw rows and hand them to
  *     `Lint.danglingEdges` / `Lint.unresolvedShortCitekeys` / `Lint.duplicates`, which are pure and
  *     unit-tested without a database.
  *
  * No rule takes a parameter, so nothing here can be an injection vector; the statements are
  * constants, spliced through [[Sql.raw]].
  */
object LintRepo:

  /** Rules that are one SQL statement. Ordered `citekey` so the report is stable. */
  val projections: Map[String, String] = Map(
    NoFindings ->
      """SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s
         WHERE NOT EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey)
         ORDER BY s.citekey""",
    NoPeriod ->
      """SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s WHERE s.period IS NULL ORDER BY s.citekey""",
    NoRegions ->
      """SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s WHERE coalesce(cardinality(s.regions), 0) = 0 ORDER BY s.citekey""",
    EmptySummary ->
      """SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s WHERE s.summary IS NULL OR btrim(s.summary) = '' ORDER BY s.citekey""",
    UnassessedStatus ->
      """SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s WHERE lower(coalesce(s.status, '')) = 'unassessed' ORDER BY s.citekey""",
    OrphanSources ->
      """SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s
         WHERE NOT EXISTS (SELECT 1 FROM edges e WHERE e.src = s.citekey)
           AND NOT EXISTS (SELECT 1 FROM unresolved_edges u WHERE u.src = s.citekey)
           AND NOT EXISTS (SELECT 1 FROM edges e WHERE e.dst = s.citekey)
         ORDER BY s.citekey""",
    StaleChunks ->
      """SELECT s.citekey, 'body of ' || length(s.ocr_markdown) || ' chars, 0 chunks'
         FROM sources s
         WHERE coalesce(length(s.ocr_markdown), 0) > 0
           AND NOT EXISTS (SELECT 1 FROM source_chunks c WHERE c.citekey = s.citekey)
         ORDER BY s.citekey""",
    UndatedFindings ->
      """SELECT f.citekey, f.id || ' — ' || left(f.claim, 70)
         FROM findings f WHERE f.period IS NULL ORDER BY f.citekey, f.id""",
    UnlocatedFindings ->
      """SELECT f.citekey, f.id || ' — ' || left(f.claim, 70)
         FROM findings f
         WHERE f.geom IS NULL AND (f.region_name IS NULL OR btrim(f.region_name) = '')
         ORDER BY f.citekey, f.id""",
    // `![alt](…figures/x.png)` anywhere in the body. The renderer rewrites these on the fly so the
    // page is not broken, but the STORED body is still wrong: the assets it names may never have
    // been imported, and every other consumer of `ocr_markdown` sees the converter's local path.
    LegacyFigureLinks ->
      """SELECT s.citekey,
                (SELECT count(*) FROM regexp_matches(s.ocr_markdown, '!\[[^\]]*\]\([^)]*figures/', 'g'))
                  || ' figure link(s) still on a figures/ path'
         FROM sources s
         WHERE s.ocr_markdown ~ '!\[[^\]]*\]\([^)]*figures/'
         ORDER BY s.citekey""",
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
         ORDER BY l.src_slug, l.target""",
    MissingYear ->
      """SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s WHERE s.year IS NULL ORDER BY s.citekey""",
    SupersededWithoutEdge ->
      """SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s
         WHERE lower(coalesce(s.status, '')) = 'superseded'
           AND NOT EXISTS (SELECT 1 FROM edges e WHERE e.src = s.citekey AND e.kind = 'superseded_by')
           AND NOT EXISTS (SELECT 1 FROM unresolved_edges u WHERE u.src = s.citekey AND u.kind = 'superseded_by')
         ORDER BY s.citekey"""
  )

  /** The raw fetches behind the pure classifiers, kept here so every statement this stream runs is
    * visible in one place. */
  val citekeysSql = "SELECT citekey FROM sources"
  // both tables: the dangling-edge rules report the PARKED ones (V026), and must see them all
  val edgesSql    = "SELECT src, dst, kind FROM edges UNION ALL SELECT src, dst, kind FROM unresolved_edges"
  val doiSql      = "SELECT citekey, lower(btrim(doi)) FROM sources WHERE doi IS NOT NULL AND btrim(doi) <> ''"
  val sha256Sql   = "SELECT citekey, lower(btrim(sha256)) FROM sources WHERE sha256 IS NOT NULL AND btrim(sha256) <> ''"
  val staleCitekeysSql =
    """SELECT s.citekey, coalesce(length(s.ocr_markdown), 0)
       FROM sources s
       WHERE coalesce(length(s.ocr_markdown), 0) > 0
         AND NOT EXISTS (SELECT 1 FROM source_chunks c WHERE c.citekey = s.citekey)
       ORDER BY s.citekey"""
  // ── wave 3 fetches ────────────────────────────────────────────────────────────────────────
  val tagsSql    = "SELECT citekey, tags FROM sources ORDER BY citekey"
  val yearsSql   = "SELECT citekey, year FROM sources ORDER BY citekey"
  val authorsSql = "SELECT citekey, authors FROM sources ORDER BY citekey"
  val articlesSql = "SELECT slug, body FROM articles ORDER BY slug"
  val articleSummariesSql = "SELECT slug, coalesce(summary, '') FROM articles ORDER BY slug"
  val articleTextsSql = "SELECT slug, body, coalesce(summary, '') FROM articles ORDER BY slug"
  val noteTextsSql =
    """SELECT citekey, coalesce(summary, ''), coalesce(notes, '') FROM sources
       WHERE btrim(coalesce(summary, '')) <> '' OR btrim(coalesce(notes, '')) <> '' ORDER BY citekey"""

  /** Every rule must be runnable — the report would otherwise lie by omission. */
  def sqlOf(rule: String): List[String] = rule match
    case DanglingEdges | UnresolvedShortCitekeys  => List(citekeysSql, edgesSql)
    case DuplicateDoi                             => List(doiSql)
    case DuplicateSha256                          => List(sha256Sql)
    case NonCanonicalTag                          => List(tagsSql)
    case YearCitekeyMismatch                      => List(yearsSql)
    case AuthorPipe                               => List(authorsSql)
    case ArticleFiller | ArticleUncitedParagraph  => List(articlesSql)
    case ArticleThinSummary                       => List(articleSummariesSql)
    case ArticleFormat                            => List(articleTextsSql)
    case NoteFormat                               => List(noteTextsSql)
    case FindingGeocode                           => List(Geocoder.legacySql)
    case FindingUnplaced                          => List(Geocoder.unplacedSql)
    case PlaceKindRule                            => List("SELECT id FROM places WHERE import_source IS NULL")
    case PlaceGridPoint                           => List("SELECT id FROM places WHERE location_precision = 'grid-cell'")
    case r                                        => projections.get(r).toList

  private def pairs(sql: String)(using DbCon): List[(String, String)] =
    Sql.raw(sql).frag.query[(String, String)].run().toList

  private def triples(sql: String)(using DbCon): List[(String, String, String)] =
    Sql.raw(sql).frag.query[(String, String, String)].run().toList

  private def arrays(sql: String)(using DbCon): List[(String, List[String])] =
    Sql.raw(sql).frag.query[(String, List[String])].run().toList

  /** Run the selected rules. One connection for the lot; the graph inputs (citekeys, edges) are
    * fetched at most once even though two rules classify them. */
  def run(ds: DataSource, rules: List[Rule], maxItems: Int, suggestions: Int, format: FormatChecker,
      geocode: Geocoder.Settings): LintReport =
    connect(ds):
      lazy val citekeys = Sql.raw(citekeysSql).frag.query[String].run().toSet
      lazy val edgeRows = Sql.raw(edgesSql).frag.query[(String, String, String)].run()
        .map((src, dst, kind) => Edge(src, dst, kind)).toList
      // wave 3 fetches — lazy, shared across rules that need them
      lazy val tagRows    = arrays(tagsSql)
      lazy val yearRows   = pairs(yearsSql).map((ck, y) => (ck, Option(y).flatMap(_.toIntOption))) // year is nullable
      lazy val authorRows = arrays(authorsSql)
      lazy val articleRows = pairs(articlesSql)

      val reports = rules.map { r =>
        val items = r.name match
          case DanglingEdges           => Lint.danglingEdges(edgeRows, citekeys, suggestions)
          case UnresolvedShortCitekeys => Lint.unresolvedShortCitekeys(edgeRows, citekeys, suggestions)
          case DuplicateDoi            => Lint.duplicates(pairs(doiSql), "doi")
          case DuplicateSha256         => Lint.duplicates(pairs(sha256Sql), "sha256")
          case NonCanonicalTag         => Lint.nonCanonicalTags(tagRows)
          case YearCitekeyMismatch     => Lint.yearCitekeyMismatch(yearRows)
          case AuthorPipe              => Lint.authorPipe(authorRows)
          case ArticleFiller           => Lint.articleFiller(articleRows)
          case ArticleUncitedParagraph => Lint.articleUncitedParagraphs(articleRows)
          case ArticleThinSummary      => Lint.articleThinSummary(pairs(articleSummariesSql))
          case ArticleFormat           => Lint.formatItems(triples(articleTextsSql).flatMap((slug, body, summary) =>
                                            List((slug, FormatField.Body, body), (slug, FormatField.Summary, summary))), format)
          case NoteFormat              => Lint.formatItems(triples(noteTextsSql).flatMap((ck, summary, notes) =>
                                            List((ck, FormatField.Summary, summary), (ck, FormatField.Notes, notes))), format)
          case FindingGeocode          => Geocoder.movesIn(geocode).map(m => LintItem(m.citekey, m.describe))
          case FindingUnplaced         => Geocoder.placementsIn(geocode).map(p => LintItem(p.citekey, p.describe))
          case PlaceKindRule           => PlaceReviewRepo.proposalsIn().map(p => LintItem(p.id, p.detail))
          case PlaceGridPoint          => PlaceReviewRepo.relocationsIn().map(r => LintItem(r.id, r.detail))
          case name =>
            projections.get(name).toList.flatMap(pairs).map((ck, detail) => LintItem(ck, detail))
        Lint.report(r, items, maxItems)
      }
      LintReport(reports)

  // ── the safe fixes ───────────────────────────────────────────────────────────────────────────

  /** Re-chunk the sources that have a body but no full-text chunks (`stale-chunks`).
    *
    * Bounded by `maxChars` of body text per call — a whole-corpus rebuild takes minutes, far past any
    * HTTP timeout — and always does at least one source, so repeated calls make progress. Returns the
    * same shape as `/api/reindex`, with `remaining` = stale sources still untouched. */
  def reindexStale(ds: DataSource, chunkChars: Int, maxChars: Long): ReindexResult =
    val pending = connect(ds):
      Sql.raw(staleCitekeysSql).frag.query[(String, Int)].run()

    val batch = pending
      .scanLeft(("", 0L)) { case ((_, spent), (ck, len)) => (ck, spent + len.toLong) }
      .drop(1)
      .takeWhile((_, spent) => spent <= maxChars)
      .map(_._1)
    val take = if batch.isEmpty then pending.take(1).map(_._1) else batch

    // Best effort, one transaction per source: another writer may re-chunk or remove a source between
    // the scan and the rewrite, and a bulk repair that aborts on one row is worse than one that keeps
    // going. A skipped source stays stale, so the next `lint` reports it again — nothing is hidden.
    val done = take.map { ck =>
      scala.util.Try {
        transact(ds):
          val md = sql"SELECT ocr_markdown FROM sources WHERE citekey = $ck".query[Option[String]].run()
          SourceRepo.replaceChunks(ck, md.headOption.flatten, chunkChars)
      }
    }
    done.zip(take).foreach {
      case (scala.util.Failure(e), ck) => scribe.warn(s"reindex of $ck skipped: ${e.getMessage}")
      case _                           => ()
    }
    val ok = done.collect { case scala.util.Success(n) => n }
    ReindexResult(ok.size, ok.sum, take.lastOption, pending.size - take.size)
