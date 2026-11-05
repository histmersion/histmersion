package com.histmersion.domain.library.sources

import com.histmersion.api.library.CitationLint

import com.histmersion.domain.catalogue.places.{FindingPlacement, GeocodeSettings}

import com.histmersion.config.*
import com.histmersion.api.checks.Lint.*
import com.histmersion.util.database.Sql
import com.histmersion.util.data.checks.{CheckInput, CheckRun}

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.checks.{FormatField, Lint}
import com.histmersion.api.library.Edge

/** The SQL half of the library's lint rules: what each rule reads of the sources, their findings and edges, and the
  * safe repairs.
  *
  * Every rule is either
  *   - a **projection**: one SQL statement yielding `(citekey, detail)` — the statement is a plain `String` in
  *     [[projections]], so a test can read it, and the classifier is the WHERE clause; or
  *   - a **fetch + pure classifier**: the graph and duplicate rules pull raw rows and hand them to
  *     `CitationLint.danglingEdges` / `CitationLint.unresolvedShortCitekeys` / `Lint.duplicates`, which are pure and
  *     unit-tested without a database.
  *
  * No rule takes a parameter, so nothing here can be an injection vector; the statements are constants, spliced through
  * [[Sql.raw]].
  */
private[sources] object SourceCheckRepo:

  /** Rules that are one SQL statement. Ordered `citekey` so the report is stable. */
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
  type ArraysResultPart1 = ArraysResultPart1.T
  object ArraysResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArraysResultPart2 = ArraysResultPart2.T
  object ArraysResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArraysSql = ArraysSql.T
  object ArraysSql extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoredCitekeyResultArg1 = StoredCitekeyResultArg1.T
  object StoredCitekeyResultArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoredCitekeyResultResult = StoredCitekeyResultResult.T
  object StoredCitekeyResultResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReportMaxItems = ReportMaxItems.T
  object ReportMaxItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleResultPart1 = StaleResultPart1.T
  object StaleResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleResultPart2 = StaleResultPart2.T
  object StaleResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val projections: Map[com.histmersion.domain.library.sources.SourceCheckRepo.ProjectionsPart1, com.histmersion.domain.library.sources.SourceCheckRepo.ProjectionsPart2] = Map(
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

  /** The raw fetches behind the pure classifiers, kept here so every statement the rules run is visible in one place.
    */
  val citekeysSql = "SELECT citekey FROM sources"
  // both tables: the dangling-edge rules report the PARKED ones (V026), and must see them all
  val edgesSql = "SELECT src, dst, kind FROM edges UNION ALL SELECT src, dst, kind FROM unresolved_edges"
  val doiSql = "SELECT citekey, lower(btrim(doi)) FROM sources WHERE doi IS NOT NULL AND btrim(doi) <> ''"
  val sha256Sql = "SELECT citekey, lower(btrim(sha256)) FROM sources WHERE sha256 IS NOT NULL AND btrim(sha256) <> ''"
  val staleCitekeysSql =
    """SELECT s.citekey, coalesce(length(s.ocr_markdown), 0)
       FROM sources s
       WHERE coalesce(length(s.ocr_markdown), 0) > 0
         AND NOT EXISTS (SELECT 1 FROM source_chunks c WHERE c.citekey = s.citekey)
       ORDER BY s.citekey"""
  val tagsSql = "SELECT citekey, tags FROM sources ORDER BY citekey"
  val yearsSql = "SELECT citekey, year FROM sources ORDER BY citekey"
  val authorsSql = "SELECT citekey, authors FROM sources ORDER BY citekey"
  val noteTextsSql =
    """SELECT citekey, coalesce(summary, ''), coalesce(notes, '') FROM sources
       WHERE btrim(coalesce(summary, '')) <> '' OR btrim(coalesce(notes, '')) <> '' ORDER BY citekey"""

  /** Every rule must be runnable — the report would otherwise lie by omission. */
  def sqlOf(rule: com.histmersion.domain.library.sources.SourceCheckRepo.SqlOfRule): List[com.histmersion.domain.library.sources.SourceCheckRepo.SqlOfResult] = rule match
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

  private def pairs(sql: com.histmersion.domain.library.sources.SourceCheckRepo.PairsSql)(using DbCon): List[(com.histmersion.domain.library.sources.SourceCheckRepo.PairsResultPart1, com.histmersion.domain.library.sources.SourceCheckRepo.PairsResultPart2)] =
    Sql.raw(sql).frag.query[(String, String)].run().toList

  private def triples(sql: com.histmersion.domain.library.sources.SourceCheckRepo.TriplesSql)(using DbCon): List[(com.histmersion.domain.library.sources.SourceCheckRepo.TriplesResultPart1, com.histmersion.domain.library.sources.SourceCheckRepo.TriplesResultPart2, com.histmersion.domain.library.sources.SourceCheckRepo.TriplesResultPart3)] =
    Sql.raw(sql).frag.query[(String, String, String)].run().toList

  private def arrays(sql: com.histmersion.domain.library.sources.SourceCheckRepo.ArraysSql)(using DbCon): List[(com.histmersion.domain.library.sources.SourceCheckRepo.ArraysResultPart1, List[com.histmersion.domain.library.sources.SourceCheckRepo.ArraysResultPart2])] =
    Sql.raw(sql).frag.query[(String, List[String])].run().toList

  /** What the rules read besides the database: how many prefix candidates a dangling target is offered
    * (`lint.suggestions`), the formatting check and the geocoder's knobs (`finding-geocode`, `finding-unplaced`).
    */
  final case class Context(suggestions: Context.Suggestions, format: FormatChecker, geocode: GeocodeSettings)

  object Context:
    type Suggestions = Suggestions.T
    object Suggestions extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(cfg: AppConfig, format: FormatChecker): Context = Context(cfg.lint.suggestions, format, geocodeOf(cfg))

  /** The geocoder's knobs (`places.geocode-*`), as `finding-geocode` and its repair use them. */
  def geocodeOf(cfg: AppConfig): GeocodeSettings =
    GeocodeSettings(cfg.places.geocodePlaceholderMin, cfg.places.geocodeRepairKm, cfg.places.pointToleranceKm)

  // the graph inputs and the fetches several rules classify, read at most once per run
  private val StoredCitekeys = CheckInput(Sql.raw(citekeysSql).frag.query[String].run().toSet)
  private val EdgeRows = CheckInput(
    Sql
      .raw(edgesSql)
      .frag
      .query[(String, String, String)]
      .run()
      .map((src, dst, kind) => Edge(com.histmersion.api.library.Citekey(src), com.histmersion.api.library.Citekey(dst), kind))
      .toList
  )
  private val TagRows = CheckInput(arrays(tagsSql))
  private val YearRows = CheckInput(
    pairs(yearsSql).map((ck, y) => (ck, Option(y).flatMap(_.toIntOption)))
  ) // year is nullable
  private val AuthorRows = CheckInput(arrays(authorsSql))

  /** Whether a citekey is stored — what the formatting rules check a bare citekey against, read once per run. */
  def storedCitekey(using run: CheckRun)(using DbCon): com.histmersion.domain.library.sources.SourceCheckRepo.StoredCitekeyResultArg1 => com.histmersion.domain.library.sources.SourceCheckRepo.StoredCitekeyResultResult = ck => run(StoredCitekeys).contains(ck)

  /** One rule's report, on the run's connection. */
  def report(r: Rule, c: Context, maxItems: com.histmersion.domain.library.sources.SourceCheckRepo.ReportMaxItems)(using run: CheckRun)(using DbCon): LintRuleReport =
    val items = r.name match
      case DanglingEdges           => CitationLint.danglingEdges(run(EdgeRows), (run(StoredCitekeys)).map(com.histmersion.api.library.Citekey.apply), c.suggestions)
      case UnresolvedShortCitekeys =>
        CitationLint.unresolvedShortCitekeys(run(EdgeRows), (run(StoredCitekeys)).map(com.histmersion.api.library.Citekey.apply), c.suggestions)
      case DuplicateDoi        => Lint.duplicates(pairs(doiSql), "doi")
      case DuplicateSha256     => Lint.duplicates(pairs(sha256Sql), "sha256")
      case NonCanonicalTag     => Lint.nonCanonicalTags(run(TagRows))
      case YearCitekeyMismatch => Lint.yearCitekeyMismatch(run(YearRows))
      case AuthorPipe          => Lint.authorPipe(run(AuthorRows))
      case NoteFormat          =>
        Lint.formatItems(
          triples(noteTextsSql).flatMap((ck, summary, notes) =>
            List((ck, FormatField.Summary, summary), (ck, FormatField.Notes, notes))
          ),
          c.format
        )
      case FindingGeocode     => FindingPlacement.movesIn(c.geocode).map(m => LintItem(m.citekey, m.describe))
      case FindingUnplaced    => FindingPlacement.placementsIn(c.geocode).map(p => LintItem(p.citekey, p.describe))
      case FindingPlaceholder =>
        FindingPlacement.unresolvedPlaceholdersIn().map((ck, detail) => LintItem(ck, detail))
      case name => projections.get(name).toList.flatMap(pairs).map((ck, detail) => LintItem(ck, detail))
    Lint.report(r, items, maxItems)

  // ── the safe fixes ───────────────────────────────────────────────────────────────────────────

  /** The sources with a body and no full-text chunks, each with its body's length (`stale-chunks`). */
  def stale()(using DbCon): Vector[(com.histmersion.domain.library.sources.SourceCheckRepo.StaleResultPart1, com.histmersion.domain.library.sources.SourceCheckRepo.StaleResultPart2)] = Sql.raw(staleCitekeysSql).frag.query[(String, Int)].run()
