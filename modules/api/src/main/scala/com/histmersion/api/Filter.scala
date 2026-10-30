package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema

/** The ONE query model (plan §1.0), shared by REST, MCP and the CLI so the three surfaces agree by construction.
  * `Filter` is a value: it carries no defaults that belong in config — `limit <= 0` means "use the server's configured
  * page size" (`histmersion.limits.search`), never a literal here.
  *
  * **The source is the unit of every result.** A source matches `period` / `bbox` / `places` when its OWN
  * period/regions match **or any of its findings** do; `tags` matches `sources.tags ∪ findings.tags`. Every count is
  * `count(distinct source)`.
  *
  * `places` values are resolved against the gazetteer (plan §1.6a) once per query, by the handler: a value that IS a
  * place id selects through the materialised `finding_places` plus every descendant valid in `period`; anything else
  * keeps the original name ILIKE. The two are OR'ed, so a seeded gazetteer can only ever add hits.
  */
final case class Filter(
    q: Option[String] = None, // free text (title/summary/body); FTS in stream 1.2
    tags: List[String] = Nil, // AND semantics — every tag must be present
    authors: List[String] = Nil, // any-of, case-insensitive substring
    places: List[String] = Nil, // any-of; a GAZETTEER ID (§1.6a) matches through finding_places
    // and the hierarchy, anything else is a name match
    topics: List[String] = Nil, // AND semantics (chip narrowing, like tags)
    status: List[String] = Nil, // any-of, case-insensitive exact
    years: Option[Period] = None, // publication-year range (inclusive)
    period: Option[Period] = None, // deep-time overlap, signed years (BCE negative)
    bbox: Option[BBox] = None, // viewport; matches a source through its findings' points
    sort: Sort = Sort.Relevance,
    limit: Int = 0, // <= 0 → server default (config `limits.search`)
    offset: Int = 0,
    title: Option[String] = None, // case-insensitive substring of the LISTED item's own title:
    // a source's title, an article's title, an event's label
    goal: Option[String] = None // the sources a research goal links (its slug)
) derives KindlingsCodecAsObject,
      KindlingsSchema:

  /** True when nothing at all is being filtered (only paging/sorting). */
  def isEmpty: Boolean =
    q.forall(_.trim.isEmpty) && tags.isEmpty && authors.isEmpty && places.isEmpty && topics.isEmpty &&
      status.isEmpty && years.isEmpty && period.isEmpty && bbox.isEmpty && title.forall(_.trim.isEmpty) &&
      goal.forall(_.trim.isEmpty)

  /** The filter as it selects SOURCES for a list of something else (articles, events): `title` is dropped, because
    * there it names the listed item's own title, not the source's; sort and paging are dropped because they belong to
    * the list, not to the set.
    */
  def sourceSet: Filter = Filter(q, tags, authors, places, topics, status, years, period, bbox, goal = goal)

  /** The criteria a *finding* can satisfy on its source's behalf. */
  def hasFindingCriteria: Boolean = period.isDefined || bbox.isDefined || places.nonEmpty || tags.nonEmpty

object Filter:
  given Schema[Filter] = summon[KindlingsSchema[Filter]].schema

  /** Open-ended bounds for half-specified ranges. Deep time never comes near these; they exist so a `Period` stays a
    * closed interval (and so `int8range(..,'[]')` can't overflow bigint).
    */
  val Earliest: Long = -1000000000L
  val Latest: Long = 1000000000L

  /** Clamp a bound into `[Earliest, Latest]`. Deep time never comes near either, so this only ever bites a nonsense
    * value — and a nonsense value is exactly what used to build an INVERTED range: `from = 2000000000` gave
    * `Period(2000000000, Latest)` with `start > end`, which Postgres rejects outright (`range lower bound must be less
    * than or equal to range upper bound`) and the request answered 500.
    */
  def clamp(v: Long): Long = math.max(Earliest, math.min(Latest, v))

  /** A closed period from optional bounds; `None` when neither bound is given. Both bounds are clamped, so the result
    * is always a valid `[start, end]`.
    */
  def periodOf(from: Option[Long], to: Option[Long]): Option[Period] =
    (from.map(clamp), to.map(clamp)) match
      case (None, None)       => None
      case (Some(a), Some(b)) => Some(if a <= b then Period(a, b) else Period(b, a))
      case (Some(a), None)    => Some(Period(a, Latest))
      case (None, Some(b))    => Some(Period(Earliest, b))

  /** The LOWER bound the caller actually gave, as opposed to the open-ended sentinel `periodOf` fills in for a
    * half-specified range. One definition — `FilterQuery.from` and the aggregation window both used to spell the
    * `!= Earliest` comparison out, which is the kind of sentinel arithmetic that goes wrong quietly.
    */
  def lowerGiven(p: Option[Period]): Option[Long] = p.map(_.start).filter(_ != Earliest)

  /** The UPPER bound the caller actually gave; `None` for a half-open range. */
  def upperGiven(p: Option[Period]): Option[Long] = p.map(_.end).filter(_ != Latest)

  def bboxOf(
      minLon: Option[Double],
      minLat: Option[Double],
      maxLon: Option[Double],
      maxLat: Option[Double]
  ): Option[BBox] =
    (minLon, minLat, maxLon, maxLat) match
      case (Some(a), Some(b), Some(c), Some(d)) => Some(BBox(a, b, c, d))
      case _                                    => None

  /** Build a `Filter` from the FLAT wire shape (REST query params, MCP tool input, CLI flags) — the single place that
    * shape is interpreted, so all three surfaces cannot drift.
    */
  def of(
      q: Option[String] = None,
      tags: List[String] = Nil,
      authors: List[String] = Nil,
      places: List[String] = Nil,
      topics: List[String] = Nil,
      status: List[String] = Nil,
      yearFrom: Option[Long] = None,
      yearTo: Option[Long] = None,
      from: Option[Long] = None,
      to: Option[Long] = None,
      minLon: Option[Double] = None,
      minLat: Option[Double] = None,
      maxLon: Option[Double] = None,
      maxLat: Option[Double] = None,
      sort: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None,
      title: Option[String] = None,
      goal: Option[String] = None
  ): Filter =
    def clean(xs: List[String]) = xs.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty)
    Filter(
      q = q.map(_.trim).filter(_.nonEmpty),
      tags = clean(tags),
      authors = clean(authors),
      places = clean(places),
      topics = clean(topics),
      status = clean(status),
      years = periodOf(yearFrom, yearTo),
      period = periodOf(from, to),
      bbox = bboxOf(minLon, minLat, maxLon, maxLat),
      sort = sort.flatMap(Sort.parse).getOrElse(Sort.Relevance),
      limit = limit.getOrElse(0),
      offset = offset.getOrElse(0).max(0),
      title = title.map(_.trim).filter(_.nonEmpty),
      goal = goal.map(_.trim).filter(_.nonEmpty)
    )
