package com.histmersion
package api.research
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class LedgerRow(
    foreignUid: UUID,
    kind: LedgerRow.Kind,
    mergeId: UUID,
    foreignRev: LedgerRow.ForeignRev,
    localUid: UUID,
    label: Option[LedgerRow.Label],
    localRevAfter: Option[LedgerRow.LocalRevAfter],
    decision: LedgerRow.Decision,
    recordedAt: LedgerRow.RecordedAt
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object LedgerRow:
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type ForeignRev = ForeignRev.T
  object ForeignRev extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type LocalRevAfter = LocalRevAfter.T
  object LocalRevAfter extends Newtype[String, Pure]
  type Decision = Decision.T
  object Decision extends Newtype[String, Pure]
  type RecordedAt = RecordedAt.T
  object RecordedAt extends Newtype[String, Pure]
