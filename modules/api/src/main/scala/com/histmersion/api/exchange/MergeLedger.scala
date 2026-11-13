package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.aliases.UidRedirect
import com.histmersion.api.research.LedgerRow
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MergeLedger(
    query: Option[MergeLedger.Query],
    entries: List[LedgerRow],
    redirects: List[UidRedirect] = Nil
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object MergeLedger:
  type Query = Query.T
  object Query extends Newtype[String, Pure]
