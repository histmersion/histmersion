package com.histmersion.util.data.exchange

import com.histmersion.api.{
  ApiError,
  BundleImportResult,
  BundleInstance,
  BundleManifest,
  MergeApplyResult,
  MergeDecision,
  MergeDetail,
  MergeInfo,
  MergeLedger,
  MergeResolution,
  MergeResolutions,
  MergeSession,
  UidRedirect,
  Write
}
import com.histmersion.api.given
import com.histmersion.util.database.{Jsonb, Param, Sql}
import com.histmersion.util.database.given
import com.histmersion.domain.MergePlanner
import com.histmersion.domain.MergePlanner.Fold
import com.histmersion.util.data.exchange.BundlePlan.Ledger
import com.histmersion.values.BundleKind

import com.augustnagro.magnum.*
import java.util.UUID

final private[exchange] case class LedgerRow(
    foreignUid: UUID,
    kind: String,
    mergeId: UUID,
    foreignRev: String,
    localUid: UUID,
    label: Option[String],
    localRevAfter: Option[String],
    decision: String,
    recordedAt: String
) derives DbCodec
