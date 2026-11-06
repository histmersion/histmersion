package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given

final case class MergeInfo(
    id: UUID,
    bundleId: UUID,
    bundleSha256: MergeInfo.BundleSha256,
    from: Bundle.Instance,
    appliedAt: MergeInfo.AppliedAt,
    planHash: Option[MergeInfo.PlanHash] = None,
    backup: Option[MergeInfo.Backup] = None,
    ledgerRows: MergeInfo.LedgerRows = _root_.com.histmersion.api.exchange.MergeInfo.LedgerRows(0),
    redirects: MergeInfo.Redirects = _root_.com.histmersion.api.exchange.MergeInfo.Redirects(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MergeInfo:
  type BundleSha256 = BundleSha256.T
  object BundleSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AppliedAt = AppliedAt.T
  object AppliedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanHash = com.histmersion.api.exchange.MergePlan.PlanHash
  val PlanHash: _root_.com.histmersion.api.exchange.MergePlan.PlanHash.type = _root_.com.histmersion.api.exchange.MergePlan.PlanHash
  type Backup = Backup.T
  object Backup extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LedgerRows = LedgerRows.T
  object LedgerRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Redirects = Redirects.T
  object Redirects extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
