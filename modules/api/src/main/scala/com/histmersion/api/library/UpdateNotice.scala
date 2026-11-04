package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** A Crossref `update-to` entry. */
final case class UpdateNotice(kind: Option[String], label: Option[String], doi: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
