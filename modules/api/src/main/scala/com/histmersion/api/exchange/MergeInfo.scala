package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class MergeInfo(
    id: UUID,
    bundleId: UUID,
    bundleSha256: MergeInfo.BundleSha256,
    from: Bundle.Instance,
    appliedAt: MergeInfo.AppliedAt,
    planHash: Option[MergeInfo.PlanHash] = None,
    backup: Option[MergeInfo.Backup] = None,
    ledgerRows: MergeInfo.LedgerRows = MergeInfo.LedgerRows(0),
    redirects: MergeInfo.Redirects = MergeInfo.Redirects(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MergeInfo:
  type BundleSha256 = BundleSha256.T
  object BundleSha256 extends RefinedType[String, Pure]
  type AppliedAt = AppliedAt.T
  object AppliedAt extends RefinedType[String, Pure]
  export MergePlan.PlanHash
  type Backup = Backup.T
  object Backup extends RefinedType[String, Pure]
  type LedgerRows = LedgerRows.T
  object LedgerRows extends RefinedType[Int, Pure]
  type Redirects = Redirects.T
  object Redirects extends RefinedType[Int, Pure]
