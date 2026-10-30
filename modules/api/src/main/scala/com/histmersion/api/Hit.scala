package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A source that matched, plus the evidence: `matched` = its findings that satisfied the period/bbox/place/tag
  * criteria, `why` = source-level reasons ("title matches 'collapse'").
  */
final case class Hit(source: SearchHit, matched: List[FindingMatch], why: List[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object Hit:
  given Schema[Hit] = summon[KindlingsSchema[Hit]].schema
