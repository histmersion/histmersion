package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class IssueField(field: String, left: Option[String], right: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object IssueField:
  given Schema[IssueField] = summon[KindlingsSchema[IssueField]].schema
