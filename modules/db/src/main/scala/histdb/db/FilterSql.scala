package histdb.db

import histdb.core.*

import com.augustnagro.magnum.{Frag, FragWriter}
import java.sql.PreparedStatement

/** `Filter` → SQL, as a **pure function** (plan §1.0/§2.2): every builder here returns a value that
  * can be asserted on in a unit test without a database. `SourceRepo` is the only thing that turns
  * one into a Magnum [[Frag]] and runs it.
  *
  * Conventions: the `sources` table is always aliased `s`; predicates that reach into `findings`
  * do so with a correlated `EXISTS`, because **the source is the unit of the result** — a source
  * matches `period`/`bbox`/`places` when its own value matches OR any of its findings do, and
  * `tags` matches `sources.tags ∪ findings.tags`. */
object FilterSql:

  /** A bound parameter. Typed (rather than `Any`) so tests can assert on it and the writer can bind
    * it without reflection — which also keeps the native image happy. */
  enum Param:
    case S(v: String)
    case L(v: Long)
    case I(v: Int)
    case D(v: Double)

  import Param.*

  /** A SQL fragment: text with `?` placeholders + the parameters that fill them, in order. */
  final case class Sql(text: String, params: List[Param]):
    def ++(other: Sql): Sql       = Sql(text + other.text, params ++ other.params)
    def wrap(pre: String, post: String): Sql = Sql(pre + text + post, params)

    /** Hand off to Magnum. `Frag.params` is only used for logging; `writer` does the binding. */
    def frag: Frag =
      val ps = params
      Frag(text, ps.map(paramValue), (stmt: PreparedStatement, pos: Int) => bind(stmt, pos, ps))

  object Sql:
    val empty: Sql                   = Sql("", Nil)
    def raw(text: String): Sql       = Sql(text, Nil)
    def of(text: String, ps: Param*) = Sql(text, ps.toList)

  private def paramValue(p: Param): Any = p match
    case S(v) => v
    case L(v) => v
    case I(v) => v
    case D(v) => v

  private def bind(stmt: PreparedStatement, pos: Int, ps: List[Param]): Int =
    var i = pos
    ps.foreach { p =>
      p match
        case S(v) => stmt.setString(i, v)
        case L(v) => stmt.setLong(i, v)
        case I(v) => stmt.setInt(i, v)
        case D(v) => stmt.setDouble(i, v)
      i += 1
    }
    i

  // ── LIKE handling ────────────────────────────────────────────────────────────────────────────
  /** `\` is the escape character in every `LIKE`/`ILIKE` below, so a user's `%`/`_` is literal. */
  private val Esc = " ESCAPE '\\'"
  def escapeLike(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
  def contains(s: String): String   = "%" + escapeLike(s) + "%"
  def startsWith(s: String): String = escapeLike(s) + "%"

  // ── combinators ──────────────────────────────────────────────────────────────────────────────
  private def join(sep: String, parts: List[Sql]): Sql =
    Sql(parts.map(_.text).mkString(sep), parts.flatMap(_.params))
  private def anyOf(parts: List[Sql]): Sql =
    if parts.sizeIs == 1 then parts.head else join(" OR ", parts).wrap("(", ")")

  // ── the WHERE predicate ──────────────────────────────────────────────────────────────────────

  /** The boolean expression selecting the sources this filter matches. Always a valid expression:
    * an empty filter yields `TRUE`. Predicates appear in `Filter` field order, which is what the
    * unit tests pin down. */
  def where(f: Filter): Sql =
    val ps = List.newBuilder[Sql]

    f.q.map(_.trim).filter(_.nonEmpty).foreach { q =>
      val p = S(contains(q))
      ps += Sql.of(
        s"(s.title ILIKE ?$Esc OR s.summary ILIKE ?$Esc OR s.ocr_markdown ILIKE ?$Esc)", p, p, p)
    }

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

    // places: any-of; place NAMES until the gazetteer arrives (wave 2) — sources.regions ∪
    // findings.region_name, matched with ILIKE so "Anatolia" finds "Central Anatolia".
    if f.places.nonEmpty then
      ps += anyOf(f.places.map(p =>
        Sql.of(
          s"(EXISTS (SELECT 1 FROM unnest(s.regions) x WHERE x ILIKE ?$Esc)" +
            s" OR EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey" +
            s" AND f.region_name ILIKE ?$Esc))",
          S(contains(p)), S(contains(p)))))

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
        "(s.period && int8range(?::bigint, ?::bigint, '[]')" +
          " OR EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey" +
          " AND f.period && int8range(?::bigint, ?::bigint, '[]')))",
        L(p.start), L(p.end), L(p.start), L(p.end)))

    f.bbox.foreach(b =>
      ps += (Sql.raw("EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey" +
        " AND f.geom IS NOT NULL AND ") ++ envelopes("f.geom", b) ++ Sql.raw(")")))

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

  /** Deterministic ordering (`, s.citekey` last) so paging is stable. `Relevance` stays
    * citation-weighted until stream 1.2 replaces it with `ts_rank_cd`. */
  def orderBy(sort: Sort): String = sort match
    case Sort.Relevance | Sort.Cited => "ORDER BY s.cited_by_count DESC NULLS LAST, s.citekey"
    case Sort.PeriodStart            => "ORDER BY lower(s.period) ASC NULLS LAST, s.citekey"
    case Sort.Year                   => "ORDER BY s.year DESC NULLS LAST, s.citekey"
    case Sort.Title                  => "ORDER BY lower(s.title) ASC, s.citekey"

  // ── the queries ──────────────────────────────────────────────────────────────────────────────

  /** Columns of [[SourceRow]] — `SearchHit` plus what `why` needs (tags, authors, body-hit). */
  private val rowCols =
    "s.citekey, s.title, s.year, s.status, array_to_string(s.topics, ', ')," +
      " array_to_string(s.regions, ', '), lower(s.period), upper(s.period) - 1, s.summary," +
      " array_to_string(s.tags, ', '), array_to_string(s.authors, ', ')"

  /** The page of matching sources. Note the SELECT-list parameter (the body-hit flag) comes BEFORE
    * the WHERE parameters — order is what JDBC binds by. */
  def sourcesQuery(f: Filter): Sql =
    val bodyHit = f.q.map(_.trim).filter(_.nonEmpty) match
      case Some(q) => Sql.of(s"(s.ocr_markdown ILIKE ?$Esc)", S(contains(q)))
      case None    => Sql.raw("FALSE")
    Sql.raw(s"SELECT $rowCols, ") ++ bodyHit ++
      Sql.raw(" AS body_hit FROM sources s WHERE ") ++ where(f) ++
      Sql.raw(s" ${orderBy(f.sort)} ") ++ Sql.of("LIMIT ? OFFSET ?", I(f.limit.max(0)), I(f.offset.max(0)))

  /** `total` = count(DISTINCT source) for the filter, ignoring limit/offset. */
  def countQuery(f: Filter): Sql =
    Sql.raw("SELECT count(*)::int FROM sources s WHERE ") ++ where(f)

  /** Every finding of the page's sources; which of them actually *matched* is decided purely, by
    * [[findingWhy]], so the explanation is unit-testable without a database. */
  def findingsQuery(citekeys: List[String]): Sql =
    Sql(
      "SELECT f.citekey, f.id, f.claim, f.region_name, ST_X(f.geom), ST_Y(f.geom)," +
        " lower(f.period), upper(f.period) - 1, f.period_name, array_to_string(f.tags, ', ')" +
        s" FROM findings f WHERE f.citekey IN (${List.fill(citekeys.size)("?").mkString(", ")})" +
        " ORDER BY f.citekey, f.id",
      citekeys.map(S(_)))

  /** The set of values a facet dimension draws from, as a LATERAL sub-select correlated on `s`. */
  def facetValues(field: FacetField): String = field match
    case FacetField.Tags =>
      "SELECT x FROM unnest(s.tags) x" +
        " UNION SELECT y FROM findings f, unnest(f.tags) y WHERE f.citekey = s.citekey"
    case FacetField.Authors => "SELECT x FROM unnest(s.authors) x"
    case FacetField.Places =>
      "SELECT x FROM unnest(s.regions) x" +
        " UNION SELECT f.region_name FROM findings f" +
        " WHERE f.citekey = s.citekey AND f.region_name IS NOT NULL"
    case FacetField.Topics => "SELECT x FROM unnest(s.topics) x"
    case FacetField.Status => "SELECT s.status"

  /** `(value, count(DISTINCT source))` for one dimension inside the current filter, most-used
    * first. `prefix` is a case-insensitive prefix match (for chip autocomplete). */
  def facetsQuery(field: FacetField, prefix: Option[String], f: Filter, limit: Int): Sql =
    val pre = prefix.map(_.trim).filter(_.nonEmpty) match
      case Some(p) => Sql.of(s" AND v ILIKE ?$Esc", S(startsWith(p)))
      case None    => Sql.empty
    Sql.raw(
      s"SELECT v AS value, count(DISTINCT s.citekey)::int AS count FROM sources s" +
        s" CROSS JOIN LATERAL (${facetValues(field)}) AS t(v) WHERE ") ++ where(f) ++
      Sql.raw(" AND v IS NOT NULL AND v <> ''") ++ pre ++
      Sql.raw(" GROUP BY v ORDER BY count(DISTINCT s.citekey) DESC, v ASC ") ++
      Sql.of("LIMIT ?", I(limit.max(1)))

  // ── pure match explanation ───────────────────────────────────────────────────────────────────

  /** One row of [[sourcesQuery]]: a `SearchHit` plus the extra columns `why` needs. */
  final case class SourceRow(
      citekey: String,
      title: String,
      year: Option[Int],
      status: String,
      topics: String,
      regions: String,
      periodStart: Option[Long],
      periodEnd: Option[Long],
      summary: Option[String],
      tags: String,
      authors: String,
      bodyHit: Boolean
  ):
    def toHit: SearchHit =
      SearchHit(citekey, title, year, status, topics, regions, periodStart, periodEnd, summary)

  private def csv(s: String): List[String] = s.split(",").map(_.trim).filter(_.nonEmpty).toList
  private def ci(a: String, b: String)     = a.equalsIgnoreCase(b)
  private def has(csvText: String, v: String)      = csv(csvText).exists(ci(_, v))
  private def like(csvText: String, v: String)     = csv(csvText).find(_.toLowerCase.contains(v.toLowerCase))

  /** Inclusive `[start, end]` overlap — the Scala twin of `int8range(a, b, '[]') &&`. */
  def overlaps(start: Option[Long], end: Option[Long], p: Period): Boolean =
    (start, end) match
      case (Some(a), Some(b)) => a <= p.end && b >= p.start
      case _                  => false

  /** Source-level reasons this source is in the result — "title matches 'collapse'", "tag:
    * sea-peoples", … Pure: everything comes from the row the SQL returned. */
  def sourceWhy(f: Filter, r: SourceRow): List[String] =
    val b = List.newBuilder[String]
    f.q.map(_.trim).filter(_.nonEmpty).foreach { q =>
      val ql = q.toLowerCase
      if r.title.toLowerCase.contains(ql) then b += s"title matches '$q'"
      if r.summary.exists(_.toLowerCase.contains(ql)) then b += s"summary matches '$q'"
      if r.bodyHit then b += s"full text matches '$q'"
    }
    f.tags.foreach(t => if has(r.tags, t) then b += s"tag: $t")
    f.authors.foreach(a => like(r.authors, a).foreach(x => b += s"author: $x"))
    f.places.foreach(p => like(r.regions, p).foreach(x => b += s"region: $x"))
    f.topics.foreach(t => if has(r.topics, t) then b += s"topic: $t")
    f.status.foreach(s => if ci(r.status, s) then b += s"status: ${r.status}")
    f.years.foreach(y => r.year.foreach(x => if x >= y.start && x <= y.end then b += s"year: $x"))
    f.period.foreach(p =>
      if overlaps(r.periodStart, r.periodEnd, p) then
        b += s"period: ${Markdown.periodLabel(r.periodStart, r.periodEnd)}")
    b.result()

  /** Which of the filter's finding-level criteria this finding satisfies. Empty ⇒ it is NOT part of
    * the evidence and is dropped from `Hit.matched`. */
  def findingWhy(f: Filter, m: FindingMatch): List[String] =
    val b = List.newBuilder[String]
    f.period.foreach(p => if overlaps(m.periodStart, m.periodEnd, p) then b += "period")
    f.bbox.foreach(box =>
      (m.lon, m.lat) match
        case (Some(x), Some(y)) if box.contains(x, y) => b += "bbox"
        case _                                        => ())
    f.places.foreach(p =>
      if m.regionName.exists(_.toLowerCase.contains(p.toLowerCase)) then b += s"place: $p")
    f.tags.foreach(t => if has(m.tags, t) then b += s"tag: $t")
    b.result()
