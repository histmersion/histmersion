package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given

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
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object LedgerRow:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ForeignRev = ForeignRev.T
  object ForeignRev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocalRevAfter = LocalRevAfter.T
  object LocalRevAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Decision = Decision.T
  object Decision extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordedAt = RecordedAt.T
  object RecordedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
