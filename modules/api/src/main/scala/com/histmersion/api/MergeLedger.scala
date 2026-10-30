package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MergeLedger(query: Option[String], entries: List[LedgerRow], redirects: List[UidRedirect] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeLedger:
  given Schema[MergeLedger] = summon[KindlingsSchema[MergeLedger]].schema
