package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given

final case class MergeInfo(
    id: UUID,
    bundleId: UUID,
    bundleSha256: String,
    from: Bundle.Instance,
    appliedAt: String,
    planHash: Option[String] = None,
    backup: Option[String] = None,
    ledgerRows: Int = 0,
    redirects: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
