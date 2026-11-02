package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** An audit of every linked item: those that disagree with their records, and how many items were checked. */
final case class CollectionAudit(
    findings: List[CollectionAuditRow],
    checked: Int
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionAudit:
  given Schema[CollectionAudit] = summon[KindlingsSchema[CollectionAudit]].schema
