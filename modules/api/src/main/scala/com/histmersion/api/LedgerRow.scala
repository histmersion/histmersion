package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID

/** One row of `merge_ledger`: the bundle's uid, the record here it maps to (and its label now), the revs merged, and
  * the decision.
  */
final case class LedgerRow(
    foreignUid: UUID,
    kind: String,
    mergeId: UUID,
    foreignRev: String,
    localUid: UUID,
    label: Option[String],
    localRevAfter: Option[String],
    decision: String,
    recordedAt: String
) derives KindlingsCodecAsObject,
      KindlingsSchema
object LedgerRow:
  given Schema[LedgerRow] = summon[KindlingsSchema[LedgerRow]].schema
