package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/merges/{id}`: the row, its report as it was written (an apply's or an import's), the resolutions it was
  * given and the decisions it left to remember.
  */
final case class MergeDetail(
    merge: MergeInfo,
    applied: Option[MergeApplyResult] = None,
    imported: Option[BundleImportResult] = None,
    resolutions: Option[MergeResolutions] = None,
    remembered: List[MergeResolution] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeDetail:
  given Schema[MergeDetail] = summon[KindlingsSchema[MergeDetail]].schema
