package com.histmersion.util.data.exchange

import com.histmersion.api.Write
import com.histmersion.api.exchange.{
  MergeApplyResult,
  MergeDecision,
  MergeDetail,
  MergeInfo,
  MergeLedger,
  MergeResolution,
  MergeResolutions,
  MergeSession
}
import com.histmersion.api.aliases.UidRedirect
import com.histmersion.api.given
import com.histmersion.util.database.{Jsonb, Param, Sql}
import com.histmersion.util.database.given
import com.histmersion.domain.MergePlanner
import com.histmersion.domain.MergePlanner.Fold
import com.histmersion.util.data.exchange.BundlePlan.Ledger

import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.util.data.exchange.Bundle
import com.histmersion.api.Api

final private[exchange] case class LedgerRow(
    foreignUid: UUID,
    kind: LedgerRow.Kind,
    mergeId: UUID,
    foreignRev: LedgerRow.ForeignRev,
    localUid: UUID,
    label: Option[LedgerRow.Label],
    localRevAfter: Option[LedgerRow.LocalRevAfter],
    decision: LedgerRow.Decision,
    recordedAt: LedgerRow.RecordedAt
) derives DbCodec

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
