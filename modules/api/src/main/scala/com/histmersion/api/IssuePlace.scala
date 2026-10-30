package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Where and when the two records of a place or event question are: GeoJSON geometries (the review screen draws both on
  * a map) and their spans.
  */
final case class IssuePlace(
    leftGeometry: Option[String] = None,
    rightGeometry: Option[String] = None,
    leftSpan: Option[IssueSpan] = None,
    rightSpan: Option[IssueSpan] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object IssuePlace:
  given Schema[IssuePlace] = summon[KindlingsSchema[IssuePlace]].schema
