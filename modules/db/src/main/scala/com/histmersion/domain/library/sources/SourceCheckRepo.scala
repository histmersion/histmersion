package com.histmersion
package domain.library.sources

import com.histmersion.api.library.CitationLint

import com.histmersion.domain.catalogue.places.{FindingPlacement, GeocodeSettings}

import com.histmersion.config.*
import com.histmersion.api.checks.Lint.*
import com.histmersion.util.data.checks.{CheckInput, CheckRun}

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.checks.{FormatField, Lint}
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.lint.{LintRule, tolerated}

/** The SQL half of the library's lint rules: what each rule reads of the sources, their findings and edges, and the
  * safe repairs.
  *
  * Every rule is either
  *   - a **projection**: one SQL statement yielding `(citekey, detail)` — the statement is a constant fragment in
  *     [[projections]], so a test can read it, and the classifier is the WHERE clause; or
  *   - a **fetch + pure classifier**: the graph and duplicate rules pull raw rows and hand them to
  *     `CitationLint.danglingEdges` / `CitationLint.unresolvedShortCitekeys` / `Lint.duplicates`, which are pure and
  *     unit-tested without a database.
  *
  * No rule takes a parameter, so nothing here can be an injection vector; the statements are constant `sql"..."`
  * fragments.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
private[sources] object SourceCheckRepo:

  /** Rules that are one SQL statement. Ordered `citekey` so the report is stable. */
  val projections: Map[String, Frag] = Map(
    NoFindings ->
      sql"""SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s
         WHERE NOT EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey)
         ORDER BY s.citekey""",
    NoPeriod ->
      sql"""SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s WHERE s.period IS NULL ORDER BY s.citekey""",
    NoRegions ->
      sql"""SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s WHERE coalesce(cardinality(s.regions), 0) = 0 ORDER BY s.citekey""",
    EmptySummary ->
      sql"""SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s WHERE s.summary IS NULL OR btrim(s.summary) = '' ORDER BY s.citekey""",
    UnassessedStatus ->
      sql"""SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s WHERE lower(coalesce(s.status, '')) = 'unassessed' ORDER BY s.citekey""",
    OrphanSources ->
      sql"""SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s
         WHERE NOT EXISTS (SELECT 1 FROM edges e WHERE e.src = s.citekey)
           AND NOT EXISTS (SELECT 1 FROM unresolved_edges u WHERE u.src = s.citekey)
           AND NOT EXISTS (SELECT 1 FROM edges e WHERE e.dst = s.citekey)
         ORDER BY s.citekey""",
    StaleChunks ->
      sql"""SELECT s.citekey, 'body of ' || length(s.ocr_markdown) || ' chars, 0 chunks'
         FROM sources s
         WHERE coalesce(length(s.ocr_markdown), 0) > 0
           AND NOT EXISTS (SELECT 1 FROM source_chunks c WHERE c.citekey = s.citekey)
         ORDER BY s.citekey""",
    UndatedFindings ->
      sql"""SELECT f.citekey, f.id || ' — ' || left(f.claim, 70)
         FROM findings f WHERE f.period IS NULL ORDER BY f.citekey, f.id""",
    UnlocatedFindings ->
      sql"""SELECT f.citekey, f.id || ' — ' || left(f.claim, 70)
         FROM findings f
         WHERE f.geom IS NULL AND (f.region_name IS NULL OR btrim(f.region_name) = '')
         ORDER BY f.citekey, f.id""",
    // `![alt](…figures/x.png)` anywhere in the body. The renderer rewrites these on the fly so the
    // page is not broken, but the STORED body is still wrong: the assets it names may never have
    // been imported, and every other consumer of `ocr_markdown` sees the converter's local path. (`sql"""…"""` reads
    // escapes like `s"…"`: each backslash the pattern needs is written twice.)
    LegacyFigureLinks ->
      sql"""SELECT s.citekey,
                (SELECT count(*) FROM regexp_matches(s.ocr_markdown, '!\\[[^\\]]*\\]\\([^)]*figures/', 'g'))
                  || ' figure link(s) still on a figures/ path'
         FROM sources s
         WHERE s.ocr_markdown ~ '!\\[[^\\]]*\\]\\([^)]*figures/'
         ORDER BY s.citekey""",
    MissingYear ->
      sql"""SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s WHERE s.year IS NULL ORDER BY s.citekey""",
    SupersededWithoutEdge ->
      sql"""SELECT s.citekey, left(coalesce(s.title, ''), 80)
         FROM sources s
         WHERE lower(coalesce(s.status, '')) = 'superseded'
           AND NOT EXISTS (SELECT 1 FROM edges e WHERE e.src = s.citekey AND e.kind = 'superseded_by')
           AND NOT EXISTS (SELECT 1 FROM unresolved_edges u WHERE u.src = s.citekey AND u.kind = 'superseded_by')
         ORDER BY s.citekey"""
  )

  /** The raw fetches behind the pure classifiers, kept here so every statement the rules run is visible in one place.
    */
  val citekeysSql = sql"SELECT citekey FROM sources"
  // both tables: the dangling-edge rules report the PARKED ones (V026), and must see them all
  val edgesSql = sql"SELECT src, dst, kind FROM edges UNION ALL SELECT src, dst, kind FROM unresolved_edges"
  val doiSql = sql"SELECT citekey, lower(btrim(doi)) FROM sources WHERE doi IS NOT NULL AND btrim(doi) <> ''"
  val sha256Sql =
    sql"SELECT citekey, lower(btrim(sha256)) FROM sources WHERE sha256 IS NOT NULL AND btrim(sha256) <> ''"
  val staleCitekeysSql =
    sql"""SELECT s.citekey, coalesce(length(s.ocr_markdown), 0)
       FROM sources s
       WHERE coalesce(length(s.ocr_markdown), 0) > 0
         AND NOT EXISTS (SELECT 1 FROM source_chunks c WHERE c.citekey = s.citekey)
       ORDER BY s.citekey"""
  val tagsSql = sql"SELECT citekey, tags FROM sources ORDER BY citekey"
  val yearsSql = sql"SELECT citekey, year FROM sources ORDER BY citekey"
  val authorsSql = sql"SELECT citekey, authors FROM sources ORDER BY citekey"
  val noteTextsSql =
    sql"""SELECT citekey, coalesce(summary, ''), coalesce(notes, '') FROM sources
       WHERE btrim(coalesce(summary, '')) <> '' OR btrim(coalesce(notes, '')) <> '' ORDER BY citekey"""

  /** Every rule must be runnable — the report would otherwise lie by omission. */
  def sqlOf(rule: String): List[Frag] = rule match
    case DanglingEdges | UnresolvedShortCitekeys => List(citekeysSql, edgesSql)
    case DuplicateDoi                            => List(doiSql)
    case DuplicateSha256                         => List(sha256Sql)
    case NonCanonicalTag                         => List(tagsSql)
    case YearCitekeyMismatch                     => List(yearsSql)
    case AuthorPipe                              => List(authorsSql)
    case NoteFormat                              => List(noteTextsSql)
    case FindingGeocode                          => List(FindingPlacement.legacyPointsSql)
    case FindingUnplaced                         => List(FindingPlacement.unplacedSql)
    case FindingPlaceholder                      => List(FindingPlacement.placeholderFindingsSql)
    case r                                       => projections.get(r).toList

  private def pairs(q: Frag)(using DbCon): List[(String, String)] =
    q.query[(String, String)].run().toList

  private def triples(q: Frag)(using DbCon): List[(String, String, String)] =
    q.query[(String, String, String)].run().toList

  private def arrays(q: Frag)(using DbCon): List[(String, List[String])] =
    q.query[(String, List[String])].run().toList

  /** What the rules read besides the database: how many prefix candidates a dangling target is offered
    * (`lint.suggestions`), the formatting check and the geocoder's knobs (`finding-geocode`, `finding-unplaced`).
    */
  final case class Context(suggestions: Lint.ItemLimit, format: FormatChecker, geocode: GeocodeSettings)

  object Context:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def of(cfg: AppConfig, format: FormatChecker): Context =
      Context(Lint.ItemLimit(cfg.lint.suggestions.value), format, geocodeOf(cfg))

  /** The geocoder's knobs (`places.geocode-*`), as `finding-geocode` and its repair use them. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def geocodeOf(cfg: AppConfig): GeocodeSettings =
    GeocodeSettings(
      GeocodeSettings.PlaceholderMin(cfg.places.geocodePlaceholderMin.value),
      GeocodeSettings.RepairKm(cfg.places.geocodeRepairKm.value),
      GeocodeSettings.ToleranceKm(cfg.places.pointToleranceKm.value)
    )

  // the graph inputs and the fetches several rules classify, read at most once per run
  private val StoredCitekeys = CheckInput(citekeysSql.query[String].run().toSet)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val EdgeRows = CheckInput(
    edgesSql
      .query[(String, String, String)]
      .run()
      .flatMap((src, dst, kind) =>
        Edge.Kind
          .fromWire(Edge.Kind.Wire(kind))
          .map(k => Edge(Citekey(src), Citekey(dst), k))
      )
      .toList
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val TagRows = CheckInput(
    arrays(tagsSql).map((ck, tags) => (Citekey(ck), tags.map(Lint.Tag(_))))
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val YearRows = CheckInput(
    pairs(yearsSql).map((ck, y) => (Citekey(ck), Option(y).flatMap(_.toIntOption).map(i => Year(i.toLong))))
  ) // year is nullable
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val AuthorRows = CheckInput(
    arrays(authorsSql).map((ck, authors) => (Citekey(ck), authors.map(AuthorName(_))))
  )

  /** Whether a citekey is stored — what the formatting rules check a bare citekey against, read once per run. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def storedCitekey(using run: CheckRun)(using DbCon): domain.library.Citekey => Boolean = ck =>
    run(StoredCitekeys).contains(ck.value)

  /** One rule's report, on the run's connection. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def report(r: Rule, c: Context, maxItems: Lint.ItemLimit)(using run: CheckRun)(using DbCon): LintRuleReport =
    val items = r.name.value match
      case DanglingEdges =>
        CitationLint.danglingEdges(run(EdgeRows), run(StoredCitekeys).map(Citekey(_)), c.suggestions)
      case UnresolvedShortCitekeys =>
        CitationLint.unresolvedShortCitekeys(run(EdgeRows), run(StoredCitekeys).map(Citekey(_)), c.suggestions)
      case DuplicateDoi =>
        Lint.duplicates(pairs(doiSql).map((ck, k) => (Citekey(ck), Lint.DuplicateKey(k))), "doi")
      case DuplicateSha256 =>
        Lint.duplicates(pairs(sha256Sql).map((ck, k) => (Citekey(ck), Lint.DuplicateKey(k))), "sha256")
      case NonCanonicalTag     => Lint.nonCanonicalTags(run(TagRows))
      case YearCitekeyMismatch => Lint.yearCitekeyMismatch(run(YearRows))
      case AuthorPipe          => Lint.authorPipe(run(AuthorRows))
      case NoteFormat          =>
        Lint.formatItems(
          triples(noteTextsSql).flatMap((ck, summary, notes) =>
            val key = Citekey(ck)
            List(
              (key, FormatField.Summary, Lint.FormatItemDetail(summary)),
              (key, FormatField.Notes, Lint.FormatItemDetail(notes))
            )
          ),
          c.format
        )
      case FindingGeocode =>
        FindingPlacement.movesIn(c.geocode).map(m => LintItem(m.citekey, LintItem.Detail(m.describe)))
      case FindingUnplaced =>
        FindingPlacement.placementsIn(c.geocode).map(p => LintItem(p.citekey, LintItem.Detail(p.describe)))
      case FindingPlaceholder =>
        FindingPlacement
          .unresolvedPlaceholdersIn()
          .map((ck, detail) => LintItem(Citekey(ck.value), LintItem.Detail(detail.value)))
      case name =>
        projections
          .get(name)
          .toList
          .flatMap(pairs)
          .map((ck, detail) => LintItem(Citekey(ck), LintItem.Detail(detail)))
    Lint.report(r, items, maxItems)

  // ── the safe fixes ───────────────────────────────────────────────────────────────────────────

  /** The sources with a body and no full-text chunks, each with its body's length (`stale-chunks`). */
  def stale()(using DbCon): Vector[(domain.library.Citekey, RowCount)] =
    staleCitekeysSql.query[(domain.library.Citekey, RowCount)].run()
