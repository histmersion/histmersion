package com.histmersion
package domain.library.search

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.database.given

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.util.database.{joined, RangeSql}

import com.histmersion.api.{BBox, Markdown}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.augustnagro.magnum.DbCodec
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Filter, SearchHit, Sort}
import com.histmersion.api.library.Source
import com.histmersion.api.library.Facet
import com.histmersion.api.library.Finding
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.library.SourceColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.LikePattern
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.Title
import com.histmersion.util.database.EnvelopeSql
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Doi
import com.histmersion.domain.Highlight
import com.histmersion.api.library.Hit
import com.augustnagro.magnum.{sql, Frag}
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** `Filter` → SQL, as a **pure function** (plan §1.0/§2.2): every builder here returns a value that can be asserted on
  * in a unit test without a database. `SourceRepo` is the only thing that turns one into a Magnum [[Frag]] and runs it.
  *
  * Conventions: the `sources` table is always aliased `s`; predicates that reach into `findings` do so with a
  * correlated `EXISTS`, because **the source is the unit of the result** — a source matches `period`/`bbox`/`places`
  * when its own value matches OR any of its findings do, and `tags` matches `sources.tags ∪ findings.tags`.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "turns a search filter into SQL fragments")
object FilterSql:

  type FacetPrefix = FacetPrefix.T
  object FacetPrefix extends Newtype[String, Pure]

  // ── LIKE handling ────────────────────────────────────────────────────────────────────────────
  /** `\` is the escape character in every `LIKE`/`ILIKE` below, so a user's `%`/`_` is literal.
    *
    * Inside a Magnum `sql"…"` the clause must be spelt `ESCAPE '\\'`: the interpolator processes escapes like `s"…"`,
    * so a bare `'\'` reached Postgres as `ESCAPE ''` (no escape character at all), every `\_` in the pattern then
    * needed a literal backslash, and a search for `ZZR_` matched nothing (#769).
    */
  private val Esc = sql" ESCAPE '\\'"
  def escapeLike(s: String): String = LikePattern.escape(s)
  def contains(s: String): String = LikePattern.contains(s)
  def startsWith(s: String): String = LikePattern.startsWith(s)

  // ── combinators ──────────────────────────────────────────────────────────────────────────────
  private val And = sql" AND "
  private def anyOf(parts: List[Frag]): Frag =
    if parts.sizeIs == 1 then parts.head else sql"(${parts.joined(sql" OR ")})"

  // ── places: gazetteer ids (plan §1.6a) with the wave-1 name match as a fallback ──────────────
  // A filter value the gazetteer recognised (see [[PlaceScope]], resolved once per query by the
  // handler) matches through the MATERIALISED `finding_places` — the place itself and, via the
  // hierarchy as of the filter's period, everything under it. Only a place ID resolves, and an id is
  // no name, so a resolved value is matched through `finding_places` alone; an unresolved value (a
  // name) is the original name ILIKE over `sources.regions ∪ findings.region_name`, as before.

  /** `finding_places` membership for a source: an UNCORRELATED semi-join with the ids bound as ONE `text[]`. The
    * correlated `EXISTS (… WHERE f.citekey = s.citekey AND fp.place_id IN (?, ?, …))` it replaces re-ran per source —
    * and, inside the Articles list's `al.target IN (…)`, per ARTICLE LINK (1 955 times): with a polity's 451
    * descendants, each run was 1 284 index searches and the hover card's article count took 15 s on a live-sized copy
    * (2026-10-09). Uncorrelated, Postgres computes the matching citekeys once (a hashed sub-plan) whatever OR or IN
    * surrounds it, and one array parameter keeps a generic plan of a prepared statement from going bad on 451 `?`s.
    */
  private def placeIdsSource(ids: List[Place.Id]): Frag =
    sql"s.citekey IN (SELECT f.citekey FROM findings f JOIN finding_places fp ON fp.finding_id = f.id WHERE fp.place_id = ANY($ids::text[]))"

  /** `finding_places` membership for ONE finding row (alias `fa`), uncorrelated like [[placeIdsSource]]. */
  private def placeIdsFinding(ids: List[Place.Id], fa: Frag): Frag =
    sql"$fa.id IN (SELECT fp.finding_id FROM finding_places fp WHERE fp.place_id = ANY($ids::text[]))"

  /** The wave-1 name match for a source: its own regions, any of its findings' `region_name`, OR any place_name
    * matching by ILIKE prefix (so "Anatolian" reaches "Anatolian plateau" etc.).
    */
  private def placeNameSource(p: String): Frag =
    sql"(EXISTS (SELECT 1 FROM unnest(s.regions) x WHERE x ILIKE ${contains(p)}$Esc) OR EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey AND f.region_name ILIKE ${contains(p)}$Esc) OR EXISTS (SELECT 1 FROM findings f JOIN finding_places fp ON fp.finding_id = f.id JOIN place_names pn ON pn.place_id = fp.place_id WHERE f.citekey = s.citekey AND pn.name ILIKE ${contains(p)}$Esc))"

  /** A value the gazetteer resolved is matched through `finding_places` ALONE. It used to be OR'ed with the name match
    * on the value itself — the ID ("pleiades_570577") as an ILIKE over every finding's place names — which can match
    * nothing and, since GeoNames and Wikidata brought 8 M names in (#1064), made a place's hover counts take 3–20 s
    * (2026-10-09).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def placeSource(p: Filter.Places, scope: PlaceScope): Frag =
    scope.idsFor(PlaceScope.IdsForValue(p.value)) match
      case Some(ids) => placeIdsSource(ids)
      case None      => placeNameSource(p.value)

  /** `finding_places` membership for ONE finding row (alias `fa`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def placeFinding(p: Filter.Places, scope: PlaceScope, fa: Frag): Frag =
    val byName = sql"$fa.region_name ILIKE ${contains(p.value)}$Esc"
    scope.idsFor(PlaceScope.IdsForValue(p.value)) match
      case Some(ids) =>
        placeIdsFinding(ids, fa)
      case None => byName

  // ── the WHERE predicate ──────────────────────────────────────────────────────────────────────

  /** The boolean expression selecting the sources this filter matches. Always a valid expression: an empty filter
    * yields `TRUE`. Predicates appear in `Filter` field order, which is what the unit tests pin down.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def where(f: Filter, scope: PlaceScope = PlaceScope.empty): Frag =
    val ps = List.newBuilder[Frag]

    // free text = Postgres FTS (plan §1.4): the weighted title/summary vector OR any body chunk.
    // `websearch` syntax ("phrase", -not, or) — see [[Search]].
    f.q.map(_.value.trim).filter(_.nonEmpty).foreach(q => ps += Search.ftsPredicate(Search.Query(q)))

    // tags: AND semantics, over sources.tags ∪ findings.tags
    f.tags.foreach { t =>
      ps += sql"(EXISTS (SELECT 1 FROM unnest(s.tags) x WHERE lower(x) = lower($t)) OR EXISTS (SELECT 1 FROM findings f, unnest(f.tags) x WHERE f.citekey = s.citekey AND lower(x) = lower($t)))"
    }

    // authors: any-of, case-insensitive substring
    if f.authors.nonEmpty then
      ps += anyOf(
        f.authors.map(a => sql"EXISTS (SELECT 1 FROM unnest(s.authors) x WHERE x ILIKE ${contains(a.value)}$Esc)")
      )

    // places: any-of — a gazetteer id through `finding_places`, otherwise the name ILIKE over
    // sources.regions ∪ findings.region_name (so "Anatolia" still finds "Central Anatolia").
    if f.places.nonEmpty then ps += anyOf(f.places.map(placeSource(_, scope)))

    // topics: AND semantics (chip narrowing, like tags); sources only — findings carry no topics
    f.topics.foreach { t =>
      ps += sql"EXISTS (SELECT 1 FROM unnest(s.topics) x WHERE lower(x) = lower($t))"
    }

    if f.status.nonEmpty then
      ps += sql"lower(s.status) IN (${f.status.map(x => sql"${x.value.trim.toLowerCase}").joined(sql", ")})"

    f.years.foreach(y => ps += sql"(s.year IS NOT NULL AND s.year BETWEEN ${y.start} AND ${y.end})")

    f.period.foreach(p =>
      ps += sql"(${RangeSql.overlaps(sql"s.period", p)} OR EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey AND ${RangeSql.overlaps(sql"f.period", p)}))"
    )

    f.bbox.foreach(b =>
      ps += (sql"EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey AND f.geom IS NOT NULL AND ${envelopes(sql"f.geom", b)})")
    )

    // the global title quick-filter: a case-insensitive substring of the source's own title
    titleLike(f).foreach(t => ps += sql"s.title ILIKE ${contains(t.value)}$Esc")

    // a research goal's sources: those it links
    f.goal
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .foreach(g => ps += sql"s.citekey IN (SELECT ref FROM goal_links WHERE goal_slug = $g AND kind = 'source')")

    val parts = ps.result()
    if parts.isEmpty then sql"TRUE" else parts.joined(And)

  /** The filter's title quick-filter, trimmed; `None` when there is nothing to match. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def titleLike(f: Filter): Option[Title] =
    f.title.map(_.value.trim).filter(_.nonEmpty).map(Title(_))

  // ── what the filter selects, seen from the other lists (articles, events) ───────────────────
  // ONE rule, reused: the Articles and Events lists are the items RELATED to the sources the
  // Sources list shows — the very same `where` as a sub-select, never a second copy of the filter
  // semantics. `title` is not part of the source set: on those lists it names the item's own title.

  /** The citekeys of the sources `f` selects — `/api/sources`' predicate as a sub-select. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def sourceKeys(f: Filter, scope: PlaceScope = PlaceScope.empty): Frag =
    sql"SELECT s.citekey FROM sources s WHERE ${where(f, scope)}"

  /** [[sourceKeys]] as an ARRAY computed ONCE per statement (an init-plan): `x = ANY(…)` against it is
    * `x IN (sourceKeys)`. Written as `IN (sub-select)` the planner pulls the sources back into a nested loop and
    * evaluates the filter's correlated sub-plans once per ARTICLE LINK — 164 to 1 955 times instead of once per source;
    * a free-text place chip's article list took 240 ms, a polity's 15 s (2026-10-09). The set is at most the corpus'
    * citekeys, small by construction.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def sourceKeySet(f: Filter, scope: PlaceScope): Frag =
    sql"ARRAY(${sourceKeys(f, scope)})"

  /** The articles for a filter (alias `a`): those CITING at least one source of `f.sourceSet` (`article_links`,
    * maintained at write time from the body's `[[citekey]]`s), and whose title contains `f.title`. A filter without
    * source criteria restricts nothing.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def articleWhere(f: Filter, scope: PlaceScope = PlaceScope.empty): Frag =
    val set = f.sourceSet
    val ps = List.newBuilder[Frag]
    if !set.isEmpty then
      ps += (sql"EXISTS (SELECT 1 FROM article_links al WHERE al.src_slug = a.slug AND al.target_kind = 'source' AND al.target = ANY(${sourceKeySet(set, scope)}))")
    titleLike(f).foreach(t => ps += sql"a.title ILIKE ${contains(t.value)}$Esc")
    val parts = ps.result()
    if parts.isEmpty then sql"TRUE" else parts.joined(And)

  /** The events for a filter (alias `e`): whose source (`e.citekey`) is among the sources of `f.sourceSet` WITHOUT its
    * period, whose OWN span overlaps the period, and whose label contains `f.title`. The period applies to the event
    * and not to its source because an event carries its own dating — the period a source covers is not the event's. No
    * source criteria besides the period ⇒ no condition on the source (an event without one stays listed).
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def eventWhere(f: Filter, scope: PlaceScope = PlaceScope.empty): Frag =
    val set = f.sourceSet.copy(period = None)
    val ps = List.newBuilder[Frag]
    if !set.isEmpty then
      // its own source, or a source that attests it (#1449)
      val keys = sourceKeySet(set, scope)
      ps += (sql"(e.citekey = ANY($keys) OR EXISTS (SELECT 1 FROM attestations ea WHERE ea.target_type = 'event' AND ea.target_id = e.id AND ea.citekey = ANY($keys)))")
    f.period.foreach(p => ps += RangeSql.overlaps(sql"e.outer_span", p))
    titleLike(f).foreach(t => ps += sql"e.label ILIKE ${contains(t.value)}$Esc")
    val parts = ps.result()
    if parts.isEmpty then sql"TRUE" else parts.joined(And)

  /** The PostGIS predicate for a viewport. A box with `minLon > maxLon` crosses the **antimeridian** and becomes two
    * envelopes OR'ed together (`minLon…180` and `-180…maxLon`).
    */
  def envelopes(geom: Frag, b: BBox): Frag = EnvelopeSql.envelopes(geom, b)

  // ── the FINDING-level predicate (map feed §1.1, histogram §1.7) ─────────────────────────────
  // `/api/sources` asks "does this SOURCE match?" — an EXISTS quantified over its findings (above).
  // `/api/findings` and `/api/aggregate` ask "does this FINDING match?": the same criteria applied to
  // one finding row. Split into period / non-period because the histogram's LATERAL needs the period
  // criterion on its own (`count(*) FILTER (WHERE …)`).

  /** The filter with every finding-level criterion removed — what is left is what a SOURCE must satisfy on its own:
    * `q`, authors, topics, status, publication years.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def sourceOnly(f: Filter): Filter =
    f.modify(_.tags).setTo(Nil).modify(_.places).setTo(Nil).modify(_.period).setTo(None).modify(_.bbox).setTo(None)

  /** Source-level predicate for a query whose row is a FINDING (its source joined in as `s`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def sourceWhere(f: Filter): Frag = where(sourceOnly(f))

  /** True when a finding must satisfy something besides the period — so a source with no findings at all cannot match
    * (the place filters have always been finding-level).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def hasFindingPlaceCriteria(f: Filter): Boolean =
    f.tags.nonEmpty || f.places.nonEmpty || f.bbox.isDefined

  /** The non-temporal criteria for ONE finding: tags (over `findings.tags ∪ sources.tags`, AND semantics), places
    * (any-of, ILIKE on `region_name`) and the viewport (antimeridian-split). `TRUE` when the filter sets none of them.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def findingPlaceWhere(f: Filter, fa: Frag = sql"f", sa: Frag = sql"s", scope: PlaceScope = PlaceScope.empty): Frag =
    val ps = List.newBuilder[Frag]

    f.tags.foreach { t =>
      ps += sql"(EXISTS (SELECT 1 FROM unnest($fa.tags) x WHERE lower(x) = lower($t)) OR EXISTS (SELECT 1 FROM unnest($sa.tags) x WHERE lower(x) = lower($t)))"
    }

    if f.places.nonEmpty then ps += anyOf(f.places.map(placeFinding(_, scope, fa)))

    f.bbox.foreach(b => ps += (sql"($fa.geom IS NOT NULL AND ${envelopes(sql"$fa.geom", b)})"))

    val parts = ps.result()
    if parts.isEmpty then sql"TRUE" else parts.joined(And)

  /** Deep-time overlap for ONE finding; `TRUE` when the filter carries no period. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def findingPeriodWhere(f: Filter, fa: Frag = sql"f"): Frag =
    f.period.fold(sql"TRUE")(p => RangeSql.overlaps(sql"$fa.period", p))

  /** Everything ONE finding must satisfy: the period AND the rest. `TRUE` for an empty filter. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def findingWhere(f: Filter, fa: Frag = sql"f", sa: Frag = sql"s", scope: PlaceScope = PlaceScope.empty): Frag =
    val parts = List(findingPeriodWhere(f, fa), findingPlaceWhere(f, fa, sa, scope)).filter(_.sqlString != "TRUE")
    if parts.isEmpty then sql"TRUE" else parts.joined(And)

  /** Deterministic ordering (`, s.citekey` last) so paging is stable. `Relevance` WITHOUT a text query has nothing to
    * rank by, so it falls back to citations; with one it is `ts_rank_cd` — see [[orderBySql]], which is what the
    * queries actually use.
    */
  @tolerated(LintRule.TotalOrder, "s is sources: citekey is its key")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def orderBy(sort: Sort): Frag = sort match
    case Sort.Relevance | Sort.Cited => sql"ORDER BY s.cited_by_count DESC NULLS LAST, s.citekey"
    case Sort.PeriodStart            => sql"ORDER BY ${RangeSql.start(sql"s.period")} ASC NULLS LAST, s.citekey"
    case Sort.Year                   => sql"ORDER BY s.year DESC NULLS LAST, s.citekey"
    case Sort.Oldest                 => sql"ORDER BY s.year ASC NULLS LAST, s.citekey"
    case Sort.Title                  => sql"ORDER BY lower(s.title) ASC, s.citekey"

  /** The ORDER BY as a fragment: `Sort.Relevance` + a text query ranks by FTS relevance (`ts_rank_cd` over
    * title/summary plus the best body chunk), citations breaking ties; everything else is the parameterless ordering
    * above.
    *
    * When `useCte` is true, ranking uses the precomputed `best_chunks` CTE (one scan of `source_chunks`) instead of a
    * per-source correlated subquery. The CTE path is the default for [[sourcesQuery]]; the legacy correlated-subquery
    * path is kept for callers that do not prepend the CTE.
    */
  @tolerated(LintRule.TotalOrder, "the relevance rank, then cited_by_count, then s.citekey, the sources' key")
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def orderBySql(f: Filter, useCte: Boolean = false): Frag = (f.sort, text(f)) match
    case (Sort.Relevance, Some(q)) =>
      val rank = if useCte then Search.rankExprCte(q) else Search.rankExpr(q)
      sql"ORDER BY $rank DESC, s.cited_by_count DESC NULLS LAST, s.citekey"
    case _ => orderBy(f.sort)

  /** The filter's text query, trimmed; `None` when there is nothing to search for. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def text(f: Filter): Option[Search.Query] =
    f.q.map(_.value.trim).filter(_.nonEmpty).map(Search.Query(_))

  // ── the queries ──────────────────────────────────────────────────────────────────────────────

  /** Columns of [[SourceRow]] — `SearchHit` plus what `why` needs (tags, authors, body-hit). */
  private val rowCols =
    sql"s.citekey, s.title, s.year, s.status, s.topics, s.regions, ${RangeSql.cols(sql"s.period")}, s.summary, s.tags, s.authors, s.doi"

  /** The page of matching sources (plan §1.4). For a text query, uses a two-phase approach:
    *
    *   1. A CTE (`best_chunks`) scans `source_chunks` ONCE to find each source's best-scoring chunk rank — replacing
    *      the N per-source correlated subqueries of the old LATERAL approach.
    *   2. The main SELECT joins the CTE for ranking and `body_hit`, but does NOT compute snippets (`ts_headline` is the
    *      expensive part). Snippets are deferred to [[snippetsQuery]], which the caller runs separately for just the
    *      page's citekeys.
    *
    * `SourceRow.snippet` and `matchedHeading` are always NULL from this query; the caller merges them in from
    * [[snippetsQuery]]. `body_hit` is true when the CTE found a matching chunk.
    *
    * Parameter order is textual: CTE, then SELECT, then WHERE, then ORDER BY, then limit/offset.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def sourcesQuery(f: Filter, snippetWords: Search.SnippetWords, scope: PlaceScope = PlaceScope.empty): Frag =
    val (cte, cols, join) = text(f) match
      case Some(q) =>
        (
          Search.bestChunkCte(q),
          sql"(bc.citekey IS NOT NULL) AS body_hit, NULL::text AS snippet, NULL::text AS heading",
          sql" LEFT JOIN best_chunks bc ON bc.citekey = s.citekey"
        )
      case None =>
        (sql"", sql"FALSE AS body_hit, NULL::text, NULL::text", sql"")
    sql"${cte}SELECT $rowCols, $cols FROM sources s$join WHERE ${where(f, scope)} ${orderBySql(f, useCte = text(f).isDefined)} LIMIT ${f.limit.value.max(0)} OFFSET ${f.offset.value.max(0)}"

  /** `total` = count(DISTINCT source) for the filter, ignoring limit/offset. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def countQuery(f: Filter, scope: PlaceScope = PlaceScope.empty): Frag =
    sql"SELECT count(*)::int FROM sources s WHERE ${where(f, scope)}"

  /** Snippets for a specific set of citekeys — the second phase of the two-phase FTS approach. Returns
    * `(citekey, heading, snippet)` rows, one per citekey, with `ts_headline` wrapping hits in `**`. An empty `citekeys`
    * list is a no-op (the caller should skip the query).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def snippetsQuery(q: Search.Query, snippetWords: Search.SnippetWords, citekeys: List[Citekey]): Frag =
    Search.snippetsQuery(q, snippetWords, citekeys)

  /** One snippet row: (citekey, heading, snippet). */
  final case class SnippetRow(citekey: Citekey, heading: Option[SnippetRow.Heading], snippet: SnippetRow.Snippet)
      derives DbCodec

  object SnippetRow:
    type Heading = Heading.T
    object Heading extends Newtype[String, Pure]
    type Snippet = Snippet.T
    object Snippet extends Newtype[String, Pure]

  /** Every finding of the page's sources; which of them actually *matched* is decided purely, by [[findingWhy]], so the
    * explanation is unit-testable without a database.
    */
  @tolerated(LintRule.TotalOrder, "finding_places of one finding (fp.finding_id = f.id): place_id completes the key")
  def findingsQuery(citekeys: List[Citekey]): Frag =
    // the gazetteer places each finding was materialised into (plan §1.6a) — labels for the reader, ids so
    // `findingWhy` can say WHICH place chip the finding satisfied. Arrays, not joined strings: a place label routinely
    // contains a comma.
    sql"SELECT f.citekey, f.id, f.claim, f.region_name, ST_X(f.geom), ST_Y(f.geom), ${RangeSql.cols(sql"f.period")}, f.period_name, f.tags, ARRAY(SELECT p.label FROM finding_places fp JOIN places p ON p.id = fp.place_id WHERE fp.finding_id = f.id ORDER BY p.label, p.id), ARRAY(SELECT fp.place_id FROM finding_places fp WHERE fp.finding_id = f.id ORDER BY fp.place_id) FROM findings f WHERE f.citekey IN (${citekeys
        .map(c => sql"$c")
        .joined(sql", ")}) ORDER BY f.citekey, f.id"

  /** Columns of [[com.histmersion.api.library.GeoHit]] — one map-feed row (a finding plus its source's title). */
  private val geoCols =
    // after the dates: what the source is (core.SourceKinds), for the marker's icon: its venue, provenance, words, and
    // whether it has a cover (`m.role` is `MediaRole.Cover` word for word; FilterSqlSuite holds that)
    sql"f.citekey, s.title, f.claim, f.region_name, ST_X(f.geom), ST_Y(f.geom), ${RangeSql.cols(sql"f.period")}, f.confidence, f.significance, s.doi, s.venue, s.source, s.tags, s.topics, EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'source' AND m.target_id = s.citekey AND m.role = 'cover')"

  /** `GET /api/findings` — the MAP FEED: every finding that ITSELF satisfies the finding-level criteria
    * ([[findingWhere]]), belonging to a source that satisfies the source-level ones ([[sourceWhere]]). Ordered
    * oldest-first, with a deterministic tiebreak so paging is stable.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def findingsFeedQuery(f: Filter, scope: PlaceScope = PlaceScope.empty): Frag =
    sql"SELECT $geoCols FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE ${sourceWhere(f)} AND ${findingWhere(f, scope = scope)} ORDER BY ${RangeSql.start(sql"f.period")} NULLS LAST, f.citekey, f.id LIMIT ${f.limit.value.max(0)} OFFSET ${f.offset.value.max(0)}"

  /** How many findings [[findingsFeedQuery]] would return without its `LIMIT` — the same WHERE, so a count and the map
    * feed can never disagree about which findings a filter selects.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def findingsCountQuery(f: Filter, scope: PlaceScope = PlaceScope.empty): Frag =
    sql"SELECT count(*)::int FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE ${sourceWhere(f)} AND ${findingWhere(f, scope = scope)}"

  /** What a filter selects in EACH list — sources, findings, articles, events — as ONE statement of four scalar counts:
    * the same predicates as [[countQuery]], [[findingsCountQuery]], [[articleWhere]] and [[eventWhere]], in one round
    * trip on one connection. A map place's hover card asks for exactly this; as four repository calls it was four
    * checkouts and four statements, one of them a page of articles fetched only for its total.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def selectionCountsQuery(f: Filter, scope: PlaceScope = PlaceScope.empty): Frag =
    sql"SELECT (${countQuery(f, scope)}), (${findingsCountQuery(f, scope)}), (SELECT count(*)::int FROM articles a WHERE ${articleWhere(f, scope)}), (SELECT count(*)::int FROM events e WHERE ${eventWhere(f, scope)})"

  /** The set of values a facet dimension draws from, as a LATERAL sub-select correlated on `s`. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def facetValues(field: Facet.Field): Frag = field match
    case Facet.Field.Tags =>
      sql"SELECT x FROM unnest(s.tags) x UNION SELECT y FROM findings f, unnest(f.tags) y WHERE f.citekey = s.citekey"
    case Facet.Field.Authors => sql"SELECT x FROM unnest(s.authors) x"
    case Facet.Field.Places  =>
      // author labels (wave 1) UNION the gazetteer labels the findings were materialised into (§1.6a)
      sql"SELECT x FROM unnest(s.regions) x UNION SELECT f.region_name FROM findings f WHERE f.citekey = s.citekey AND f.region_name IS NOT NULL UNION SELECT p.label FROM findings f JOIN finding_places fp ON fp.finding_id = f.id JOIN places p ON p.id = fp.place_id WHERE f.citekey = s.citekey"
    case Facet.Field.Topics     => sql"SELECT x FROM unnest(s.topics) x"
    case Facet.Field.Status     => sql"SELECT s.status"
    case Facet.Field.Provenance => sql"SELECT coalesce(s.source, 'unknown')"

  /** `(value, count(DISTINCT source))` for one dimension inside the current filter, most-used first. `prefix` is a
    * case-insensitive prefix match (for chip autocomplete).
    *
    * Values are grouped CASE-INSENSITIVELY (`GROUP BY lower(v)`) — `Anatolia` and `anatolia` are one bucket — and the
    * label shown is the most frequent original spelling (`mode()`), so a chip inserts a value that exists in the data.
    * Matching (`Filter.tags/topics/status` on `lower()`, `places` on `ILIKE`) is case-insensitive too, so either
    * spelling finds the whole bucket.
    */
  @tolerated(LintRule.TotalOrder, "GROUP BY lower(v): one row per lower(v)")
  @tolerated(LintRule.TotalOrder, "mode() WITHIN GROUP (ORDER BY v): rows tied on v hold the same value")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def facetsQuery(
      field: Facet.Field,
      prefix: Option[FacetPrefix],
      f: Filter,
      limit: PageSize,
      scope: PlaceScope = PlaceScope.empty
  ): Frag =
    val pre = prefix.map(_.value.trim).filter(_.nonEmpty) match
      // matched as DISPLAYED: the UI shows `_` as a space (Fmt.display), so "bronze age" and
      // "bronze_age" both find `bronze_age_collapse`
      case Some(p) => sql" AND replace(v, '_', ' ') ILIKE ${startsWith(p.replace('_', ' '))}$Esc"
      case None    => sql""
    // `places` additionally reports the gazetteer id behind a label (`lower(v)` IS the group key, so
    // the scalar sub-select is legal) — that is what lets a chip filter by id instead of by name.
    // (`min(lower(v))` rather than `lower(v)`: every row of the group has the same value, and an
    // aggregate is what makes the correlated sub-select legal under GROUP BY.) The two columns share
    // ONE definition and ONE ordering, so they always describe the same place.
    def gazetteer(col: Frag) =
      sql"(SELECT $col FROM place_names pn JOIN places p ON p.id = pn.place_id WHERE lower(pn.name) = min(lower(v)) ORDER BY (lower(p.label) = min(lower(v))) DESC, p.id LIMIT 1)"
    val idCol =
      if field == Facet.Field.Places then
        sql", ${gazetteer(sql"pn.place_id")} AS place_id, coalesce(${gazetteer(sql"(p.properties->>'contested')::boolean")}, false) AS contested"
      else sql", NULL::text AS place_id, FALSE AS contested"
    sql"SELECT mode() WITHIN GROUP (ORDER BY v) AS value, count(DISTINCT s.citekey)::int AS count$idCol FROM sources s CROSS JOIN LATERAL (${facetValues(field)}) AS t(v) WHERE ${where(f, scope)} AND v IS NOT NULL AND v <> ''$pre GROUP BY lower(v) ORDER BY count(DISTINCT s.citekey) DESC, lower(v) ASC LIMIT ${limit.value.max(1)}"

  // ── pure match explanation ───────────────────────────────────────────────────────────────────

  type SourceDoi = SourceDoi.T
  object SourceDoi extends Newtype[String, Pure]

  /** One row of [[sourcesQuery]]: a `SearchHit` plus the extra columns `why` needs. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class SourceRow(
      citekey: Citekey,
      title: com.histmersion.api.library.Title,
      year: Option[Year],
      status: Source.Status,
      topics: List[SearchHit.Topics],
      regions: List[SearchHit.Regions],
      periodStart: Option[Year],
      periodEnd: Option[Year],
      summary: Option[SearchHit.Summary],
      tags: List[Filter.Tags],
      authors: List[com.histmersion.api.library.AuthorName],
      doi: Option[SourceDoi] = None,
      bodyHit: Boolean,
      snippet: Option[SearchHit.Snippet] = None,
      matchedHeading: Option[SearchHit.MatchedHeading] = None
  ) derives DbCodec:
    /** The WIRE shape: arrays as proper lists. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def toHit: SearchHit =
      SearchHit(
        com.histmersion.api.library.Citekey(citekey.value),
        title,
        year,
        status,
        topics,
        regions,
        periodStart,
        periodEnd,
        summary,
        snippet,
        matchedHeading,
        doi.map(d => Doi(d.value)),
        authors
      )

  private def ci(a: String, b: String) = a.equalsIgnoreCase(b)
  private def has(values: List[String], v: String) = values.exists(ci(_, v))
  private def like(values: List[String], v: String) = values.find(_.toLowerCase.contains(v.toLowerCase))

  /** The words a `websearch_to_tsquery` string actually asks for: quotes stripped, `-excluded` terms and the `or`/`and`
    * connectives dropped. The SQL side is a tsquery, so the explanation cannot just look for the raw string —
    * `"sea peoples"` never occurs verbatim in a title.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def queryTerms(q: Search.Query): List[Highlight.Term] =
    q.value
      .split("\\s+")
      .toList
      .map(_.replace("\"", "").trim.toLowerCase)
      .filterNot(t => t.isEmpty || t.startsWith("-") || t == "or" || t == "and")
      .map(Highlight.Term(_))

  /** Inclusive `[start, end]` overlap — the Scala twin of `int8range(a, b, '[]') &&`. */
  def overlaps(start: Option[Year], end: Option[Year], p: Period): Boolean =
    (start, end) match
      case (Some(a), Some(b)) => a <= p.end && b >= p.start
      case _                  => false

  /** Source-level reasons this source is in the result — "title matches 'collapse'", "tag: sea-peoples", … Pure:
    * everything comes from the row the SQL returned.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def sourceWhy(f: Filter, r: SourceRow): List[Hit.Rationale] =
    val b = List.newBuilder[String]
    text(f).foreach { q =>
      val terms = queryTerms(q)
      def hits(s: String) = terms.nonEmpty && terms.forall(t => s.toLowerCase.contains(t.value))
      if hits(r.title.value) then b += s"title matches '${q.value}'"
      if r.summary.exists(x => hits(x.value)) then b += s"summary matches '${q.value}'"
      if r.bodyHit then b += s"full text matches '${q.value}'"
    }
    f.tags.foreach(t => if has(r.tags.map(_.value), t.value) then b += s"tag: ${t.value}")
    f.authors.foreach(a => like(r.authors.map(_.value), a.value).foreach(x => b += s"author: $x"))
    f.places.foreach(p => like(r.regions.map(_.value), p.value).foreach(x => b += s"region: $x"))
    f.topics.foreach(t => if has(r.topics.map(_.value), t.value) then b += s"topic: ${t.value}")
    f.status.foreach(s => if ci(r.status.wire.value, s.value) then b += s"status: ${r.status.wire.value}")
    f.years.foreach(y => r.year.foreach(x => if x >= y.start && x <= y.end then b += s"year: ${x.value}"))
    f.period.foreach(p =>
      if overlaps(r.periodStart, r.periodEnd, p) then
        b += s"period: ${Markdown.periodLabel(r.periodStart, r.periodEnd)}"
    )
    b.result().map((text => Hit.Rationale(text)))

  /** Which of the filter's finding-level criteria this finding satisfies. Empty ⇒ it is NOT part of the evidence and is
    * dropped from `Hit.matched`.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def findingWhy(
      f: Filter,
      m: Finding.Match,
      placeIds: List[Place.Id] = Nil,
      scope: PlaceScope = PlaceScope.empty
  ): List[Hit.Rationale] =
    val b = List.newBuilder[String]
    f.period.foreach(p => if overlaps(m.periodStart, m.periodEnd, p) then b += "period")
    f.bbox.foreach(box =>
      (m.lon, m.lat) match
        case (Some(x), Some(y)) if box.contains(x, y) => b += "bbox"
        case _                                        => ()
    )
    f.places.foreach { p =>
      val byGazetteer = scope.idsFor(PlaceScope.IdsForValue(p.value)).exists(ids => placeIds.exists(ids.contains))
      // named by its LABEL, not its gazetteer id: "place: pleiades_589831" explains nothing (item 15)
      if byGazetteer || m.regionName.exists(_.value.toLowerCase.contains(p.value.toLowerCase)) then
        b += s"place: ${scope.labelFor(PlaceScope.LabelForValue(p.value))}"
    }
    f.tags.foreach(t => if m.tags.exists(x => ci(x.value, t.value)) then b += s"tag: ${t.value}")
    b.result().map((text => Hit.Rationale(text)))
