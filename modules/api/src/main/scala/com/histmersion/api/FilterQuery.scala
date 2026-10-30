package com.histmersion.api

import io.circe.{Codec as CirceCodec, Decoder, Encoder}

/** One case class so Tapir can `mapInTo` it. The parameter names are fixed: callers send them as REST query parameters.
  */
final case class FilterQuery(
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

object FilterQuery:
  def from(f: Filter): FilterQuery =
    def lo(p: Option[Period]) = Filter.lowerGiven(p)
    def hi(p: Option[Period]) = Filter.upperGiven(p)
    FilterQuery(
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
