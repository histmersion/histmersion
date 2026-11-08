package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.BBox
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.research.ReviewGoal

final case class Filter(
    q: Option[Filter.Q] = None,
    tags: List[Filter.Tags] = Nil,
    authors: List[Filter.Authors] = Nil,
    places: List[Filter.Places] = Nil,
    topics: List[Filter.Topics] = Nil,
    status: List[Filter.Status] = Nil,
    years: Option[Period] = None,
    period: Option[Period] = None,
    bbox: Option[BBox] = None,
    sort: Sort = Sort.Relevance,
    limit: Filter.Limit = Filter.Limit(0),
    offset: Filter.Offset = Filter.Offset(0),
    title: Option[Filter.Title] = None,
    goal: Option[Filter.Goal] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:

  def isEmpty: Boolean =
    (q.forall((_).value.trim.isEmpty) && tags.isEmpty && authors.isEmpty && places.isEmpty && topics.isEmpty &&
      status.isEmpty && years.isEmpty && period.isEmpty && bbox.isEmpty && title.forall((_).value.trim.isEmpty) &&
      goal.forall((_).value.trim.isEmpty))

  def sourceSet: Filter = Filter(q, tags, authors, places, topics, status, years, period, bbox, goal = goal)

  def hasFindingCriteria: Boolean = (period.isDefined || bbox.isDefined || places.nonEmpty || tags.nonEmpty)

object Filter:
  type Last = Last.T
  object Last extends Newtype[String, Pure]
  type YearFrom = YearFrom.T
  object YearFrom extends Newtype[Long, Pure]
  type YearTo = YearTo.T
  object YearTo extends Newtype[Long, Pure]


  /** Open-ended bounds, far enough inside bigint that `int8range(..,'[]')` cannot overflow. */

  type Q = Q.T
  object Q extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type Authors = Authors.T
  object Authors extends Newtype[String, Pure]
  type Places = Places.T
  object Places extends Newtype[String, Pure]
  type Topics = Topics.T
  object Topics extends Newtype[String, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Offset = Offset.T
  object Offset extends Newtype[Int, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  export com.histmersion.api.research.ReviewGoal.{Slug as Goal}

  val Earliest: Year = Year(-1000000000L)
  val Latest: Year = Year(1000000000L)

  /** Postgres rejects an inverted range (`range lower bound must be less than or equal to range upper bound`), so a
    * nonsense bound is clamped before it can build one.
    */
  def clamp(v: Year): Year = Year(math.max(Earliest.value, math.min(Latest.value, v.value)))

  def periodOf(from: Option[Year], to: Option[Year]): Option[Period] =
    (from.map(clamp), to.map(clamp)) match
      case (None, None)       => None
      case (Some(a), Some(b)) => Some(if a <= b then Period(a, b) else Period(b, a))
      case (Some(a), None)    => Some(Period(a, Latest))
      case (None, Some(b))    => Some(Period(Earliest, b))

  def lowerGiven(p: Option[Period]): Option[Year] = p.map(_.start).filter(_ != Earliest)

  def upperGiven(p: Option[Period]): Option[Year] = p.map(_.end).filter(_ != Latest)

  def bboxOf(
      minLon: Option[Longitude],
      minLat: Option[Latitude],
      maxLon: Option[Longitude],
      maxLat: Option[Latitude]
  ): Option[BBox] =
    (minLon, minLat, maxLon, maxLat) match
      case (Some(a), Some(b), Some(c), Some(d)) => Some(BBox(a, b, c, d))
      case _                                    => None

  def of(
      q: Option[Q] = None,
      tags: List[Tags] = Nil,
      authors: List[Authors] = Nil,
      places: List[Places] = Nil,
      topics: List[Topics] = Nil,
      status: List[Status] = Nil,
      yearFrom: Option[Year] = None,
      yearTo: Option[Year] = None,
      from: Option[Year] = None,
      to: Option[Year] = None,
      minLon: Option[Longitude] = None,
      minLat: Option[Latitude] = None,
      maxLon: Option[Longitude] = None,
      maxLat: Option[Latitude] = None,
      sort: Option[Sort.Wire] = None,
      limit: Option[Limit] = None,
      offset: Option[Offset] = None,
      title: Option[Title] = None,
      goal: Option[ReviewGoal.Slug] = None
  ): Filter =
    def clean[A](xs: List[A])(unwrap: A => String)(wrap: String => A): List[A] =
      xs.flatMap(x => unwrap(x).split(",")).map(_.trim).filter(_.nonEmpty).map(wrap)
    Filter(
      q = q.map(v => Q(v.value.trim)).filter(_.value.nonEmpty),
      tags = clean(tags)(_.value)(Tags(_)),
      authors = clean(authors)(_.value)(Authors(_)),
      places = clean(places)(_.value)(Places(_)),
      topics = clean(topics)(_.value)(Topics(_)),
      status = clean(status)(_.value)(Status(_)),
      years = periodOf(yearFrom, yearTo),
      period = periodOf(from, to),
      bbox = bboxOf(minLon, minLat, maxLon, maxLat),
      sort = sort.flatMap(Sort.parse).getOrElse(Sort.Relevance),
      limit = limit.getOrElse(Limit(0)),
      offset = Offset(offset.fold(0)(_.value).max(0)),
      title = title.map(v => Title(v.value.trim)).filter(_.value.nonEmpty),
      goal = goal.map(v => Goal(v.value.trim)).filter(_.value.nonEmpty)
    )

  /** [[Param.brief]] is the agents' wording, a few words: every turn of an agent step re-sends a tool's schema. */
  object Docs:
    final case class Param(rest: Param.Rest, mcp: Param.Mcp, doc: Param.Doc, brief: Param.Brief)

    object Param:
      type Rest = Rest.T
      object Rest extends Newtype[String, Pure]
      type Mcp = Mcp.T
      object Mcp extends Newtype[String, Pure]
      type Doc = Doc.T
      object Doc extends Newtype[String, Pure]
      type Brief = Brief.T
      object Brief extends Newtype[String, Pure]

    val query = Param(
      Param.Rest("q"),
      Param.Mcp("query"),
      Param.Doc("Full text (websearch syntax: bare words AND-ed, \"phrase\", -exclude, or), evaluated within one ~4 000-char chunk"),
      Param.Brief("Free text: words AND-ed, \"a phrase\", -word, or")
    )
    val tags = Param(
      Param.Rest("tag"),
      Param.Mcp("tags"),
      Param.Doc("ALL required — every tag must be present on the source or one of its findings"),
      Param.Brief("Every one required")
    )
    val authors = Param(
      Param.Rest("author"),
      Param.Mcp("authors"),
      Param.Doc("ANY matches — a case-insensitive substring of an author's name"),
      Param.Brief("Any one, a substring of an author's name")
    )
    val places = Param(
      Param.Rest("place"),
      Param.Mcp("places"),
      Param.Doc("ANY matches — a place id from `places` matches by geometry and hierarchy, a name matches text"),
      Param.Brief("Any one, a place id or name")
    )
    val topics =
      Param(Param.Rest("topic"), Param.Mcp("topics"), Param.Doc("ALL required — every topic must be present on the source"), Param.Brief("Every one required"))
    val status = Param(
      Param.Rest("status"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("status"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Doc(s"ANY matches — ${com.histmersion.api.library.Source.Status.values.map(_.wire).mkString("|")}"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Brief(s"Any of ${com.histmersion.api.library.Source.Status.values.map(_.wire).mkString("|")}")
    )
    val yearFrom = Param(Param.Rest("yearFrom"), Param.Mcp("yearFrom"), Param.Doc("Earliest publication year (inclusive)"), Param.Brief("Earliest publication year"))
    val yearTo = Param(Param.Rest("yearTo"), Param.Mcp("yearTo"), Param.Doc("Latest publication year (inclusive)"), Param.Brief("Latest publication year"))
    val from = Param(
      Param.Rest("from"),
      Param.Mcp("from"),
      Param.Doc("Deep-time start, a signed year (BCE negative: 1200 BCE is -1200)"),
      Param.Brief("Period start, a signed year (BCE negative)")
    )
    val to = Param(Param.Rest("to"), Param.Mcp("to"), Param.Doc("Deep-time end, a signed year (BCE negative)"), Param.Brief("Period end, a signed year"))
    val minLon = Param(
      Param.Rest("minLon"),
      Param.Mcp("minLon"),
      Param.Doc("Bounding box: west longitude (a west above the east crosses the antimeridian)"),
      Param.Brief("Box: west longitude")
    )
    val minLat = Param(Param.Rest("minLat"), Param.Mcp("minLat"), Param.Doc("Bounding box: south latitude"), Param.Brief("Box: south latitude"))
    val maxLon = Param(Param.Rest("maxLon"), Param.Mcp("maxLon"), Param.Doc("Bounding box: east longitude"), Param.Brief("Box: east longitude"))
    val maxLat = Param(Param.Rest("maxLat"), Param.Mcp("maxLat"), Param.Doc("Bounding box: north latitude"), Param.Brief("Box: north latitude"))
    val sort = Param(Param.Rest("sort"), Param.Mcp("sort"), Param.Doc(s"Ordering: ${Sort.all.mkString("|")}"), Param.Brief(Sort.all.mkString("|")))
    val limit =
      Param(Param.Rest("limit"), Param.Mcp("limit"), Param.Doc("Results per page (default: the configured page size, at most 500)"), Param.Brief("Results per page"))
    val offset = Param(
      Param.Rest("offset"),
      Param.Mcp("offset"),
      Param.Doc("Skip this many results — the next page's offset is in the list's footer (`next: offset=N`)"),
      Param.Brief("The next page's offset, from the listing's footer")
    )
    val title =
      Param(Param.Rest("title"), Param.Mcp("title"), Param.Doc("Case-insensitive substring of the listed item's title"), Param.Brief("A substring of the title"))
    val goal = Param(Param.Rest("goal"), Param.Mcp("goal"), Param.Doc("Only the sources a research goal links (its slug)"), Param.Brief("A research goal's slug"))

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

    def describe[T](schema: Schema[T]): Schema[T] = describeWith(schema)(_.doc.value)

    def describeBrief[T](schema: Schema[T]): Schema[T] = describeWith(schema)(_.brief.value)

    private def describeWith[T](schema: Schema[T])(text: Param => String): Schema[T] =
      all.foldLeft(schema)((s, p) => s.modifyUnsafe[Any](p.mcp.value)(_.description(text(p))))

  /** One case class so Tapir can `mapInTo` it. The parameter names are fixed: callers send them as REST query
    * parameters.
    */
  final case class Query(
      q: Option[Q],
      tag: List[Tags],
      author: List[Authors],
      place: List[Places],
      topic: List[Topics],
      status: List[Status],
      yearFrom: Option[Year],
      yearTo: Option[Year],
      from: Option[Year],
      to: Option[Year],
      minLon: Option[Longitude],
      minLat: Option[Latitude],
      maxLon: Option[Longitude],
      maxLat: Option[Latitude],
      sort: Option[Sort.Wire],
      limit: Option[Limit],
      offset: Option[Offset],
      title: Option[Title] = None,
      goal: Option[ReviewGoal.Slug] = None
  ):
    def toFilter: Filter =
      of(
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
      def lo(p: Option[Period]) = lowerGiven(p)
      def hi(p: Option[Period]) = upperGiven(p)
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
        limit = Option(f.limit).filter((_).value > 0),
        offset = Option(f.offset).filter((_).value > 0),
        title = f.title,
        goal = f.goal
      )
