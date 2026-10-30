package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A Crossref `update-to` entry. */
final case class UpdateNotice(kind: Option[String], label: Option[String], doi: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object UpdateNotice:
  given Schema[UpdateNotice] = summon[KindlingsSchema[UpdateNotice]].schema
