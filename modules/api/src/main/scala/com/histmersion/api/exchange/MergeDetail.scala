package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MergeDetail(
    merge: MergeInfo,
    applied: Option[MergeApplyResult] = None,
    imported: Option[Bundle.ImportResult] = None,
    resolutions: Option[MergeResolutions] = None,
    remembered: List[MergeResolution] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema
