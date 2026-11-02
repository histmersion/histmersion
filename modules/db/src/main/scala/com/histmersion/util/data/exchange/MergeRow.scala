package com.histmersion.util.data.exchange

import com.histmersion.api.{ApiError, Write}
import com.histmersion.api.exchange.{
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
  MergeSession
}
import com.histmersion.api.aliases.UidRedirect
import com.histmersion.api.given
import com.histmersion.util.database.{Jsonb, Param, Sql}
import com.histmersion.util.database.given
import com.histmersion.domain.MergePlanner
import com.histmersion.domain.MergePlanner.Fold
import com.histmersion.util.data.exchange.BundlePlan.Ledger
import com.histmersion.values.BundleKind

import com.augustnagro.magnum.*
import java.util.UUID

final private[exchange] case class MergeRow(
    id: UUID,
    bundleId: UUID,
    bundleSha256: String,
    fromInstance: UUID,
    fromLabel: Option[String],
    appliedAt: String,
    planHash: Option[String],
    backup: Option[String],
    ledgerRows: Int,
    redirects: Int
) derives DbCodec
