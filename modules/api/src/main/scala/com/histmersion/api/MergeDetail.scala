package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

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
