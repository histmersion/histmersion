package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One field of the two records an issue is about, as the review screen's three-column table shows it (here | result |
  * bundle). `None` = the record says nothing.
  */
final case class IssueField(field: String, left: Option[String], right: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object IssueField:
  given Schema[IssueField] = summon[KindlingsSchema[IssueField]].schema
