package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.BBox
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given

final case class Filter(
    q: Option[String] = None,
    tags: List[String] = Nil,
    authors: List[String] = Nil,
    places: List[String] = Nil,
    topics: List[String] = Nil,
    status: List[String] = Nil,
    years: Option[Period] = None,
    period: Option[Period] = None,
    bbox: Option[BBox] = None,
    sort: Sort = Sort.Relevance,
    limit: Int = 0,
    offset: Int = 0,
    title: Option[String] = None,
    goal: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:

  def isEmpty: Boolean =
    q.forall(_.trim.isEmpty) && tags.isEmpty && authors.isEmpty && places.isEmpty && topics.isEmpty &&
      status.isEmpty && years.isEmpty && period.isEmpty && bbox.isEmpty && title.forall(_.trim.isEmpty) &&
      goal.forall(_.trim.isEmpty)

  def sourceSet: Filter = Filter(q, tags, authors, places, topics, status, years, period, bbox, goal = goal)

  def hasFindingCriteria: Boolean = period.isDefined || bbox.isDefined || places.nonEmpty || tags.nonEmpty

object Filter:
  given Schema[Filter] = summon[KindlingsSchema[Filter]].schema

  /** Open-ended bounds, far enough inside bigint that `int8range(..,'[]')` cannot overflow. */
  val Earliest: Long = -1000000000L
  val Latest: Long = 1000000000L

  /** Postgres rejects an inverted range (`range lower bound must be less than or equal to range upper bound`), so a
    * nonsense bound is clamped before it can build one.
    */
  def clamp(v: Long): Long = math.max(Earliest, math.min(Latest, v))

  def periodOf(from: Option[Long], to: Option[Long]): Option[Period] =
    (from.map(clamp), to.map(clamp)) match
      case (None, None)       => None
      case (Some(a), Some(b)) => Some(if a <= b then Period(a, b) else Period(b, a))
      case (Some(a), None)    => Some(Period(a, Latest))
      case (None, Some(b))    => Some(Period(Earliest, b))

  def lowerGiven(p: Option[Period]): Option[Long] = p.map(_.start).filter(_ != Earliest)

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

  /** [[Param.brief]] is the agents' wording, a few words: every turn of an agent step re-sends a tool's schema. */
  object Docs:

    final case class Param(rest: String, mcp: String, doc: String, brief: String)

    val query = Param(
      "q",
      "query",
      "Full text (websearch syntax: bare words AND-ed, \"phrase\", -exclude, or), evaluated within one ~4 000-char chunk",
      "Free text: words AND-ed, \"a phrase\", -word, or"
    )
    val tags = Param(
      "tag",
      "tags",
      "ALL required — every tag must be present on the source or one of its findings",
      "Every one required"
    )
    val authors = Param(
      "author",
      "authors",
      "ANY matches — a case-insensitive substring of an author's name",
      "Any one, a substring of an author's name"
    )
    val places = Param(
      "place",
      "places",
      "ANY matches — a place id from `places` matches by geometry and hierarchy, a name matches text",
      "Any one, a place id or name"
    )
    val topics =
      Param("topic", "topics", "ALL required — every topic must be present on the source", "Every one required")
    val status = Param(
      "status",
      "status",
      s"ANY matches — ${com.histmersion.api.library.Source.Status.values.map(_.wire).mkString("|")}",
      s"Any of ${com.histmersion.api.library.Source.Status.values.map(_.wire).mkString("|")}"
    )
    val yearFrom = Param("yearFrom", "yearFrom", "Earliest publication year (inclusive)", "Earliest publication year")
    val yearTo = Param("yearTo", "yearTo", "Latest publication year (inclusive)", "Latest publication year")
    val from = Param(
      "from",
      "from",
      "Deep-time start, a signed year (BCE negative: 1200 BCE is -1200)",
      "Period start, a signed year (BCE negative)"
    )
    val to = Param("to", "to", "Deep-time end, a signed year (BCE negative)", "Period end, a signed year")
    val minLon = Param(
      "minLon",
      "minLon",
      "Bounding box: west longitude (a west above the east crosses the antimeridian)",
      "Box: west longitude"
    )
    val minLat = Param("minLat", "minLat", "Bounding box: south latitude", "Box: south latitude")
    val maxLon = Param("maxLon", "maxLon", "Bounding box: east longitude", "Box: east longitude")
    val maxLat = Param("maxLat", "maxLat", "Bounding box: north latitude", "Box: north latitude")
    val sort = Param("sort", "sort", s"Ordering: ${Sort.all.mkString("|")}", Sort.all.mkString("|"))
    val limit =
      Param("limit", "limit", "Results per page (default: the configured page size, at most 500)", "Results per page")
    val offset = Param(
      "offset",
      "offset",
      "Skip this many results — the next page's offset is in the list's footer (`next: offset=N`)",
      "The next page's offset, from the listing's footer"
    )
    val title =
      Param("title", "title", "Case-insensitive substring of the listed item's title", "A substring of the title")
    val goal = Param("goal", "goal", "Only the sources a research goal links (its slug)", "A research goal's slug")

    val all: List[Param] =
      List(
        query,
        tags,
        authors,
        places,
        topics,
        status,
        yearFrom,
        yearTo,
        from,
        to,
        minLon,
        minLat,
        maxLon,
        maxLat,
        sort,
        limit,
        offset,
        title,
        goal
      )

    def describe[T](schema: Schema[T]): Schema[T] = describeWith(schema)(_.doc)

    def describeBrief[T](schema: Schema[T]): Schema[T] = describeWith(schema)(_.brief)

    private def describeWith[T](schema: Schema[T])(text: Param => String): Schema[T] =
      all.foldLeft(schema)((s, p) => s.modifyUnsafe[Any](p.mcp)(_.description(text(p))))

  /** One case class so Tapir can `mapInTo` it. The parameter names are fixed: callers send them as REST query
    * parameters.
    */
  final case class Query(
      q: Option[String],
      tag: List[String],
      author: List[String],
      place: List[String],
      topic: List[String],
      status: List[String],
      yearFrom: Option[Long],
      yearTo: Option[Long],
      from: Option[Long],
      to: Option[Long],
      minLon: Option[Double],
      minLat: Option[Double],
      maxLon: Option[Double],
      maxLat: Option[Double],
      sort: Option[String],
      limit: Option[Int],
      offset: Option[Int],
      title: Option[String] = None,
      goal: Option[String] = None
  ):
    def toFilter: Filter =
      Filter.of(
        q,
        tag,
        author,
        place,
        topic,
        status,
        yearFrom,
        yearTo,
        from,
        to,
        minLon,
        minLat,
        maxLon,
        maxLat,
        sort,
        limit,
        offset,
        title,
        goal
      )

  object Query:
    def from(f: Filter): Query =
      def lo(p: Option[Period]) = Filter.lowerGiven(p)
      def hi(p: Option[Period]) = Filter.upperGiven(p)
      Query(
        q = f.q,
        tag = f.tags,
        author = f.authors,
        place = f.places,
        topic = f.topics,
        status = f.status,
        yearFrom = lo(f.years),
        yearTo = hi(f.years),
        from = lo(f.period),
        to = hi(f.period),
        minLon = f.bbox.map(_.minLon),
        minLat = f.bbox.map(_.minLat),
        maxLon = f.bbox.map(_.maxLon),
        maxLat = f.bbox.map(_.maxLat),
        sort = Some(Sort.label(f.sort)),
        limit = Option(f.limit).filter(_ > 0),
        offset = Option(f.offset).filter(_ > 0),
        title = f.title,
        goal = f.goal
      )
