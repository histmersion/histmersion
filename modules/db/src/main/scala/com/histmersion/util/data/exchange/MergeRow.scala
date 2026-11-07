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

final private[exchange] case class MergeRow(
    id: UUID,
    bundleId: UUID,
    bundleSha256: MergeRow.BundleSha256,
    fromInstance: UUID,
    fromLabel: Option[MergeRow.FromLabel],
    appliedAt: MergeRow.AppliedAt,
    planHash: Option[MergeRow.PlanHash],
    backup: Option[MergeRow.Backup],
    ledgerRows: MergeRow.LedgerRows,
    redirects: MergeRow.Redirects
) derives DbCodec

object MergeRow:
  type BundleSha256 = com.histmersion.Sha256
  val BundleSha256: _root_.com.histmersion.Sha256.type = _root_.com.histmersion.Sha256
  type FromLabel = FromLabel.T
  object FromLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AppliedAt = AppliedAt.T
  object AppliedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanHash = PlanHash.T
  object PlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Backup = Backup.T
  object Backup extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LedgerRows = com.histmersion.RowCount
  val LedgerRows: _root_.com.histmersion.RowCount.type = _root_.com.histmersion.RowCount
  type Redirects = Redirects.T
  object Redirects extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
