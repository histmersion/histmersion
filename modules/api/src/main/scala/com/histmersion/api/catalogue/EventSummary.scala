package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given

final case class EventSummary(
    id: String,
    label: String,
    kind: String,
    timeSpan: TimeSpan,
    historicity: String,
    placeId: Option[String] = None,
    parentId: Option[String] = None,
    description: Option[String] = None,
    citekey: Option[String] = None,
    scale: String = EventScale.Local,
    scaleStated: Boolean = false,
    tier: String = EventTier.Attested,
    precision: String = EventPrecision.Precise,
    central: Option[Long] = None,
    plusMinus: Option[Long] = None,
    datingMethod: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def datingNote: Option[String] = EventPrecision.note(central, plusMinus, datingMethod)
