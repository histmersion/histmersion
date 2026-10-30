package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PlaceSummary(
    id: String,
    label: String,
    kind: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    source: Option[String],
    names: String,
    contested: Boolean = false,
    dating: PlaceDating = PlaceDating.Stated,
    importSource: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def when: String = PlaceDating.describe(dating, periodStart, periodEnd)
object PlaceSummary:
  given Schema[PlaceSummary] = summon[KindlingsSchema[PlaceSummary]].schema
