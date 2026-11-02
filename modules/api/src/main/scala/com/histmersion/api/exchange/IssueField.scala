package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class IssueField(field: String, left: Option[String], right: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object IssueField:
  given Schema[IssueField] = summon[KindlingsSchema[IssueField]].schema
