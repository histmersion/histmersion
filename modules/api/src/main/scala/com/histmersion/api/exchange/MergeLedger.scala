package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.aliases.UidRedirect
import com.histmersion.api.research.LedgerRow
import com.histmersion.api.given

final case class MergeLedger(query: Option[String], entries: List[LedgerRow], redirects: List[UidRedirect] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
