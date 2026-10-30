package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema

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
