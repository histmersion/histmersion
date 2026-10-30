package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/merges/ledger?uid=&merge=&limit=`: the ledger rows (of one record — a uid, here or merged away, or a
  * citekey — or of one merge, or the latest) and the redirects that name it.
  */
final case class MergeLedger(query: Option[String], entries: List[LedgerRow], redirects: List[UidRedirect] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeLedger:
  given Schema[MergeLedger] = summon[KindlingsSchema[MergeLedger]].schema
