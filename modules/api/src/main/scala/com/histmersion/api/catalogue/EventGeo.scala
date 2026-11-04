package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given

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
      KindlingsSchema, com.histmersion.api.json.Schema
object EventGeo:


  def missingEnd(ts: TimeSpan): Option[String] =
    val hasStart = ts.notBefore.isDefined || ts.beginBy.isDefined
    val hasEnd = ts.endFrom.isDefined || ts.notAfter.isDefined
    if hasStart && !hasEnd then Some("end not recorded")
    else if hasEnd && !hasStart then Some("start not recorded")
    else None

  final case class List(events: scala.List[EventGeo], unplaced: Int = 0, imprecise: Int = 0)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
