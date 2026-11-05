package com.histmersion.domain.catalogue.places

import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.database.RangeSql
import com.histmersion.domain.catalogue.Place

/** The places' SQL other queries embed, over a `places` row by its alias: what an import brought in, how a place is
  * dated, its time basis and the time rule of a view at a year or over a window, the base layers' predicates, the
  * labels that name nothing.
  */
object PlaceSql:

  /** A place GeoNames or Wikidata brought in (#1064), over a `places` row aliased `alias`. Such a place is for finding
    * a name and placing a finding, and for an explicit pick: it is no part of the hierarchy (a filter's scope, the
    * tree, a place's children), gets no findings by proximity or containment — only the finding the geocoder put on its
    * very point — and is drawn on the base map only with a finding (2026-10-09: 1.2 M points had made every region's
    * subtree and hover counts enormous).
    */
  type ImportedSqlResult = ImportedSqlResult.T
  object ImportedSqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportedSqlAlias = ImportedSqlAlias.T
  object ImportedSqlAlias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportedList = ImportedList.T
  object ImportedList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceTierSql = SourceTierSql.T
  object SourceTierSql extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormLabelSqlResult = NormLabelSqlResult.T
  object NormLabelSqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormLabelSqlCol = NormLabelSqlCol.T
  object NormLabelSqlCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModernSqlResult = ModernSqlResult.T
  object ModernSqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModernSqlAlias = ModernSqlAlias.T
  object ModernSqlAlias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultModernFrom = DefaultModernFrom.T
  object DefaultModernFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UndatedSqlResult = UndatedSqlResult.T
  object UndatedSqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UndatedSqlAlias = UndatedSqlAlias.T
  object UndatedSqlAlias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimelessSqlResult = TimelessSqlResult.T
  object TimelessSqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimelessSqlAlias = TimelessSqlAlias.T
  object TimelessSqlAlias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeBasisSqlResult = TimeBasisSqlResult.T
  object TimeBasisSqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeBasisSqlAlias = TimeBasisSqlAlias.T
  object TimeBasisSqlAlias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeWindowCteAsOf = TimeWindowCteAsOf.T
  object TimeWindowCteAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeWindowCteModernFrom = TimeWindowCteModernFrom.T
  object TimeWindowCteModernFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowCteWindowPart1 = WindowCteWindowPart1.T
  object WindowCteWindowPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowCteWindowPart2 = WindowCteWindowPart2.T
  object WindowCteWindowPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowCteModernFrom = WindowCteModernFrom.T
  object WindowCteModernFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InTimeSqlResult = InTimeSqlResult.T
  object InTimeSqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InTimeSqlAlias = InTimeSqlAlias.T
  object InTimeSqlAlias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InTimeSqlAsOf = InTimeSqlAsOf.T
  object InTimeSqlAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InTimeSqlKind = InTimeSqlKind.T
  object InTimeSqlKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InWindowSqlResult = InWindowSqlResult.T
  object InWindowSqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InWindowSqlAlias = InWindowSqlAlias.T
  object InWindowSqlAlias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InWindowSqlWindowPart1 = InWindowSqlWindowPart1.T
  object InWindowSqlWindowPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InWindowSqlWindowPart2 = InWindowSqlWindowPart2.T
  object InWindowSqlWindowPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InWindowSqlKind = InWindowSqlKind.T
  object InWindowSqlKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InWindowSqlModernFrom = InWindowSqlModernFrom.T
  object InWindowSqlModernFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def importedSql(alias: com.histmersion.domain.catalogue.places.PlaceSql.ImportedSqlAlias): com.histmersion.domain.catalogue.places.PlaceSql.ImportedSqlResult =
    s"coalesce($alias.import_source, '') IN $ImportedList"

  /** `('wikidata', 'geonames', 'tgn')` — [[Gazetteer.ImportedSources]] as an SQL list. The partial indexes of
    * V075/V076/V080 (rebuilt by V093 when TGN joined) spell it word for word.
    */
  val ImportedList: com.histmersion.domain.catalogue.places.PlaceSql.ImportedList = Gazetteer.ImportedSources.map(s => s"'$s'").mkString("(", ", ", ")")

  /** A place whose source states NO date (GeoNames, Wikidata — [[PlaceDating.Unknown]]): the open range there means
    * "unknown", not "always". Findable and placeable by name; never on a time-sliced map. Over a `places` row aliased
    * `p`.
    */
  val UndatedImportSql =
    s"(coalesce(p.import_source, '') IN $ImportedList AND lower_inf(p.period) AND upper_inf(p.period))"

  /** [[PlaceDating]] of a `places` row aliased `p` (V074). */
  val datingCol =
    s"CASE WHEN p.start_unknown THEN 'end-only' WHEN $UndatedImportSql THEN 'unknown' ELSE 'stated' END"

  /** The STATED start: `NULL` when it is unknown (the stored lower bound is then only the window the time filters use,
    * V074).
    */
  val startCol = s"CASE WHEN p.start_unknown THEN NULL ELSE ${RangeSql.start("p.period")} END"

  /** Which source wins when two describe the same state at the same time: 1 = OpenHistoricalMap, 2 = every other real
    * dataset (Cliopatria/Seshat, Pleiades, Natural Earth), 3 = the agent-drawn sketches of `db/places/polities` — shown
    * only where nothing better covers them.
    */
  val sourceTierSql: com.histmersion.domain.catalogue.places.PlaceSql.SourceTierSql =
    "CASE WHEN p.properties->>'method' = 'ohm-import' THEN 1" +
      " WHEN p.properties->>'method' = 'agent-drawn' THEN 3 ELSE 2 END"

  /** A label reduced to what identifies the state: lower case, no parentheses, no filler words (of/the/and), no
    * punctuation — so "Old Kingdom of Egypt" and "Old Kingdom Egypt" meet.
    */
  def normLabelSql(col: com.histmersion.domain.catalogue.places.PlaceSql.NormLabelSqlCol): com.histmersion.domain.catalogue.places.PlaceSql.NormLabelSqlResult =
    s"regexp_replace(regexp_replace(lower($col), '\\m(of|the|and)\\M', '', 'g'), '[^a-z0-9]+', '', 'g')"

  /** Share of the SMALLER outline covered by the other above which two same-kind shapes from different sources are
    * taken to be the same state (different names, e.g. "Palatial Minoan Crete" vs "Minoan civilization").
    */
  val SameStateOverlap = 0.5

  /** Labels that name nothing: Pleiades records a site with no known ancient name as "Untitled", "Unnamed place",
    * "Unnamed villa"… Drawn on the map they are noise ("Unnamed place" beside Misrata), so the map skips them; the
    * records stay (findings may be assigned to them). A label that says more — "Unnamed Gate of the Themistoklean Wall"
    * — is kept.
    */
  val UninformativeLabel = """^\s*(untitled|unnamed(\s+\w+)?)\s*$"""

  /** A PRESENT-DAY place (`properties.modern`, the Natural Earth cities and physical features, #987): undated, so valid
    * at every year — kept for finding a name and placing a finding. A view at a time shows a city only from
    * `places.modern-from` ("Kraków" would sit among the sites of 1400 BCE) and a physical feature at any time
    * ([[inTimeSql]]); the map across all time draws neither.
    */
  val ModernSql = modernSql("p")

  /** [[ModernSql]] over a `places` row aliased `alias`. */
  def modernSql(alias: com.histmersion.domain.catalogue.places.PlaceSql.ModernSqlAlias): com.histmersion.domain.catalogue.places.PlaceSql.ModernSqlResult = s"coalesce(($alias.properties->>'modern')::boolean, false)"

  // ── the time rule (2026-10-09): what a view AT A TIME shows ─────────────────────────────────────

  /** The configured `places.modern-from` for callers that hold no config (the tests). It MUST equal `reference.conf`;
    * `PlaceTimeSuite` asserts it. Every production read passes the configured value.
    */
  val DefaultModernFrom: com.histmersion.domain.catalogue.places.PlaceSql.DefaultModernFrom = 1500L

  /** A stored period open on both sides: the source states no date. An unknown start (V074) is not — it stores a
    * window.
    */
  private def undatedSql(alias: com.histmersion.domain.catalogue.places.PlaceSql.UndatedSqlAlias): com.histmersion.domain.catalogue.places.PlaceSql.UndatedSqlResult = s"(lower_inf($alias.period) AND upper_inf($alias.period))"

  /** A physical feature of the seed: there at any time ([[PlaceTimeBasis.Timeless]]). */
  private def timelessSql(alias: com.histmersion.domain.catalogue.places.PlaceSql.TimelessSqlAlias): com.histmersion.domain.catalogue.places.PlaceSql.TimelessSqlResult =
    s"(NOT ${importedSql(alias)} AND ($alias.kind = 'feature' OR ($alias.kind = 'region' AND ${modernSql(alias)})))"

  /** [[PlaceTimeBasis.of]] in SQL, over a `places` row aliased `alias`: the basis's wire name. */
  def timeBasisSql(alias: com.histmersion.domain.catalogue.places.PlaceSql.TimeBasisSqlAlias): com.histmersion.domain.catalogue.places.PlaceSql.TimeBasisSqlResult =
    s"CASE WHEN ${timelessSql(alias)} THEN 'timeless' WHEN ${modernSql(alias)} THEN 'modern'" +
      s" WHEN ${undatedSql(alias)} AND $alias.geom IS NULL THEN 'grouping'" +
      s" WHEN ${undatedSql(alias)} THEN 'findings' ELSE 'dated' END"

  /** The window of a time view as a one-row CTE `tw(w, modern_on)` for the year `asOf`: `w` the window (`NULL` = no
    * time, nothing is filtered), `modern_on` whether it reaches `modern-from`. [[inTimeSql]] reads it; every
    * time-filtered place query starts with it.
    */
  def timeWindowCte(asOf: Option[com.histmersion.domain.catalogue.places.PlaceSql.TimeWindowCteAsOf], modernFrom: com.histmersion.domain.catalogue.places.PlaceSql.TimeWindowCteModernFrom): Sql =
    windowCte(asOf.map(y => (y, y)), modernFrom)

  /** [[timeWindowCte]] for the inclusive window `lo..hi` (the map's area layer over a whole range, [[GeoTime.Within]]):
    * `modern_on` when its END reaches `modern-from`. A year is the window `y..y`.
    */
  def windowCte(window: Option[(com.histmersion.domain.catalogue.places.PlaceSql.WindowCteWindowPart1, com.histmersion.domain.catalogue.places.PlaceSql.WindowCteWindowPart2)], modernFrom: com.histmersion.domain.catalogue.places.PlaceSql.WindowCteModernFrom): Sql =
    import Param.*
    window.fold(Sql.raw("tw AS (SELECT NULL::int8range AS w, false AS modern_on)"))((lo, hi) =>
      Sql.of(
        "tw AS (SELECT int8range(?::bigint, ?::bigint, '[]') AS w, ?::bigint >= ?::bigint AS modern_on)",
        L(lo),
        L(hi),
        L(hi),
        L(modernFrom)
      )
    )

  /** [[PlaceTimeBasis.visible]] in SQL, over a `places` row aliased `alias`, at the year `asOf` against the window of
    * the `tw` CTE ([[timeWindowCte]]); no time filters nothing. A dated place overlaps the window; a physical feature
    * is always there; a present-day place only from `modern-from`; an undated node with no shape is kept (a tree goes
    * on through it); any other undated place only when a finding or an event DATED within the window is attached to it
    * — in [[timeBasisSql]]'s order.
    *
    * Led by an overlap with the year as a LITERAL (a `Long`, so nothing to escape): every place the rule shows overlaps
    * the window (an undated one is open on both sides) except a physical feature dated outside it, so
    * `period && [y, y]` — OR'ed with "physical" only where `kind` can be one — is implied by the rule. Read from the
    * CTE (`(SELECT tw.w FROM tw)`, an init-plan whose value the planner cannot see) the overlap was estimated at 13 000
    * rows and `places_period_gist` was AND'ed into every candidate scan — 1.19 M rows, 40 ms of a place search's 47,
    * because every undated import overlaps every year (2026-10-09). With the year visible the planner knows the overlap
    * keeps nearly everything and finds the candidates by what does narrow them: the trigram index of a search, the
    * (kind, period) GiSTs of a map layer (V076).
    */
  def inTimeSql(alias: com.histmersion.domain.catalogue.places.PlaceSql.InTimeSqlAlias, asOf: Option[com.histmersion.domain.catalogue.places.PlaceSql.InTimeSqlAsOf], kind: Option[com.histmersion.domain.catalogue.places.PlaceSql.InTimeSqlKind] = None): com.histmersion.domain.catalogue.places.PlaceSql.InTimeSqlResult =
    inWindowSql(alias, asOf.map(y => (y, y)), kind)

  /** [[inTimeSql]] over the inclusive window `lo..hi` ([[windowCte]]): shown at ANY year of it. With `modernFrom` the
    * rule reads the window and whether it reaches `modern-from` as literals instead of from the `tw` CTE, which then
    * need not head the statement: a map tile's query (`TileRepo`), where the planner, seeing the window, probes the
    * findings of each undated place through its `finding_places` rows — through the CTE's init-plan it read every
    * finding of the window for each of them (2.5 s of a sites tile over -3000..2000, 2026-10-27).
    */
  def inWindowSql(
      alias: com.histmersion.domain.catalogue.places.PlaceSql.InWindowSqlAlias,
      window: Option[(com.histmersion.domain.catalogue.places.PlaceSql.InWindowSqlWindowPart1, com.histmersion.domain.catalogue.places.PlaceSql.InWindowSqlWindowPart2)],
      kind: Option[com.histmersion.domain.catalogue.places.PlaceSql.InWindowSqlKind] = None,
      modernFrom: Option[com.histmersion.domain.catalogue.places.PlaceSql.InWindowSqlModernFrom] = None
  ): com.histmersion.domain.catalogue.places.PlaceSql.InWindowSqlResult =
    window.fold("true") { (lo, hi) =>
      val w = modernFrom.fold("(SELECT tw.w FROM tw)")(_ => s"int8range($lo::bigint, $hi::bigint, '[]')")
      val modernOn = modernFrom.fold("(SELECT tw.modern_on FROM tw)")(m => if hi >= m then "true" else "false")
      val modern = modernSql(alias)
      val undated = undatedSql(alias)
      val timeless = timelessSql(alias)
      val overlap = RangeSql.overlaps(s"$alias.period", s"$lo::bigint", s"$hi::bigint")
      val physicalKind = kind.forall(k => k == Place.Kind.Feature || k == Place.Kind.Region)
      val lead = if physicalKind then s"($overlap OR $timeless)" else overlap
      s"($lead AND ($timeless" +
        s" OR ($modern AND $modernOn)" +
        s" OR (NOT $undated AND NOT $modern)" +
        s" OR (NOT $modern AND $undated AND $alias.geom IS NULL)" +
        s" OR (NOT $modern AND $undated AND $alias.geom IS NOT NULL AND (" +
        s"EXISTS (SELECT 1 FROM finding_places fp JOIN findings f ON f.id = fp.finding_id" +
        s" WHERE fp.place_id = $alias.id AND f.period && $w AND NOT (lower_inf(f.period) AND upper_inf(f.period)))" +
        s" OR EXISTS (SELECT 1 FROM events e WHERE e.place_id = $alias.id AND e.outer_span && $w" +
        s" AND NOT (lower_inf(e.outer_span) AND upper_inf(e.outer_span)))))))"
    }

  /** The BASE layers (polity, region, site, feature) draw the curated gazetteer: the bundled seed, a person's own
    * places and LPF datasets. A GeoNames or Wikidata place is drawn only once a finding is attached to it
    * (`finding_places`) — a picked or searched place is drawn by the place API instead. Explicit here, not left to
    * ranking: 1.2 M imported points would otherwise fill the map with dots that are neither sites nor boundaries. Over
    * a `places` row aliased `p`.
    */
  val BaseLayerSql =
    s"(p.import_source IS NULL OR p.import_source NOT IN $ImportedList" +
      " OR EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id))"

  /** The curated half of [[BaseLayerSql]] — word for word the predicate of the partial index `places_base_layer`
    * (V075), which is what lets the planner use it.
    */
  val CuratedSql = s"(p.import_source IS NULL OR p.import_source NOT IN $ImportedList)"

  /** NOT a present-day GeoNames/Wikidata place — word for word the predicate of the partial indexes
    * `places_before_modern` and `places_before_modern_label_trgm` (V076). A view at a year before `places.modern-from`
    * never shows such a place ([[inTimeSql]]: it is neither a physical feature, being imported, nor shown by findings,
    * being present-day), so there this is implied by the time rule and only tells the planner which indexes hold the
    * candidates.
    */
  val ShownBeforeModernSql = s"NOT (${importedSql("p")} AND ${modernSql("p")})"

  /** A DATED GeoNames/Wikidata place — word for word the predicate of the partial index `places_dated_import` (V076).
    * At a year the imported half of a base layer is exactly these (an undated import is never drawn then), and saying
    * so lets the planner read the few hundred alive that year instead of every imported place a finding is attached to.
    */
  val DatedImportSql =
    s"(p.import_source IN $ImportedList AND NOT (lower_inf(p.period) AND upper_inf(p.period)))"

  /** Not an undated polity known only by a point, over a `places` row aliased `p`: a map at a year never draws one,
    * findings or not (region-cleanup, 2026-10-09) — a state is not "always". The dimension is the stored `shape_dim`
    * (V090): `ST_Dimension(p.geom)` decompressed every polity alive.
    */
  val UndatedPolityPointSql =
    "NOT (p.kind = 'polity' AND p.shape_dim = 0 AND lower_inf(p.period) AND upper_inf(p.period))"
