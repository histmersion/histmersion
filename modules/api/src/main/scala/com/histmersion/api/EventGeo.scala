package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventGeo(
    id: String,
    label: String,
    kind: String,
    scale: String,
    historicity: String,
    timeSpan: TimeSpan,
    lat: Double,
    lon: Double,
    anchor: String,
    placeId: Option[String] = None,
    placeLabel: Option[String] = None,
    parentId: Option[String] = None,
    outline: Option[String] = None,
    tier: String = EventTier.Attested,
    size: Option[String] = None,
    precision: String = EventPrecision.Precise,
    dating: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventGeo:
  given Schema[EventGeo] = summon[KindlingsSchema[EventGeo]].schema

  def outer(ts: TimeSpan): Option[(Long, Long)] =
    val start = ts.notBefore.orElse(ts.beginBy).orElse(ts.endFrom).orElse(ts.notAfter)
    val end = ts.notAfter.orElse(ts.endFrom).orElse(ts.beginBy).orElse(ts.notBefore)
    for a <- start; b <- end yield (a.min(b), a.max(b))

  def missingEnd(ts: TimeSpan): Option[String] =
    val hasStart = ts.notBefore.isDefined || ts.beginBy.isDefined
    val hasEnd = ts.endFrom.isDefined || ts.notAfter.isDefined
    if hasStart && !hasEnd then Some("end not recorded")
    else if hasEnd && !hasStart then Some("start not recorded")
    else None
