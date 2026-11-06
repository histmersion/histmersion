package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.BBox
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given

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
    limit: Filter.Limit = _root_.com.histmersion.api.library.Filter.Limit(0),
    offset: Filter.Offset = _root_.com.histmersion.api.library.Filter.Offset(0),
    title: Option[Filter.Title] = None,
    goal: Option[Filter.Goal] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:

  def isEmpty: com.histmersion.api.library.Filter.IsEmptyResult =
    q.forall((_).value.trim.isEmpty) && tags.isEmpty && authors.isEmpty && places.isEmpty && topics.isEmpty &&
      status.isEmpty && years.isEmpty && period.isEmpty && bbox.isEmpty && title.forall((_).value.trim.isEmpty) &&
      goal.forall((_).value.trim.isEmpty)

  def sourceSet: Filter = Filter(q, tags, authors, places, topics, status, years, period, bbox, goal = goal)

  def hasFindingCriteria: com.histmersion.api.library.Filter.HasFindingCriteriaResult = _root_.com.histmersion.api.library.Filter.HasFindingCriteriaResult(period.isDefined || bbox.isDefined || places.nonEmpty || tags.nonEmpty)

object Filter:


  /** Open-ended bounds, far enough inside bigint that `int8range(..,'[]')` cannot overflow. */
  type OfCleanXs = OfCleanXs.T
  object OfCleanXs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IsEmptyResult = IsEmptyResult.T
  object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasFindingCriteriaResult = HasFindingCriteriaResult.T
  object HasFindingCriteriaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Earliest = Earliest.T
  object Earliest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Latest = Latest.T
  object Latest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampResult = ClampResult.T
  object ClampResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampV = ClampV.T
  object ClampV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodOfFrom = PeriodOfFrom.T
  object PeriodOfFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodOfTo = PeriodOfTo.T
  object PeriodOfTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LowerGivenResult = LowerGivenResult.T
  object LowerGivenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpperGivenResult = UpperGivenResult.T
  object UpperGivenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BboxOfMinLon = BboxOfMinLon.T
  object BboxOfMinLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BboxOfMinLat = BboxOfMinLat.T
  object BboxOfMinLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BboxOfMaxLon = BboxOfMaxLon.T
  object BboxOfMaxLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BboxOfMaxLat = BboxOfMaxLat.T
  object BboxOfMaxLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfQ = OfQ.T
  object OfQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfTags = OfTags.T
  object OfTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfAuthors = OfAuthors.T
  object OfAuthors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfPlaces = OfPlaces.T
  object OfPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfTopics = OfTopics.T
  object OfTopics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfStatus = OfStatus.T
  object OfStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfYearFrom = OfYearFrom.T
  object OfYearFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfYearTo = OfYearTo.T
  object OfYearTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfFrom = OfFrom.T
  object OfFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfTo = OfTo.T
  object OfTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMinLon = OfMinLon.T
  object OfMinLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMinLat = OfMinLat.T
  object OfMinLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMaxLon = OfMaxLon.T
  object OfMaxLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMaxLat = OfMaxLat.T
  object OfMaxLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfSort = OfSort.T
  object OfSort extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLimit = OfLimit.T
  object OfLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfOffset = OfOffset.T
  object OfOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfTitle = OfTitle.T
  object OfTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfGoal = OfGoal.T
  object OfGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Q = Q.T
  object Q extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Places = Places.T
  object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Topics = Topics.T
  object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Offset = Offset.T
  object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Goal = Goal.T
  object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Earliest: com.histmersion.api.library.Filter.Earliest = _root_.com.histmersion.api.library.Filter.Earliest(-1000000000L)
  val Latest: com.histmersion.api.library.Filter.Latest = _root_.com.histmersion.api.library.Filter.Latest(1000000000L)

  /** Postgres rejects an inverted range (`range lower bound must be less than or equal to range upper bound`), so a
    * nonsense bound is clamped before it can build one.
    */
  def clamp(v: com.histmersion.api.library.Filter.ClampV): com.histmersion.api.library.Filter.ClampResult = math.max(Earliest, math.min(Latest, (v).value))

  def periodOf(from: Option[com.histmersion.api.library.Filter.PeriodOfFrom], to: Option[com.histmersion.api.library.Filter.PeriodOfTo]): Option[Period] =
    (from.map(clamp), to.map(clamp)) match
      case (None, None)       => None
      case (Some(a), Some(b)) => Some(if a <= b then Period(a, b) else Period(b, a))
      case (Some(a), None)    => Some(Period(a, Latest))
      case (None, Some(b))    => Some(Period(Earliest, b))

  def lowerGiven(p: Option[Period]): Option[com.histmersion.api.library.Filter.LowerGivenResult] = p.map(_.start).filter(_ != Earliest)

  def upperGiven(p: Option[Period]): Option[com.histmersion.api.library.Filter.UpperGivenResult] = p.map(_.end).filter(_ != Latest)

  def bboxOf(
      minLon: Option[com.histmersion.api.library.Filter.BboxOfMinLon],
      minLat: Option[com.histmersion.api.library.Filter.BboxOfMinLat],
      maxLon: Option[com.histmersion.api.library.Filter.BboxOfMaxLon],
      maxLat: Option[com.histmersion.api.library.Filter.BboxOfMaxLat]
  ): Option[BBox] =
    (minLon, minLat, maxLon, maxLat) match
      case (Some(a), Some(b), Some(c), Some(d)) => Some(BBox(a, b, c, d))
      case _                                    => None

  def of(
      q: Option[com.histmersion.api.library.Filter.OfQ] = None,
      tags: List[com.histmersion.api.library.Filter.OfTags] = Nil,
      authors: List[com.histmersion.api.library.Filter.OfAuthors] = Nil,
      places: List[com.histmersion.api.library.Filter.OfPlaces] = Nil,
      topics: List[com.histmersion.api.library.Filter.OfTopics] = Nil,
      status: List[com.histmersion.api.library.Filter.OfStatus] = Nil,
      yearFrom: Option[com.histmersion.api.library.Filter.OfYearFrom] = None,
      yearTo: Option[com.histmersion.api.library.Filter.OfYearTo] = None,
      from: Option[com.histmersion.api.library.Filter.OfFrom] = None,
      to: Option[com.histmersion.api.library.Filter.OfTo] = None,
      minLon: Option[com.histmersion.api.library.Filter.OfMinLon] = None,
      minLat: Option[com.histmersion.api.library.Filter.OfMinLat] = None,
      maxLon: Option[com.histmersion.api.library.Filter.OfMaxLon] = None,
      maxLat: Option[com.histmersion.api.library.Filter.OfMaxLat] = None,
      sort: Option[com.histmersion.api.library.Filter.OfSort] = None,
      limit: Option[com.histmersion.api.library.Filter.OfLimit] = None,
      offset: Option[com.histmersion.api.library.Filter.OfOffset] = None,
      title: Option[com.histmersion.api.library.Filter.OfTitle] = None,
      goal: Option[com.histmersion.api.library.Filter.OfGoal] = None
  ): Filter =
    def clean(xs: List[com.histmersion.api.library.Filter.OfCleanXs]) = xs.flatMap((_).value.split(",")).map(_.trim).filter(_.nonEmpty)
    Filter(
      q = q.map((_).value.trim).filter((_).value.nonEmpty),
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
      title = title.map((_).value.trim).filter((_).value.nonEmpty),
      goal = goal.map((_).value.trim).filter((_).value.nonEmpty)
    )

  /** [[Param.brief]] is the agents' wording, a few words: every turn of an agent step re-sends a tool's schema. */
  object Docs:

    type DescribeWithTextResult = DescribeWithTextResult.T
    object DescribeWithTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    final case class Param(rest: Param.Rest, mcp: Param.Mcp, doc: Param.Doc, brief: Param.Brief)

    object Param:
      type Rest = Rest.T
      object Rest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Mcp = Mcp.T
      object Mcp extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Doc = Doc.T
      object Doc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Brief = Brief.T
      object Brief extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val query = Param(
      _root_.com.histmersion.api.library.Filter.Docs.Param.Rest("q"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("query"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Full text (websearch syntax: bare words AND-ed, \"phrase\", -exclude, or), evaluated within one ~4 000-char chunk"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Free text: words AND-ed, \"a phrase\", -word, or")
    )
    val tags = Param(
      _root_.com.histmersion.api.library.Filter.Docs.Param.Rest("tag"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("tags"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("ALL required — every tag must be present on the source or one of its findings"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Every one required")
    )
    val authors = Param(
      _root_.com.histmersion.api.library.Filter.Docs.Param.Rest("author"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("authors"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("ANY matches — a case-insensitive substring of an author's name"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Any one, a substring of an author's name")
    )
    val places = Param(
      _root_.com.histmersion.api.library.Filter.Docs.Param.Rest("place"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("places"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("ANY matches — a place id from `places` matches by geometry and hierarchy, a name matches text"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Any one, a place id or name")
    )
    val topics =
      Param(_root_.com.histmersion.api.library.Filter.Docs.Param.Rest("topic"), _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("topics"), _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("ALL required — every topic must be present on the source"), _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Every one required"))
    val status = Param(
      _root_.com.histmersion.api.library.Filter.Docs.Param.Rest("status"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("status"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Doc(s"ANY matches — ${com.histmersion.api.library.Source.Status.values.map(_.wire).mkString("|")}"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Brief(s"Any of ${com.histmersion.api.library.Source.Status.values.map(_.wire).mkString("|")}")
    )
    val yearFrom = Param(_root_.com.histmersion.api.library.Filter.Docs.Param.Rest("yearFrom"), _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("yearFrom"), _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Earliest publication year (inclusive)"), _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Earliest publication year"))
    val yearTo = Param(_root_.com.histmersion.api.library.Filter.Docs.Param.Rest("yearTo"), _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("yearTo"), _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Latest publication year (inclusive)"), _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Latest publication year"))
    val from = Param(
      _root_.com.histmersion.api.library.Filter.Docs.Param.Rest("from"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("from"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Deep-time start, a signed year (BCE negative: 1200 BCE is -1200)"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Period start, a signed year (BCE negative)")
    )
    val to = Param(_root_.com.histmersion.api.library.Filter.Docs.Param.Rest("to"), _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("to"), _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Deep-time end, a signed year (BCE negative)"), _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Period end, a signed year"))
    val minLon = Param(
      _root_.com.histmersion.api.library.Filter.Docs.Param.Rest("minLon"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("minLon"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Bounding box: west longitude (a west above the east crosses the antimeridian)"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Box: west longitude")
    )
    val minLat = Param(_root_.com.histmersion.api.library.Filter.Docs.Param.Rest("minLat"), _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("minLat"), _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Bounding box: south latitude"), _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Box: south latitude"))
    val maxLon = Param(_root_.com.histmersion.api.library.Filter.Docs.Param.Rest("maxLon"), _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("maxLon"), _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Bounding box: east longitude"), _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Box: east longitude"))
    val maxLat = Param(_root_.com.histmersion.api.library.Filter.Docs.Param.Rest("maxLat"), _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("maxLat"), _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Bounding box: north latitude"), _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Box: north latitude"))
    val sort = Param(_root_.com.histmersion.api.library.Filter.Docs.Param.Rest("sort"), _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("sort"), _root_.com.histmersion.api.library.Filter.Docs.Param.Doc(s"Ordering: ${Sort.all.mkString("|")}"), _root_.com.histmersion.api.library.Filter.Docs.Param.Brief(Sort.all.mkString("|")))
    val limit =
      Param(_root_.com.histmersion.api.library.Filter.Docs.Param.Rest("limit"), _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("limit"), _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Results per page (default: the configured page size, at most 500)"), _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("Results per page"))
    val offset = Param(
      _root_.com.histmersion.api.library.Filter.Docs.Param.Rest("offset"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("offset"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Skip this many results — the next page's offset is in the list's footer (`next: offset=N`)"),
      _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("The next page's offset, from the listing's footer")
    )
    val title =
      Param(_root_.com.histmersion.api.library.Filter.Docs.Param.Rest("title"), _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("title"), _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Case-insensitive substring of the listed item's title"), _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("A substring of the title"))
    val goal = Param(_root_.com.histmersion.api.library.Filter.Docs.Param.Rest("goal"), _root_.com.histmersion.api.library.Filter.Docs.Param.Mcp("goal"), _root_.com.histmersion.api.library.Filter.Docs.Param.Doc("Only the sources a research goal links (its slug)"), _root_.com.histmersion.api.library.Filter.Docs.Param.Brief("A research goal's slug"))

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

    private def describeWith[T](schema: Schema[T])(text: Param => com.histmersion.api.library.Filter.Docs.DescribeWithTextResult): Schema[T] =
      all.foldLeft(schema)((s, p) => s.modifyUnsafe[Any]((p.mcp).value)(_.description(text(p))))

  /** One case class so Tapir can `mapInTo` it. The parameter names are fixed: callers send them as REST query
    * parameters.
    */
  final case class Query(
      q: Option[com.histmersion.api.library.Filter.Q],
      tag: List[com.histmersion.api.library.Filter.Tags],
      author: List[com.histmersion.api.library.Filter.Authors],
      place: List[com.histmersion.api.library.Filter.Places],
      topic: List[com.histmersion.api.library.Filter.Topics],
      status: List[com.histmersion.api.library.Filter.Status],
      yearFrom: Option[com.histmersion.api.catalogue.Year],
      yearTo: Option[com.histmersion.api.catalogue.Year],
      from: Option[com.histmersion.api.catalogue.Year],
      to: Option[com.histmersion.api.catalogue.Year],
      minLon: Option[com.histmersion.api.catalogue.Longitude],
      minLat: Option[com.histmersion.api.catalogue.Latitude],
      maxLon: Option[com.histmersion.api.catalogue.Longitude],
      maxLat: Option[com.histmersion.api.catalogue.Latitude],
      sort: Option[Query.Sort],
      limit: Option[com.histmersion.api.library.Filter.Limit],
      offset: Option[com.histmersion.api.library.Filter.Offset],
      title: Option[com.histmersion.api.library.Filter.Title] = None,
      goal: Option[com.histmersion.api.library.Filter.Goal] = None
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
    type Sort = Sort.T
    object Sort extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
