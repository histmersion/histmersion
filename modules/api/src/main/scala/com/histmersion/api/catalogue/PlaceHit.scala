package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class PlaceHit(
    id: String,
    label: String,
    kind: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    source: Option[String],
    names: String,
    matchedName: Option[String],
    findings: Int,
    contested: Boolean = false,
    dating: PlaceDating = PlaceDating.Stated,
    importSource: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def toSummary: PlaceSummary =
    PlaceSummary(id, label, kind, periodStart, periodEnd, source, names, contested, dating, importSource)
object PlaceHit:
  given Schema[PlaceHit] = summon[KindlingsSchema[PlaceHit]].schema
