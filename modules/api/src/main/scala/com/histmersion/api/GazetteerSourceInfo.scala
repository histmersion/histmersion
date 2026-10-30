package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class GazetteerSourceInfo(
    key: String,
    kind: GazetteerKind,
    title: String,
    file: Option[String],
    present: Boolean,
    pinnedVersion: Option[String],
    importedVersion: Option[String] = None,
    importedAt: Option[String] = None,
    places: Option[Int] = None,
    names: Option[Int] = None,
    links: Option[Int] = None,
    licence: Option[String] = None,
    citation: Option[String] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object GazetteerSourceInfo:
  given Schema[GazetteerSourceInfo] = summon[KindlingsSchema[GazetteerSourceInfo]].schema
