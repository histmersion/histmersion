package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.values.{MediaRole, SourceStatus}


/** `Filter` → SQL, as a **pure function** (plan §1.0/§2.2): every builder here returns a value that
  * can be asserted on in a unit test without a database. `SourceRepo` is the only thing that turns
  * one into a Magnum [[Frag]] and runs it.
  *
  * Conventions: the `sources` table is always aliased `s`; predicates that reach into `findings`
  * do so with a correlated `EXISTS`, because **the source is the unit of the result** — a source
  * matches `period`/`bbox`/`places` when its own value matches OR any of its findings do, and
  * `tags` matches `sources.tags ∪ findings.tags`. */
object FilterSql:

  import Param.*

  // ── LIKE handling ────────────────────────────────────────────────────────────────────────────
  /** `\` is the escape character in every `LIKE`/`ILIKE` below, so a user's `%`/`_` is literal.
    *
    * Inside a Magnum `sql"…"` the clause must be spelt `ESCAPE '\\'`: the interpolator processes
    * escapes like `s"…"`, so a bare `'\'` reached Postgres as `ESCAPE ''` (no escape character at
    * all), every `\_` in the pattern then needed a literal backslash, and a search for `ZZR_`
    * matched nothing (#769). */
  private val Esc = " ESCAPE '\\'"
  def escapeLike(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
  def contains(s: String): String   = "%" + escapeLike(s) + "%"
  def startsWith(s: String): String = escapeLike(s) + "%"

  // ── combinators ──────────────────────────────────────────────────────────────────────────────
  private def join(sep: String, parts: List[Sql]): Sql =
    Sql(parts.map(_.text).mkString(sep), parts.flatMap(_.params))
  private def anyOf(parts: List[Sql]): Sql =
    if parts.sizeIs == 1 then parts.head else join(" OR ", parts).wrap("(", ")")

  // ── places: gazetteer ids (plan §1.6a) with the wave-1 name match as a fallback ──────────────
  // A filter value the gazetteer recognised (see [[PlaceScope]], resolved once per query by the
  // handler) matches through the MATERIALISED `finding_places` — the place itself and, via the
  // hierarchy as of the filter's period, everything under it. Only a place ID resolves, and an id is
  // no name, so a resolved value is matched through `finding_places` alone; an unresolved value (a
  // name) is the original name ILIKE over `sources.regions ∪ findings.region_name`, as before.

  /** `finding_places` membership for a source: an UNCORRELATED semi-join with the ids bound as ONE
    * `text[]`. The correlated `EXISTS (… WHERE f.citekey = s.citekey AND fp.place_id IN (?, ?, …))`
    * it replaces re-ran per source — and, inside the Articles list's `al.target IN (…)`, per ARTICLE
    * LINK (1 955 times): with a polity's 451 descendants, each run was 1 284 index searches and the
    * hover card's article count took 15 s on a live-sized copy (2026-10-09). Uncorrelated, Postgres
    * computes the matching citekeys once (a hashed sub-plan) whatever OR or IN surrounds it, and one
    * array parameter keeps a generic plan of a prepared statement from going bad on 451 `?`s. */
  private def placeIdsSource(ids: List[String]): Sql =
    Sql.of(
      "s.citekey IN (SELECT f.citekey FROM findings f JOIN finding_places fp ON fp.finding_id = f.id" +
        " WHERE fp.place_id = ANY(?::text[]))",
      A(ids))

  /** `finding_places` membership for ONE finding row (alias `fa`), uncorrelated like [[placeIdsSource]]. */
  private def placeIdsFinding(ids: List[String], fa: String): Sql =
    Sql.of(s"$fa.id IN (SELECT fp.finding_id FROM finding_places fp WHERE fp.place_id = ANY(?::text[]))", A(ids))

  /** The wave-1 name match for a source: its own regions, any of its findings' `region_name`, OR
    * any place_name matching by ILIKE prefix (so "Anatolian" reaches "Anatolian plateau" etc.). */
  private def placeNameSource(p: String): Sql =
    Sql.of(
      s"(EXISTS (SELECT 1 FROM unnest(s.regions) x WHERE x ILIKE ?$Esc)" +
        s" OR EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey" +
        s" AND f.region_name ILIKE ?$Esc)" +
        s" OR EXISTS (SELECT 1 FROM findings f" +
        s" JOIN finding_places fp ON fp.finding_id = f.id" +
        s" JOIN place_names pn ON pn.place_id = fp.place_id" +
        s" WHERE f.citekey = s.citekey AND pn.name ILIKE ?$Esc))",
      S(contains(p)), S(contains(p)), S(contains(p)))

  /** A value the gazetteer resolved is matched through `finding_places` ALONE. It used to be OR'ed
    * with the name match on the value itself — the ID ("pleiades_570577") as an ILIKE over every
    * finding's place names — which can match nothing and, since GeoNames and Wikidata brought 8 M
    * names in (#1064), made a place's hover counts take 3–20 s (2026-10-09). */
  private def placeSource(p: String, scope: PlaceScope): Sql =
    scope.idsFor(p) match
      case Some(ids) => placeIdsSource(ids)
      case None      => placeNameSource(p)

  /** `finding_places` membership for ONE finding row (alias `fa`). */
  private def placeFinding(p: String, scope: PlaceScope, fa: String): Sql =
    val byName = Sql.of(s"$fa.region_name ILIKE ?$Esc", S(contains(p)))
    scope.idsFor(p) match
      case Some(ids) =>
        placeIdsFinding(ids, fa)
      case None => byName

  // ── the WHERE predicate ──────────────────────────────────────────────────────────────────────

  /** The boolean expression selecting the sources this filter matches. Always a valid expression:
    * an empty filter yields `TRUE`. Predicates appear in `Filter` field order, which is what the
    * unit tests pin down. */
  def where(f: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    val ps = List.newBuilder[Sql]

    // free text = Postgres FTS (plan §1.4): the weighted title/summary vector OR any body chunk.
    // `websearch` syntax ("phrase", -not, or) — see [[Search]].
    f.q.map(_.trim).filter(_.nonEmpty).foreach(q => ps += Search.ftsPredicate(q))

    // tags: AND semantics, over sources.tags ∪ findings.tags
    f.tags.foreach { t =>
      ps += Sql.of(
        "(EXISTS (SELECT 1 FROM unnest(s.tags) x WHERE lower(x) = lower(?))" +
          " OR EXISTS (SELECT 1 FROM findings f, unnest(f.tags) x" +
          " WHERE f.citekey = s.citekey AND lower(x) = lower(?)))",
        S(t), S(t))
    }

    // authors: any-of, case-insensitive substring
    if f.authors.nonEmpty then
      ps += anyOf(f.authors.map(a =>
        Sql.of(s"EXISTS (SELECT 1 FROM unnest(s.authors) x WHERE x ILIKE ?$Esc)", S(contains(a)))))

    // places: any-of — a gazetteer id through `finding_places`, otherwise the name ILIKE over
    // sources.regions ∪ findings.region_name (so "Anatolia" still finds "Central Anatolia").
    if f.places.nonEmpty then ps += anyOf(f.places.map(placeSource(_, scope)))

    // topics: AND semantics (chip narrowing, like tags); sources only — findings carry no topics
    f.topics.foreach { t =>
      ps += Sql.of("EXISTS (SELECT 1 FROM unnest(s.topics) x WHERE lower(x) = lower(?))", S(t))
    }

    if f.status.nonEmpty then
      ps += Sql(
        s"lower(s.status) IN (${List.fill(f.status.size)("?").mkString(", ")})",
        f.status.map(x => S(x.trim.toLowerCase)))

    f.years.foreach(y =>
      ps += Sql.of("(s.year IS NOT NULL AND s.year BETWEEN ? AND ?)", L(y.start), L(y.end)))

    f.period.foreach(p =>
      ps += Sql.of(
        s"(${PeriodSql.overlaps("s.period", "?::bigint", "?::bigint")}" +
          " OR EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey" +
          s" AND ${PeriodSql.overlaps("f.period", "?::bigint", "?::bigint")}))",
        L(p.start), L(p.end), L(p.start), L(p.end)))

    f.bbox.foreach(b =>
      ps += (Sql.raw("EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey" +
        " AND f.geom IS NOT NULL AND ") ++ envelopes("f.geom", b) ++ Sql.raw(")")))

    // the global title quick-filter: a case-insensitive substring of the source's own title
    titleLike(f).foreach(t => ps += Sql.of(s"s.title ILIKE ?$Esc", S(contains(t))))

    // a research goal's sources: those it links
    f.goal.map(_.trim).filter(_.nonEmpty).foreach(g =>
      ps += Sql.of("s.citekey IN (SELECT ref FROM goal_links WHERE goal_slug = ? AND kind = 'source')", S(g)))

    val parts = ps.result()
    if parts.isEmpty then Sql.raw("TRUE") else join(" AND ", parts)

  /** The filter's title quick-filter, trimmed; `None` when there is nothing to match. */
  def titleLike(f: Filter): Option[String] = f.title.map(_.trim).filter(_.nonEmpty)

  // ── what the filter selects, seen from the other lists (articles, events) ───────────────────
  // ONE rule, reused: the Articles and Events lists are the items RELATED to the sources the
  // Sources list shows — the very same `where` as a sub-select, never a second copy of the filter
  // semantics. `title` is not part of the source set: on those lists it names the item's own title.

  /** The citekeys of the sources `f` selects — `/api/sources`' predicate as a sub-select. */
  def sourceKeys(f: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    Sql.raw("SELECT s.citekey FROM sources s WHERE ") ++ where(f, scope)

  /** [[sourceKeys]] as an ARRAY computed ONCE per statement (an init-plan): `x = ANY(…)` against it is
    * `x IN (sourceKeys)`. Written as `IN (sub-select)` the planner pulls the sources back into a
    * nested loop and evaluates the filter's correlated sub-plans once per ARTICLE LINK — 164 to 1 955
    * times instead of once per source; a free-text place chip's article list took 240 ms, a
    * polity's 15 s (2026-10-09). The set is at most the corpus' citekeys, small by construction. */
  private def sourceKeySet(f: Filter, scope: PlaceScope): Sql =
    sourceKeys(f, scope).wrap("ARRAY(", ")")

  /** The articles for a filter (alias `a`): those CITING at least one source of `f.sourceSet`
    * (`article_links`, maintained at write time from the body's `[[citekey]]`s), and whose title
    * contains `f.title`. A filter without source criteria restricts nothing. */
  def articleWhere(f: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    val set = f.sourceSet
    val ps = List.newBuilder[Sql]
    if !set.isEmpty then
      ps += (Sql.raw("EXISTS (SELECT 1 FROM article_links al WHERE al.src_slug = a.slug" +
        " AND al.target_kind = 'source' AND al.target = ANY(") ++ sourceKeySet(set, scope) ++ Sql.raw("))"))
    titleLike(f).foreach(t => ps += Sql.of(s"a.title ILIKE ?$Esc", S(contains(t))))
    val parts = ps.result()
    if parts.isEmpty then Sql.raw("TRUE") else join(" AND ", parts)

  /** The events for a filter (alias `e`): whose source (`e.citekey`) is among the sources of
    * `f.sourceSet` WITHOUT its period, whose OWN span overlaps the period, and whose label contains
    * `f.title`. The period applies to the event and not to its source because an event carries its
    * own dating — the period a source covers is not the event's. No source criteria besides the
    * period ⇒ no condition on the source (an event without one stays listed). */
  def eventWhere(f: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    val set = f.sourceSet.copy(period = None)
    val ps = List.newBuilder[Sql]
    if !set.isEmpty then
      // its own source, or a source that attests it (#1449)
      val keys = sourceKeySet(set, scope)
      ps += (Sql.raw("(e.citekey = ANY(") ++ keys ++ Sql.raw(") OR EXISTS (SELECT 1 FROM attestations ea " +
        "WHERE ea.target_type = 'event' AND ea.target_id = e.id AND ea.citekey = ANY(") ++ keys ++ Sql.raw(")))"))
    f.period.foreach(p =>
      ps += PeriodSql.overlaps("e.outer_span", p))
    titleLike(f).foreach(t => ps += Sql.of(s"e.label ILIKE ?$Esc", S(contains(t))))
    val parts = ps.result()
    if parts.isEmpty then Sql.raw("TRUE") else join(" AND ", parts)

  /** The PostGIS predicate for a viewport. A box with `minLon > maxLon` crosses the **antimeridian**
    * and becomes two envelopes OR'ed together (`minLon…180` and `-180…maxLon`). */
  def envelopes(geom: String, b: BBox): Sql =
    def env(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
      Sql.of(s"$geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)", D(minLon), D(minLat), D(maxLon), D(maxLat))
    if b.crossesAntimeridian then
      anyOf(List(env(b.minLon, b.minLat, 180.0, b.maxLat), env(-180.0, b.minLat, b.maxLon, b.maxLat)))
    else env(b.minLon, b.minLat, b.maxLon, b.maxLat)

  // ── the FINDING-level predicate (map feed §1.1, histogram §1.7) ─────────────────────────────
  // `/api/sources` asks "does this SOURCE match?" — an EXISTS quantified over its findings (above).
  // `/api/findings` and `/api/aggregate` ask "does this FINDING match?": the same criteria applied to
  // one finding row. Split into period / non-period because the histogram's LATERAL needs the period
  // criterion on its own (`count(*) FILTER (WHERE …)`).

  /** The filter with every finding-level criterion removed — what is left is what a SOURCE must
    * satisfy on its own: `q`, authors, topics, status, publication years. */
  def sourceOnly(f: Filter): Filter = f.copy(tags = Nil, places = Nil, period = None, bbox = None)

  /** Source-level predicate for a query whose row is a FINDING (its source joined in as `s`). */
  def sourceWhere(f: Filter): Sql = where(sourceOnly(f))

  /** True when a finding must satisfy something besides the period — so a source with no findings
    * at all cannot match (the place filters have always been finding-level). */
  def hasFindingPlaceCriteria(f: Filter): Boolean =
    f.tags.nonEmpty || f.places.nonEmpty || f.bbox.isDefined

  /** The non-temporal criteria for ONE finding: tags (over `findings.tags ∪ sources.tags`, AND
    * semantics), places (any-of, ILIKE on `region_name`) and the viewport (antimeridian-split).
    * `TRUE` when the filter sets none of them. */
  def findingPlaceWhere(f: Filter, fa: String = "f", sa: String = "s",
      scope: PlaceScope = PlaceScope.empty): Sql =
    val ps = List.newBuilder[Sql]

    f.tags.foreach { t =>
      ps += Sql.of(
        s"(EXISTS (SELECT 1 FROM unnest($fa.tags) x WHERE lower(x) = lower(?))" +
          s" OR EXISTS (SELECT 1 FROM unnest($sa.tags) x WHERE lower(x) = lower(?)))",
        S(t), S(t))
    }

    if f.places.nonEmpty then ps += anyOf(f.places.map(placeFinding(_, scope, fa)))

    f.bbox.foreach(b =>
      ps += (Sql.raw(s"($fa.geom IS NOT NULL AND ") ++ envelopes(s"$fa.geom", b) ++ Sql.raw(")")))

    val parts = ps.result()
    if parts.isEmpty then Sql.raw("TRUE") else join(" AND ", parts)

  /** Deep-time overlap for ONE finding; `TRUE` when the filter carries no period. */
  def findingPeriodWhere(f: Filter, fa: String = "f"): Sql =
    f.period.fold(Sql.raw("TRUE"))(p =>
      PeriodSql.overlaps(s"$fa.period", p))

  /** Everything ONE finding must satisfy: the period AND the rest. `TRUE` for an empty filter. */
  def findingWhere(f: Filter, fa: String = "f", sa: String = "s",
      scope: PlaceScope = PlaceScope.empty): Sql =
    val parts = List(findingPeriodWhere(f, fa), findingPlaceWhere(f, fa, sa, scope)).filter(_.text != "TRUE")
    if parts.isEmpty then Sql.raw("TRUE") else join(" AND ", parts)

  /** Deterministic ordering (`, s.citekey` last) so paging is stable. `Relevance` WITHOUT a text
    * query has nothing to rank by, so it falls back to citations; with one it is `ts_rank_cd` — see
    * [[orderBySql]], which is what the queries actually use. */
  def orderBy(sort: Sort): String = sort match
    case Sort.Relevance | Sort.Cited => "ORDER BY s.cited_by_count DESC NULLS LAST, s.citekey"
    case Sort.PeriodStart            => s"ORDER BY ${PeriodSql.start("s.period")} ASC NULLS LAST, s.citekey"
    case Sort.Year                   => "ORDER BY s.year DESC NULLS LAST, s.citekey"
    case Sort.Oldest                 => "ORDER BY s.year ASC NULLS LAST, s.citekey"
    case Sort.Title                  => "ORDER BY lower(s.title) ASC, s.citekey"

  /** The ORDER BY as a fragment: `Sort.Relevance` + a text query ranks by FTS relevance
    * (`ts_rank_cd` over title/summary plus the best body chunk), citations breaking ties; everything
    * else is the parameterless ordering above.
    *
    * When `useCte` is true, ranking uses the precomputed `best_chunks` CTE (one scan of
    * `source_chunks`) instead of a per-source correlated subquery. The CTE path is the default for
    * [[sourcesQuery]]; the legacy correlated-subquery path is kept for callers that do not prepend
    * the CTE. */
  def orderBySql(f: Filter, useCte: Boolean = false): Sql = (f.sort, text(f)) match
    case (Sort.Relevance, Some(q)) =>
      val rank = if useCte then Search.rankExprCte(q) else Search.rankExpr(q)
      Sql.raw("ORDER BY ") ++ rank ++
        Sql.raw(" DESC, s.cited_by_count DESC NULLS LAST, s.citekey")
    case _ => Sql.raw(orderBy(f.sort))

  /** The filter's text query, trimmed; `None` when there is nothing to search for. */
  def text(f: Filter): Option[String] = f.q.map(_.trim).filter(_.nonEmpty)

  // ── the queries ──────────────────────────────────────────────────────────────────────────────

  /** Columns of [[SourceRow]] — `SearchHit` plus what `why` needs (tags, authors, body-hit). */
  private val rowCols =
    s"s.citekey, s.title, s.year, s.status, s.topics, s.regions, ${PeriodSql.cols("s.period")}," +
      " s.summary, s.tags, s.authors, s.doi"

  /** The page of matching sources (plan §1.4). For a text query, uses a two-phase approach:
    *
    *  1. A CTE (`best_chunks`) scans `source_chunks` ONCE to find each source's best-scoring chunk
    *     rank — replacing the N per-source correlated subqueries of the old LATERAL approach.
    *  2. The main SELECT joins the CTE for ranking and `body_hit`, but does NOT compute snippets
    *     (`ts_headline` is the expensive part). Snippets are deferred to [[snippetsQuery]], which
    *     the caller runs separately for just the page's citekeys.
    *
    * `SourceRow.snippet` and `matchedHeading` are always NULL from this query; the caller merges
    * them in from [[snippetsQuery]]. `body_hit` is true when the CTE found a matching chunk.
    *
    * Parameter order is textual: CTE, then SELECT, then WHERE, then ORDER BY, then limit/offset. */
  def sourcesQuery(f: Filter, snippetWords: Int, scope: PlaceScope = PlaceScope.empty): Sql =
    val (cte, cols, join) = text(f) match
      case Some(q) =>
        (Search.bestChunkCte(q),
          Sql.raw("(bc.citekey IS NOT NULL) AS body_hit, NULL::text AS snippet, NULL::text AS heading"),
          Sql.raw(" LEFT JOIN best_chunks bc ON bc.citekey = s.citekey"))
      case None =>
        (Sql.empty,
          Sql.raw("FALSE AS body_hit, NULL::text, NULL::text"),
          Sql.empty)
    cte ++ Sql.raw(s"SELECT $rowCols, ") ++ cols ++ Sql.raw(" FROM sources s") ++ join ++
      Sql.raw(" WHERE ") ++ where(f, scope) ++ Sql.raw(" ") ++ orderBySql(f, useCte = text(f).isDefined) ++
      Sql.raw(" ") ++ Sql.of("LIMIT ? OFFSET ?", I(f.limit.max(0)), I(f.offset.max(0)))

  /** `total` = count(DISTINCT source) for the filter, ignoring limit/offset. */
  def countQuery(f: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    Sql.raw("SELECT count(*)::int FROM sources s WHERE ") ++ where(f, scope)

  /** Snippets for a specific set of citekeys — the second phase of the two-phase FTS approach.
    * Returns `(citekey, heading, snippet)` rows, one per citekey, with `ts_headline` wrapping hits
    * in `**`. An empty `citekeys` list is a no-op (the caller should skip the query). */
  def snippetsQuery(q: String, snippetWords: Int, citekeys: List[String]): Sql =
    Search.snippetsQuery(q, snippetWords, citekeys)

  /** One snippet row: (citekey, heading, snippet). */
  final case class SnippetRow(citekey: String, heading: Option[String], snippet: String)

  /** Every finding of the page's sources; which of them actually *matched* is decided purely, by
    * [[findingWhy]], so the explanation is unit-testable without a database. */
  def findingsQuery(citekeys: List[String]): Sql =
    Sql(
      "SELECT f.citekey, f.id, f.claim, f.region_name, ST_X(f.geom), ST_Y(f.geom)," +
        s" ${PeriodSql.cols("f.period")}, f.period_name, f.tags," +
        // the gazetteer places this finding was materialised into (plan §1.6a) — labels for the
        // reader, ids so `findingWhy` can say WHICH place chip the finding satisfied. Arrays, not
        // joined strings: a place label routinely contains a comma.
        " ARRAY(SELECT p.label FROM finding_places fp JOIN places p ON p.id = fp.place_id" +
        " WHERE fp.finding_id = f.id ORDER BY p.label)," +
        " ARRAY(SELECT fp.place_id FROM finding_places fp WHERE fp.finding_id = f.id ORDER BY fp.place_id)" +
        s" FROM findings f WHERE f.citekey IN (${List.fill(citekeys.size)("?").mkString(", ")})" +
        " ORDER BY f.citekey, f.id",
      citekeys.map(S(_)))

  /** Columns of [[com.histmersion.api.GeoHit]] — one map-feed row (a finding plus its source's title). */
  private val geoCols =
    "f.citekey, s.title, f.claim, f.region_name, ST_X(f.geom), ST_Y(f.geom)," +
      s" ${PeriodSql.cols("f.period")}, f.confidence, f.significance, s.doi," +
      // what the source is (core.SourceKinds), for the marker's icon: its venue, provenance, words, cover
      " s.venue, s.source, s.tags, s.topics," +
      " EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'source' AND m.target_id = s.citekey" +
      s" AND m.role = '${MediaRole.Cover}')"

  /** `GET /api/findings` — the MAP FEED: every finding that ITSELF satisfies the finding-level
    * criteria ([[findingWhere]]), belonging to a source that satisfies the source-level ones
    * ([[sourceWhere]]). Ordered oldest-first, with a deterministic tiebreak so paging is stable. */
  def findingsFeedQuery(f: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    Sql.raw(s"SELECT $geoCols FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE ") ++
      sourceWhere(f) ++ Sql.raw(" AND ") ++ findingWhere(f, scope = scope) ++
      Sql.raw(s" ORDER BY ${PeriodSql.start("f.period")} NULLS LAST, f.citekey, f.id ") ++
      Sql.of("LIMIT ? OFFSET ?", I(f.limit.max(0)), I(f.offset.max(0)))

  /** How many findings [[findingsFeedQuery]] would return without its `LIMIT` — the same WHERE, so a
    * count and the map feed can never disagree about which findings a filter selects. */
  def findingsCountQuery(f: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    Sql.raw("SELECT count(*)::int FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE ") ++
      sourceWhere(f) ++ Sql.raw(" AND ") ++ findingWhere(f, scope = scope)

  /** What a filter selects in EACH list — sources, findings, articles, events — as ONE statement of
    * four scalar counts: the same predicates as [[countQuery]], [[findingsCountQuery]],
    * [[articleWhere]] and [[eventWhere]], in one round trip on one connection. A map place's hover
    * card asks for exactly this; as four repository calls it was four checkouts and four statements,
    * one of them a page of articles fetched only for its total. */
  def selectionCountsQuery(f: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    Sql.raw("SELECT (") ++ countQuery(f, scope) ++ Sql.raw("), (") ++ findingsCountQuery(f, scope) ++
      Sql.raw("), (SELECT count(*)::int FROM articles a WHERE ") ++ articleWhere(f, scope) ++
      Sql.raw("), (SELECT count(*)::int FROM events e WHERE ") ++ eventWhere(f, scope) ++ Sql.raw(")")

  /** The set of values a facet dimension draws from, as a LATERAL sub-select correlated on `s`. */
  def facetValues(field: FacetField): String = field match
    case FacetField.Tags =>
      "SELECT x FROM unnest(s.tags) x" +
        " UNION SELECT y FROM findings f, unnest(f.tags) y WHERE f.citekey = s.citekey"
    case FacetField.Authors => "SELECT x FROM unnest(s.authors) x"
    case FacetField.Places =>
      // author labels (wave 1) UNION the gazetteer labels the findings were materialised into (§1.6a)
      "SELECT x FROM unnest(s.regions) x" +
        " UNION SELECT f.region_name FROM findings f" +
        " WHERE f.citekey = s.citekey AND f.region_name IS NOT NULL" +
        " UNION SELECT p.label FROM findings f JOIN finding_places fp ON fp.finding_id = f.id" +
        " JOIN places p ON p.id = fp.place_id WHERE f.citekey = s.citekey"
    case FacetField.Topics => "SELECT x FROM unnest(s.topics) x"
    case FacetField.Status => "SELECT s.status"
    case FacetField.Provenance => "SELECT coalesce(s.source, 'unknown')"

  /** `(value, count(DISTINCT source))` for one dimension inside the current filter, most-used
    * first. `prefix` is a case-insensitive prefix match (for chip autocomplete).
    *
    * Values are grouped CASE-INSENSITIVELY (`GROUP BY lower(v)`) — `Anatolia` and `anatolia` are one
    * bucket — and the label shown is the most frequent original spelling (`mode()`), so a chip
    * inserts a value that exists in the data. Matching (`Filter.tags/topics/status` on `lower()`,
    * `places` on `ILIKE`) is case-insensitive too, so either spelling finds the whole bucket. */
  def facetsQuery(field: FacetField, prefix: Option[String], f: Filter, limit: Int,
      scope: PlaceScope = PlaceScope.empty): Sql =
    val pre = prefix.map(_.trim).filter(_.nonEmpty) match
      // matched as DISPLAYED: the UI shows `_` as a space (Fmt.display), so "bronze age" and
      // "bronze_age" both find `bronze_age_collapse`
      case Some(p) => Sql.of(s" AND replace(v, '_', ' ') ILIKE ?$Esc", S(startsWith(p.replace('_', ' '))))
      case None    => Sql.empty
    // `places` additionally reports the gazetteer id behind a label (`lower(v)` IS the group key, so
    // the scalar sub-select is legal) — that is what lets a chip filter by id instead of by name.
    // (`min(lower(v))` rather than `lower(v)`: every row of the group has the same value, and an
    // aggregate is what makes the correlated sub-select legal under GROUP BY.) The two columns share
    // ONE definition and ONE ordering, so they always describe the same place.
    def gazetteer(col: String) =
      s"(SELECT $col FROM place_names pn JOIN places p ON p.id = pn.place_id" +
        " WHERE lower(pn.name) = min(lower(v))" +
        " ORDER BY (lower(p.label) = min(lower(v))) DESC, p.id LIMIT 1)"
    val idCol =
      if field == FacetField.Places then
        s", ${gazetteer("pn.place_id")} AS place_id" +
          s", coalesce(${gazetteer("(p.properties->>'contested')::boolean")}, false) AS contested"
      else ", NULL::text AS place_id, FALSE AS contested"
    Sql.raw(
      s"SELECT mode() WITHIN GROUP (ORDER BY v) AS value, count(DISTINCT s.citekey)::int AS count$idCol" +
        s" FROM sources s CROSS JOIN LATERAL (${facetValues(field)}) AS t(v) WHERE ") ++ where(f, scope) ++
      Sql.raw(" AND v IS NOT NULL AND v <> ''") ++ pre ++
      Sql.raw(" GROUP BY lower(v) ORDER BY count(DISTINCT s.citekey) DESC, lower(v) ASC ") ++
      Sql.of("LIMIT ?", I(limit.max(1)))

  // ── pure match explanation ───────────────────────────────────────────────────────────────────

  /** One row of [[sourcesQuery]]: a `SearchHit` plus the extra columns `why` needs. */
  final case class SourceRow(
      citekey: String,
      title: String,
      year: Option[Int],
      status: SourceStatus,
      topics: List[String],
      regions: List[String],
      periodStart: Option[Long],
      periodEnd: Option[Long],
      summary: Option[String],
      tags: List[String],
      authors: List[String],
      doi: Option[String] = None,
      bodyHit: Boolean,
      snippet: Option[String] = None,
      matchedHeading: Option[String] = None
  ):
    /** The WIRE shape: arrays as proper lists. */
    def toHit: SearchHit =
      SearchHit(citekey, title, year, status, topics, regions, periodStart, periodEnd,
        summary, snippet, matchedHeading, doi, authors)

  private def ci(a: String, b: String)                = a.equalsIgnoreCase(b)
  private def has(values: List[String], v: String)    = values.exists(ci(_, v))
  private def like(values: List[String], v: String)   = values.find(_.toLowerCase.contains(v.toLowerCase))

  /** The words a `websearch_to_tsquery` string actually asks for: quotes stripped, `-excluded` terms
    * and the `or`/`and` connectives dropped. The SQL side is a tsquery, so the explanation cannot
    * just look for the raw string — `"sea peoples"` never occurs verbatim in a title. */
  def queryTerms(q: String): List[String] =
    q.split("\\s+").toList
      .map(_.replace("\"", "").trim.toLowerCase)
      .filterNot(t => t.isEmpty || t.startsWith("-") || t == "or" || t == "and")

  /** Inclusive `[start, end]` overlap — the Scala twin of `int8range(a, b, '[]') &&`. */
  def overlaps(start: Option[Long], end: Option[Long], p: Period): Boolean =
    (start, end) match
      case (Some(a), Some(b)) => a <= p.end && b >= p.start
      case _                  => false

  /** Source-level reasons this source is in the result — "title matches 'collapse'", "tag:
    * sea-peoples", … Pure: everything comes from the row the SQL returned. */
  def sourceWhy(f: Filter, r: SourceRow): List[String] =
    val b = List.newBuilder[String]
    text(f).foreach { q =>
      val terms = queryTerms(q)
      def hits(s: String) = terms.nonEmpty && terms.forall(s.toLowerCase.contains)
      if hits(r.title) then b += s"title matches '$q'"
      if r.summary.exists(hits) then b += s"summary matches '$q'"
      if r.bodyHit then b += s"full text matches '$q'"
    }
    f.tags.foreach(t => if has(r.tags, t) then b += s"tag: $t")
    f.authors.foreach(a => like(r.authors, a).foreach(x => b += s"author: $x"))
    f.places.foreach(p => like(r.regions, p).foreach(x => b += s"region: $x"))
    f.topics.foreach(t => if has(r.topics, t) then b += s"topic: $t")
    f.status.foreach(s => if ci(r.status.wire, s) then b += s"status: ${r.status.wire}")
    f.years.foreach(y => r.year.foreach(x => if x >= y.start && x <= y.end then b += s"year: $x"))
    f.period.foreach(p =>
      if overlaps(r.periodStart, r.periodEnd, p) then
        b += s"period: ${Markdown.periodLabel(r.periodStart, r.periodEnd)}")
    b.result()

  /** Which of the filter's finding-level criteria this finding satisfies. Empty ⇒ it is NOT part of
    * the evidence and is dropped from `Hit.matched`. */
  def findingWhy(f: Filter, m: FindingMatch, placeIds: List[String] = Nil,
      scope: PlaceScope = PlaceScope.empty): List[String] =
    val b = List.newBuilder[String]
    f.period.foreach(p => if overlaps(m.periodStart, m.periodEnd, p) then b += "period")
    f.bbox.foreach(box =>
      (m.lon, m.lat) match
        case (Some(x), Some(y)) if box.contains(x, y) => b += "bbox"
        case _                                        => ())
    f.places.foreach { p =>
      val byGazetteer = scope.idsFor(p).exists(ids => placeIds.exists(ids.contains))
      // named by its LABEL, not its gazetteer id: "place: pleiades_589831" explains nothing (item 15)
      if byGazetteer || m.regionName.exists(_.toLowerCase.contains(p.toLowerCase)) then
        b += s"place: ${scope.labelFor(p)}"
    }
    f.tags.foreach(t => if m.tags.exists(ci(_, t)) then b += s"tag: $t")
    b.result()
