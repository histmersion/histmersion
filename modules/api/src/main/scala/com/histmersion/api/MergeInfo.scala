package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID

// ── merge list | show | ledger (M7) ───────────────────────────────────────────────────────────────

/** One row of `merges`: an import or an apply that changed something. */
final case class MergeInfo(
    id: UUID,
    bundleId: UUID,
    bundleSha256: String,
    from: BundleInstance,
    appliedAt: String,
    /** Set by `merge apply` (the plan it carried out); an import without matching has none. */
    planHash: Option[String] = None,
    /** The backup taken before the apply (its file name in `backups.dir`). */
    backup: Option[String] = None,
    /** Ledger rows and redirects this merge left. */
    ledgerRows: Int = 0,
    redirects: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeInfo:
  given Schema[MergeInfo] = summon[KindlingsSchema[MergeInfo]].schema
